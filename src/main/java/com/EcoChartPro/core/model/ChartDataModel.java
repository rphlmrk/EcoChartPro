package com.EcoChartPro.core.model;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.ReplayStateListener;
import com.EcoChartPro.core.indicator.IndicatorManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ChartDataModel implements ReplayStateListener {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataModel.class);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int DATA_WINDOW_SIZE = 5000;
    private static final int DATA_WINDOW_TRIGGER_BUFFER = DATA_WINDOW_SIZE / 3;


    private List<KLine> visibleKLines;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager;
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private int startIndex = 0;
    private int barsPerScreen = 200;
    private boolean viewingLiveEdge = true;
    private List<KLine> finalizedCandles;
    private KLine currentlyFormingCandle;
    private boolean isConfiguredForReplay = false;
    private double rightMarginRatio = 0.20;

    private int totalCandleCount = 0;
    private int dataWindowStartIndex = 0;
    private final IndicatorManager indicatorManager;
    private ChartPanel chartPanel;
    private SwingWorker<RebuildResult, Void> activeResampleWorker = null;

    // Cache for the raw M1 data used to build the current data window.
    private List<KLine> baseDataWindow;

    // A record to pass multiple results from the async worker.
    private record RebuildResult(List<KLine> resampledCandles, KLine formingCandle, int newWindowStart, List<KLine> rawM1Slice) {}

    public ChartDataModel() {
        this.visibleKLines = new ArrayList<>();
        this.finalizedCandles = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.baseDataWindow = new ArrayList<>();
    }
    
    public void setView(ChartPanel chartPanel) {
        this.chartPanel = chartPanel;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    public void fireDataUpdated() {
        pcs.firePropertyChange("dataUpdated", null, null);
    }

    public void setDatabaseManager(DatabaseManager dbManager, DataSourceManager.ChartDataSource source) {
        this.dbManager = dbManager;
        this.currentSource = source;
    }

    public DatabaseManager getDbManager() {
        return this.dbManager;
    }

    private void updateView() {
        if (isConfiguredForReplay && currentDisplayTimeframe != Timeframe.M1) {
            if (loadReplayDataWindowIfNeeded()) {
                // The async worker will call triggerIndicatorRecalculation and updateView when done.
                return;
            }
            assembleVisibleKLines();
        } else if (!isConfiguredForReplay) {
            loadStandardDataWindowIfNeeded();
            assembleVisibleKLines();
        } else {
            assembleVisibleKLines();
        }

        calculateBoundaries();
        fireDataUpdated();
    }
    
    private void loadStandardDataWindowIfNeeded() {
        if (isConfiguredForReplay || totalCandleCount == 0 || dbManager == null) {
            return;
        }

        int requiredStart = Math.max(0, startIndex - INDICATOR_LOOKBACK_BUFFER);
        int requiredEnd = Math.min(totalCandleCount, startIndex + barsPerScreen);
        int currentWindowEnd = dataWindowStartIndex + finalizedCandles.size();

        boolean needsReload = finalizedCandles.isEmpty() ||
                              (startIndex < dataWindowStartIndex + DATA_WINDOW_TRIGGER_BUFFER && dataWindowStartIndex > 0) ||
                              (requiredEnd > currentWindowEnd - DATA_WINDOW_TRIGGER_BUFFER && currentWindowEnd < totalCandleCount);

        if (needsReload) {
            int newWindowCenter = startIndex + (barsPerScreen / 2);
            int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
            int newWindowSize = Math.min(DATA_WINDOW_SIZE, totalCandleCount - newWindowStart);

            if (newWindowSize <= 0) {
                finalizedCandles.clear();
                return;
            }

            this.dataWindowStartIndex = newWindowStart;
            String tfString = currentDisplayTimeframe.getDisplayName().replace(" ", "");
            this.finalizedCandles = dbManager.getKLinesByIndex(new Symbol(currentSource.symbol()), tfString, newWindowStart, newWindowSize);
            this.baseDataWindow = this.finalizedCandles;
            
            // A window reload is a data change.
            triggerIndicatorRecalculation();
        }
    }

    private boolean loadReplayDataWindowIfNeeded() {
        if (activeResampleWorker != null && !activeResampleWorker.isDone()) {
            return true;
        }

        int currentWindowEnd = dataWindowStartIndex + finalizedCandles.size() + (currentlyFormingCandle != null ? 1 : 0);

        boolean needsReload = finalizedCandles.isEmpty() ||
                              (startIndex < dataWindowStartIndex + DATA_WINDOW_TRIGGER_BUFFER && dataWindowStartIndex > 0) ||
                              (startIndex + barsPerScreen > currentWindowEnd - DATA_WINDOW_TRIGGER_BUFFER && currentWindowEnd < totalCandleCount);


        if (needsReload) {
            rebuildReplayHistoryAroundIndexAsync(startIndex);
            return true;
        }

        return false;
    }


    public void triggerIndicatorRecalculation() {
        indicatorManager.recalculateAll(this, getIndicatorDataSlice());
    }
    
    private List<KLine> getIndicatorDataSlice() {
        if (isConfiguredForReplay) {
            // When in replay, the data slice for indicators is the entire loaded data window,
            // as this already includes the necessary lookback buffer.
            return getAllChartableCandles();
        } else { // Standard mode
            if (finalizedCandles.isEmpty()) {
                 return Collections.emptyList();
            }
            // In standard mode, we construct the slice from the visible area + lookback.
            int viewStartInWindow = this.startIndex - this.dataWindowStartIndex;
            int calculationEndIndex = Math.min(viewStartInWindow + this.barsPerScreen, finalizedCandles.size());
            int calculationStartIndex = Math.max(0, viewStartInWindow - INDICATOR_LOOKBACK_BUFFER);
            return (calculationStartIndex < calculationEndIndex) ? finalizedCandles.subList(calculationStartIndex, calculationEndIndex) : Collections.emptyList();
        }
    }


    public void pan(int barDelta) {
        if (dbManager == null && !isConfiguredForReplay) return;

        viewingLiveEdge = false;
        int newStartIndex = Math.max(0, this.startIndex + barDelta);
        newStartIndex = Math.min(newStartIndex, getTotalCandleCount() - (int)(barsPerScreen * (1.0 - rightMarginRatio)));
        
        this.startIndex = newStartIndex;
        updateView();
    }

    public void zoom(double zoomFactor) {
        if (dbManager == null && !isConfiguredForReplay) return;

        this.viewingLiveEdge = false;
        int totalSize = getTotalCandleCount();
        int centerIndexOnScreen = this.startIndex + (this.barsPerScreen / 2);

        this.barsPerScreen = Math.max(20, Math.min((int)(this.barsPerScreen / zoomFactor), 1000));
        this.startIndex = Math.max(0, centerIndexOnScreen - (this.barsPerScreen / 2));
        this.startIndex = Math.min(this.startIndex, totalSize - barsPerScreen);
        updateView();
    }

    public void loadDataset(DataSourceManager.ChartDataSource source, String timeframe) {
        this.currentSource = source;
        this.isConfiguredForReplay = false;
        String tfString = timeframe.replace(" ", "");
        this.currentDisplayTimeframe = Timeframe.fromString(tfString);

        if (dbManager == null) {
             clearData();
             return;
        }
        
        this.totalCandleCount = dbManager.getTotalKLineCount(new Symbol(source.symbol()), tfString);
        
        if(totalCandleCount == 0){
             clearData();
             return;
        }
        
        this.finalizedCandles.clear();
        this.dataWindowStartIndex = 0;
        this.currentlyFormingCandle = null;
        this.viewingLiveEdge = true;
        updateView();
        triggerIndicatorRecalculation();
    }

    @Override
    public void onReplayTick(KLine newM1Bar) {
        if (currentDisplayTimeframe != Timeframe.M1) {
            processNewM1Bar(newM1Bar);
        }
        triggerIndicatorRecalculation();
        updateView();
    }

    @Override
    public void onReplaySessionStart() {
        this.viewingLiveEdge = true;
        if (this.currentDisplayTimeframe != Timeframe.M1) {
            rebuildReplayHistoryForLiveEdgeAsync();
        } else {
            triggerIndicatorRecalculation();
            updateView();
        }
    }
    
    @Override
    public void onReplayStateChanged() {
        if (ReplaySessionManager.getInstance().isPlaying()) {
            jumpToLiveEdge();
        }
    }

    public void setDisplayTimeframe(Timeframe newTimeframe) {
        if (newTimeframe == null || this.currentDisplayTimeframe == newTimeframe) return;
        
        this.currentDisplayTimeframe = newTimeframe;
        if (isConfiguredForReplay) {
            this.viewingLiveEdge = true;
            if (this.currentDisplayTimeframe != Timeframe.M1) {
                rebuildReplayHistoryForLiveEdgeAsync();
            } else {
                this.finalizedCandles.clear();
                this.currentlyFormingCandle = null;
                triggerIndicatorRecalculation();
                updateView();
            }
        } else {
            loadDataset(this.currentSource, newTimeframe.getDisplayName());
        }
    }

    public void clearData() {
        this.visibleKLines.clear();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
        this.startIndex = 0;
        this.viewingLiveEdge = true;
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        this.baseDataWindow.clear();
        this.indicatorManager.clearAllIndicators();
        this.totalCandleCount = 0;
        this.dataWindowStartIndex = 0;
        fireDataUpdated();
    }

    public void configureForReplay(Timeframe initialDisplayTimeframe, DataSourceManager.ChartDataSource source) {
        clearData();
        this.isConfiguredForReplay = true;
        this.currentDisplayTimeframe = initialDisplayTimeframe;
        this.currentSource = source;
    }

    private void rebuildReplayHistoryForLiveEdgeAsync() {
        int estimatedTotal = 0;
        if (currentDisplayTimeframe.getDuration().toMinutes() > 0) {
            estimatedTotal = (int) (ReplaySessionManager.getInstance().getTotalBarCount() / currentDisplayTimeframe.getDuration().toMinutes());
        }
        int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
        int targetStartIndex = Math.max(0, estimatedTotal - dataBarsOnScreen);
        rebuildReplayHistoryAroundIndexAsync(targetStartIndex);
    }

    private void rebuildReplayHistoryAroundIndexAsync(int targetStartIndexInResampledData) {
        if (activeResampleWorker != null && !activeResampleWorker.isDone()) {
            activeResampleWorker.cancel(true);
        }

        if (chartPanel != null) {
            chartPanel.setLoading(true, "Building " + currentDisplayTimeframe.getDisplayName() + " history...");
        }

        activeResampleWorker = new SwingWorker<>() {
            @Override
            protected RebuildResult doInBackground() throws Exception {
                ReplaySessionManager manager = ReplaySessionManager.getInstance();
                long m1BarsPerTargetCandle = currentDisplayTimeframe.getDuration().toMinutes();
                if (m1BarsPerTargetCandle <= 0) return new RebuildResult(Collections.emptyList(), null, 0, Collections.emptyList());

                int m1WindowCenter = (int) ((targetStartIndexInResampledData + (barsPerScreen / 2)) * m1BarsPerTargetCandle);
                m1WindowCenter = Math.min(m1WindowCenter, manager.getReplayHeadIndex());

                int m1LookbackForWindow = (int) ((DATA_WINDOW_SIZE + INDICATOR_LOOKBACK_BUFFER) * m1BarsPerTargetCandle);
                int m1FetchStartIndex = Math.max(0, m1WindowCenter - (m1LookbackForWindow / 2));
                int m1FetchCount = Math.min(m1LookbackForWindow, manager.getReplayHeadIndex() + 1 - m1FetchStartIndex);

                if (m1FetchCount <= 0) return new RebuildResult(Collections.emptyList(), null, 0, Collections.emptyList());

                logger.info("On-demand resampling for index {}. Fetching M1 slice: start={}, count={}",
                        targetStartIndexInResampledData, m1FetchStartIndex, m1FetchCount);
                List<KLine> m1HistorySlice = manager.getOneMinuteBars(m1FetchStartIndex, m1FetchCount);

                List<KLine> resampledData = DataResampler.resample(m1HistorySlice, currentDisplayTimeframe);
                
                int newWindowStart = 0;
                if (!m1HistorySlice.isEmpty() && !resampledData.isEmpty()) {
                     newWindowStart = m1FetchStartIndex / (int) m1BarsPerTargetCandle;
                }

                KLine formingCandle = null;
                if (!resampledData.isEmpty()) {
                    Instant lastM1TsInSlice = m1HistorySlice.get(m1HistorySlice.size() - 1).timestamp();
                    Instant lastResampledCandleTs = resampledData.get(resampledData.size() - 1).timestamp();
                    if (getIntervalStart(lastM1TsInSlice, currentDisplayTimeframe).equals(lastResampledCandleTs)) {
                        formingCandle = resampledData.remove(resampledData.size() - 1);
                    }
                }
                return new RebuildResult(resampledData, formingCandle, newWindowStart, m1HistorySlice);
            }

            @Override
            protected void done() {
                try {
                    RebuildResult result = get();
                    finalizedCandles = result.resampledCandles();
                    currentlyFormingCandle = result.formingCandle();
                    dataWindowStartIndex = result.newWindowStart();
                    baseDataWindow = result.rawM1Slice();
                    
                    if (currentDisplayTimeframe.getDuration().toMinutes() > 0) {
                        totalCandleCount = (ReplaySessionManager.getInstance().getReplayHeadIndex() + 1) / (int)currentDisplayTimeframe.getDuration().toMinutes();
                    } else {
                        totalCandleCount = finalizedCandles.size();
                    }

                    startIndex = Math.max(0, targetStartIndexInResampledData);
                    
                } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                    logger.warn("Resampling task was cancelled.");
                    return;
                } catch (ExecutionException e) {
                    logger.error("Failed to resample replay history in background", e.getCause());
                    finalizedCandles = Collections.emptyList();
                    currentlyFormingCandle = null;
                    baseDataWindow.clear();
                } finally {
                    if (chartPanel != null) {
                        chartPanel.setLoading(false, null);
                    }
                    // CRITICAL FIX: Trigger calculation AFTER new data is loaded.
                    triggerIndicatorRecalculation();
                    updateView();
                    activeResampleWorker = null;
                }
            }
        };
        activeResampleWorker.execute();
    }


    private void processNewM1Bar(KLine m1Bar) {
        if (baseDataWindow != null) {
            baseDataWindow.add(m1Bar);
        }

        Instant m1Timestamp = m1Bar.timestamp();
        Instant intervalStart = getIntervalStart(m1Timestamp, currentDisplayTimeframe);
        if (currentlyFormingCandle == null) {
            currentlyFormingCandle = new KLine(intervalStart, m1Bar.open(), m1Bar.high(), m1Bar.low(), m1Bar.close(), m1Bar.volume());
        } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
            finalizedCandles.add(currentlyFormingCandle);
            totalCandleCount++;
            currentlyFormingCandle = new KLine(intervalStart, m1Bar.open(), m1Bar.high(), m1Bar.low(), m1Bar.close(), m1Bar.volume());
        } else {
            currentlyFormingCandle = new KLine(currentlyFormingCandle.timestamp(), currentlyFormingCandle.open(), currentlyFormingCandle.high().max(m1Bar.high()), currentlyFormingCandle.low().min(m1Bar.low()), m1Bar.close(), currentlyFormingCandle.volume().add(m1Bar.volume()));
        }
    }

    private void assembleVisibleKLines() {
        if (isConfiguredForReplay) {
            if (currentDisplayTimeframe == Timeframe.M1) {
                ReplaySessionManager manager = ReplaySessionManager.getInstance();
                int headIndex = manager.getReplayHeadIndex();
                if (headIndex < 0) { this.visibleKLines = Collections.emptyList(); return; }
                if (viewingLiveEdge) {
                    int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
                    this.startIndex = Math.max(0, (headIndex + 1) - dataBarsOnScreen);
                }
                
                int fetchCount = Math.min(this.barsPerScreen, headIndex + 1 - this.startIndex);
                this.baseDataWindow = manager.getOneMinuteBars(this.startIndex, fetchCount);
                this.visibleKLines = this.baseDataWindow;
            } else {
                List<KLine> allChartableCandles = getAllChartableCandles();
                if (viewingLiveEdge) {
                    int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
                    this.startIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
                }
                if (!allChartableCandles.isEmpty()) {
                    int fromIndex = Math.max(0, this.startIndex - this.dataWindowStartIndex);
                    int toIndex = Math.min(fromIndex + this.barsPerScreen, allChartableCandles.size());
                    this.visibleKLines = (fromIndex < toIndex) ? allChartableCandles.subList(fromIndex, toIndex) : Collections.emptyList();
                } else {
                    this.visibleKLines = Collections.emptyList();
                }
            }
        } else { // Standard mode
            if (viewingLiveEdge && totalCandleCount > 0) {
                int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
                this.startIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
            }
            if (finalizedCandles.isEmpty()) {
                this.visibleKLines = Collections.emptyList();
                return;
            }
            int fromIndexInWindow = this.startIndex - this.dataWindowStartIndex;
            int toIndexInWindow = fromIndexInWindow + this.barsPerScreen;
            fromIndexInWindow = Math.max(0, fromIndexInWindow);
            toIndexInWindow = Math.min(toIndexInWindow, finalizedCandles.size());
            this.visibleKLines = (fromIndexInWindow < toIndexInWindow) ? finalizedCandles.subList(fromIndexInWindow, toIndexInWindow) : Collections.emptyList();
        }
    }

    // This is the new, fully asynchronous and thread-safe implementation.
    private record JumpResult(List<KLine> candles, int newWindowStart, int newStartIndex, int newBarsPerScreen) {}

    public void centerOnTrade(Trade trade) {
        if (trade == null) return;

        if (isConfiguredForReplay) {
            // Replaced time-based estimation with accurate index lookup ---
            ReplaySessionManager manager = ReplaySessionManager.getInstance();

            // 1. Find the actual M1 index for the trade entry/exit
            int entryM1Index = manager.findClosestM1IndexForTimestamp(trade.entryTime());
            int exitM1Index = manager.findClosestM1IndexForTimestamp(trade.exitTime());
            if (entryM1Index == -1) {
                logger.warn("Could not find M1 index for trade entry time: {}", trade.entryTime());
                return; // Can't jump if we can't find the start
            }
            if (exitM1Index == -1) exitM1Index = entryM1Index;


            // 2. Convert M1 indices to the current timeframe's indices
            long m1BarsPerCandle = currentDisplayTimeframe.getDuration().toMinutes();
            if (m1BarsPerCandle <= 0) m1BarsPerCandle = 1; // Prevent division by zero

            int entryIndexHTF = (int) (entryM1Index / m1BarsPerCandle);
            int exitIndexHTF = (int) (exitM1Index / m1BarsPerCandle);

            // 3. Calculate view parameters based on correct indices
            int tradeBarCount = Math.max(1, exitIndexHTF - entryIndexHTF + 1);
            int padding = (int) (tradeBarCount * 0.5) + 10; // Add some extra padding
            this.barsPerScreen = Math.max(20, Math.min(1000, tradeBarCount + (2 * padding)));
            int targetStartIndex = Math.max(0, entryIndexHTF - padding);

            // 4. Trigger the asynchronous reload
            this.viewingLiveEdge = false;
            rebuildReplayHistoryAroundIndexAsync(targetStartIndex);
            return;
        }

        // Standard Charting Mode - now uses a SwingWorker for a non-blocking UI.
        if (dbManager == null || currentSource == null || currentDisplayTimeframe == null) return;
        if (chartPanel != null) chartPanel.setLoading(true, "Jumping to trade...");

        SwingWorker<JumpResult, Void> worker = new SwingWorker<>() {
            @Override
            protected JumpResult doInBackground() throws Exception {
                String tfString = currentDisplayTimeframe.getDisplayName();
                Symbol symbol = new Symbol(currentSource.symbol());

                // Perform all DB lookups on the background thread
                int entryIndex = dbManager.findClosestTimestampIndex(symbol, tfString, trade.entryTime());
                int exitIndex = dbManager.findClosestTimestampIndex(symbol, tfString, trade.exitTime());
                if (entryIndex < 0) return null;
                if (exitIndex < 0) exitIndex = entryIndex;

                // Calculate the new view parameters
                int tradeBarCount = Math.max(1, exitIndex - entryIndex + 1);
                int padding = (int) (tradeBarCount * 0.5);
                int newBarsPerScreen = Math.max(20, Math.min(1000, tradeBarCount + (2 * padding)));
                int newStartIndex = Math.max(0, entryIndex - padding);

                // Calculate the required data window for the new view
                int newWindowCenter = newStartIndex + (newBarsPerScreen / 2);
                int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
                int newWindowSize = Math.min(DATA_WINDOW_SIZE, totalCandleCount - newWindowStart);
                
                // Fetch the data and package everything into a result object
                List<KLine> candles = dbManager.getKLinesByIndex(symbol, tfString, newWindowStart, newWindowSize);
                return new JumpResult(candles, newWindowStart, newStartIndex, newBarsPerScreen);
            }

            @Override
            protected void done() {
                try {
                    JumpResult result = get();
                    if (result != null) {
                        // --- All state is updated here, on the EDT, atomically before any repaint ---
                        barsPerScreen = result.newBarsPerScreen();
                        startIndex = result.newStartIndex();
                        viewingLiveEdge = false;
                        finalizedCandles = result.candles();
                        dataWindowStartIndex = result.newWindowStart();
                        triggerIndicatorRecalculation();
                    }
                } catch (Exception e) {
                    logger.error("Failed to jump to trade", e);
                    finalizedCandles.clear();
                } finally {
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                    updateView();
                }
            }
        };
        worker.execute();
    }


    private void calculateBoundaries() {
        if (visibleKLines.isEmpty()) {
            minPrice = BigDecimal.ZERO; maxPrice = BigDecimal.ONE; return;
        }
        minPrice = visibleKLines.get(0).low();
        maxPrice = visibleKLines.get(0).high();
        for (KLine k : visibleKLines) {
            if (k.low().compareTo(minPrice) < 0) minPrice = k.low();
            if (k.high().compareTo(maxPrice) > 0) maxPrice = k.high();
        }
    }

    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        long durationMillis = timeframe.getDuration().toMillis();
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
    
    private List<KLine> getAllChartableCandles() {
        if (isConfiguredForReplay) {
            List<KLine> all = new ArrayList<>(finalizedCandles);
            if (currentlyFormingCandle != null) all.add(currentlyFormingCandle);
            return all;
        }
        return finalizedCandles;
    }
    
    public void jumpToLiveEdge() {
        this.viewingLiveEdge = true;
        if (isConfiguredForReplay && currentDisplayTimeframe != Timeframe.M1) {
            rebuildReplayHistoryForLiveEdgeAsync();
        } else {
            triggerIndicatorRecalculation();
            updateView();
        }
    }

    public void increaseRightMargin() {
        this.rightMarginRatio = Math.min(0.9, this.rightMarginRatio + 0.05);
        if (viewingLiveEdge) updateView();
    }
    
    public void decreaseRightMargin() {
        this.rightMarginRatio = Math.max(0.05, this.rightMarginRatio - 0.05);
        if (viewingLiveEdge) updateView();
    }

    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (isConfiguredForReplay) {
            if (baseDataWindow == null || baseDataWindow.isEmpty()) {
                logger.warn("HTF data requested for {} but the base M1 data window is empty.", targetTimeframe);
                return Collections.emptyList();
            }
            logger.debug("Resampling for HTF {}: Using cached base data window with {} M1 bars.", targetTimeframe, baseDataWindow.size());
            return DataResampler.resample(this.baseDataWindow, targetTimeframe);
        } else {
            logger.warn("HTF data request in non-replay mode is not yet supported. Returning empty list for {}.", targetTimeframe);
            return Collections.emptyList();
        }
    }

    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    
    public DrawingManager getDrawingManager() {
        return DrawingManager.getInstance();
    }

    public List<KLine> getVisibleKLines() { return visibleKLines; }
    public DataSourceManager.ChartDataSource getCurrentSymbol() { return this.currentSource; }
    public Timeframe getCurrentDisplayTimeframe() { return this.currentDisplayTimeframe; }
    public int getStartIndex() { return startIndex; }
    public int getBarsPerScreen() { return barsPerScreen; }
    public int getTotalCandleCount() {
        if (isConfiguredForReplay) {
            if (currentDisplayTimeframe == Timeframe.M1) {
                return ReplaySessionManager.getInstance().getTotalBarCount();
            }
            return totalCandleCount;
        }
        return totalCandleCount;
    }
    public KLine getCurrentReplayKLine() { return ReplaySessionManager.getInstance().getCurrentBar(); }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public boolean isViewingLiveEdge() { return viewingLiveEdge; }
    public boolean isInReplayMode() { return isConfiguredForReplay; }
}