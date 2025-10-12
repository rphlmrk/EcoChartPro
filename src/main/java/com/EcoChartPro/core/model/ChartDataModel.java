package com.EcoChartPro.core.model;

import com.EcoChartPro.core.controller.ChartInteractionManager;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ChartDataModel implements ReplayStateListener, PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataModel.class);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int DATA_WINDOW_SIZE = 20000;
    private static final int DATA_WINDOW_TRIGGER_BUFFER = DATA_WINDOW_SIZE / 3;

    private List<KLine> visibleKLines;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager;
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private List<KLine> finalizedCandles;
    private KLine currentlyFormingCandle;
    private boolean isConfiguredForReplay = false;

    private int totalCandleCount = 0;
    private int dataWindowStartIndex = 0;
    private final IndicatorManager indicatorManager;
    private ChartPanel chartPanel;
    private SwingWorker<RebuildResult, Void> activeRebuildWorker = null;
    private List<KLine> baseDataWindow;

    // --- NEW FIELDS FOR PRE-FETCHING ---
    private volatile boolean isPreFetching = false;
    private RebuildResult preFetchedResult = null;
    private SwingWorker<RebuildResult, Void> preFetchWorker = null;

    private ChartInteractionManager interactionManager;


    private record RebuildResult(List<KLine> resampledCandles, KLine formingCandle, int newWindowStart, List<KLine> rawM1Slice) {}

    public ChartDataModel() {
        this.visibleKLines = new ArrayList<>();
        this.finalizedCandles = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.baseDataWindow = new ArrayList<>();
        // Add listener to repaint when drawings change (e.g., symbol switch)
        DrawingManager.getInstance().addPropertyChangeListener("activeSymbolChanged", this);
    }

    // [MODIFIED] Method to handle property changes from listened-to services.
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("activeSymbolChanged".equals(evt.getPropertyName())) {
            // A change in the active symbol requires a repaint.
            fireDataUpdated();
        } else if ("viewStateChanged".equals(evt.getPropertyName())) {
            // The view state (pan/zoom) changed, so we need to update our data view.
            updateView();
        }
    }

    public void setInteractionManager(ChartInteractionManager interactionManager) {
        this.interactionManager = interactionManager;
        this.interactionManager.addPropertyChangeListener(this);
    }

    // [NEW] Cleanup method for good resource management.
    public void cleanup() {
        DrawingManager.getInstance().removePropertyChangeListener("activeSymbolChanged", this);
        if (isConfiguredForReplay) {
            ReplaySessionManager.getInstance().removeListener(this);
        }
        if (this.interactionManager != null) {
            this.interactionManager.removePropertyChangeListener(this);
        }
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
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return;
        }
        
        if (handleDataWindowLoading()) {
            return; 
        }
        
        assembleVisibleKLines();
        calculateBoundaries();
        fireDataUpdated();
        checkForPreFetchTrigger();
    }
    
    private boolean handleDataWindowLoading() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return true; 
        }

        List<KLine> sourceData = getAllChartableCandles();
        int currentWindowEnd = dataWindowStartIndex + sourceData.size();
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        boolean needsReload = sourceData.isEmpty() ||
                              (currentStartIndex < dataWindowStartIndex + DATA_WINDOW_TRIGGER_BUFFER && dataWindowStartIndex > 0) ||
                              (currentStartIndex + currentBarsPerScreen > currentWindowEnd - DATA_WINDOW_TRIGGER_BUFFER && currentWindowEnd < totalCandleCount);
        
        if (needsReload) {
            int newWindowCenter = currentStartIndex + (currentBarsPerScreen / 2);
            int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
            newWindowStart = Math.min(newWindowStart, Math.max(0, totalCandleCount - DATA_WINDOW_SIZE));

            if (preFetchedResult != null && preFetchedResult.newWindowStart() == newWindowStart) {
                logger.debug("Pre-fetch cache HIT. Applying cached data window.");
                applyRebuildResult(preFetchedResult, currentStartIndex);
                preFetchedResult = null; 
                triggerIndicatorRecalculation();
                updateView();
                return false; 
            } else {
                if(preFetchedResult != null) {
                    logger.warn("Pre-fetch cache MISS. Needed window starting at {} but had {}. Discarding cache.", newWindowStart, preFetchedResult.newWindowStart());
                    preFetchedResult = null;
                }
                rebuildHistoryAsync(currentStartIndex, false);
                return true; 
            }
        }
        return false;
    }


    public void triggerIndicatorRecalculation() {
        indicatorManager.recalculateAll(this, getIndicatorDataSlice());
    }
    
    private List<KLine> getIndicatorDataSlice() {
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        if (isConfiguredForReplay) {
            if (currentDisplayTimeframe == Timeframe.M1) {
                ReplaySessionManager manager = ReplaySessionManager.getInstance();
                int calculationEndIndex = currentStartIndex + currentBarsPerScreen;
                int calculationStartIndex = Math.max(0, currentStartIndex - INDICATOR_LOOKBACK_BUFFER);
                int fetchCount = calculationEndIndex - calculationStartIndex;
                return manager.getOneMinuteBars(calculationStartIndex, fetchCount);
            } else {
                 List<KLine> sourceData = getAllChartableCandles();
                 if (sourceData.isEmpty()) return Collections.emptyList();

                int viewStartInWindow = currentStartIndex - dataWindowStartIndex;
                int calculationEndIndex = Math.min(viewStartInWindow + currentBarsPerScreen, sourceData.size());
                int calculationStartIndex = Math.max(0, viewStartInWindow - INDICATOR_LOOKBACK_BUFFER);

                if (calculationStartIndex >= calculationEndIndex || calculationStartIndex < 0 || calculationEndIndex > sourceData.size()) {
                    return Collections.emptyList();
                }
                return sourceData.subList(calculationStartIndex, calculationEndIndex);
            }
        } else {
            if (finalizedCandles.isEmpty()) {
                 return Collections.emptyList();
            }
            int viewStartInWindow = currentStartIndex - this.dataWindowStartIndex;
            int calculationEndIndex = Math.min(viewStartInWindow + currentBarsPerScreen, finalizedCandles.size());
            int calculationStartIndex = Math.max(0, viewStartInWindow - INDICATOR_LOOKBACK_BUFFER);
            return (calculationStartIndex < calculationEndIndex) ? finalizedCandles.subList(calculationStartIndex, calculationEndIndex) : Collections.emptyList();
        }
    }

    /**
     * [MODIFIED] This method's signature now takes a Timeframe object instead of a string.
     */
    public void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        this.currentSource = source;
        this.isConfiguredForReplay = false;
        this.currentDisplayTimeframe = timeframe;

        if (dbManager == null || timeframe == null) {
             clearData();
             return;
        }
        
        this.totalCandleCount = dbManager.getTotalKLineCount(new Symbol(source.symbol()), timeframe.displayName());
        
        if(totalCandleCount == 0){
             clearData();
             return;
        }
        
        this.finalizedCandles.clear();
        this.dataWindowStartIndex = 0;
        this.currentlyFormingCandle = null;
        
        int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
        int initialStartIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
        rebuildHistoryAsync(initialStartIndex, false);
    }

    @Override
    public void onReplayTick(KLine newM1Bar) {
        if (!isConfiguredForReplay) return;

        if (baseDataWindow != null) {
            baseDataWindow.add(newM1Bar);
        }

        if (currentDisplayTimeframe == Timeframe.M1) {
            totalCandleCount++;
        } else {
            processNewM1Bar(newM1Bar);
        }
        
        assembleVisibleKLines();
        calculateBoundaries();
        triggerIndicatorRecalculation();
        fireDataUpdated();
    }
    
    @Override
    public void onReplaySessionStart() {
        if (!isConfiguredForReplay) return;
        // When a replay session (or active symbol) starts, we need to build the initial history.
        setDisplayTimeframe(this.currentDisplayTimeframe, true);
    }
    
    @Override
    public void onReplayStateChanged() {
    }

    public void setDisplayTimeframe(Timeframe newTimeframe) {
        setDisplayTimeframe(newTimeframe, false);
    }

    public void setDisplayTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && this.currentDisplayTimeframe == newTimeframe) return;
        
        clearData(); // Clear old data immediately to prevent rendering inconsistencies.
        interactionManager.setAutoScalingY(true);

        this.currentDisplayTimeframe = newTimeframe;

        if (isConfiguredForReplay) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int m1HeadIndex = manager.getReplayHeadIndex();

            if (m1HeadIndex < 0) {
                clearData();
                return;
            }

            if (currentDisplayTimeframe != Timeframe.M1 && !currentDisplayTimeframe.duration().isZero()) {
                totalCandleCount = (m1HeadIndex + 1) / (int)currentDisplayTimeframe.duration().toMinutes();
            } else {
                totalCandleCount = m1HeadIndex + 1;
            }

            int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
            int targetStartIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
            
            rebuildHistoryAsync(targetStartIndex, false);
        } else {
            if (dbManager == null) return;
            this.totalCandleCount = dbManager.getTotalKLineCount(new Symbol(currentSource.symbol()), newTimeframe.displayName());
            int initialStartIndex = Math.max(0, totalCandleCount - interactionManager.getBarsPerScreen());
            rebuildHistoryAsync(initialStartIndex, false);
        }
    }

    public void clearData() {
        this.visibleKLines.clear();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
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
    
    private void rebuildHistoryAsync(final int targetStartIndex, boolean isPreFetch) {
        if (!isPreFetch) {
            if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
            // Use the record's accessor method 'displayName()'
            if (chartPanel != null) chartPanel.setLoading(true, "Building " + currentDisplayTimeframe.displayName() + " history...");
        }

        SwingWorker<RebuildResult, Void> worker = new SwingWorker<>() {
            @Override
            protected RebuildResult doInBackground() throws Exception {
                int newWindowCenter = targetStartIndex + (interactionManager.getBarsPerScreen() / 2);
                int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
                newWindowStart = Math.min(newWindowStart, Math.max(0, totalCandleCount - DATA_WINDOW_SIZE));

                if (isConfiguredForReplay) {
                    return fetchReplayData(newWindowStart);
                } else {
                    return fetchStandardData(newWindowStart);
                }
            }

            @Override
            protected void done() {
                try {
                    RebuildResult result = get();
                    if (isPreFetch) {
                        preFetchedResult = result;
                        isPreFetching = false;
                        logger.debug("Pre-fetch complete for window starting at {}", result.newWindowStart());
                    } else {
                        applyRebuildResult(result, targetStartIndex);
                        triggerIndicatorRecalculation();
                        
                        // Set the start index. This might or might not fire an event.
                        interactionManager.setStartIndex(targetStartIndex);
                        
                        // Manually call updateView() to guarantee the chart is drawn
                        // after the initial load, especially in cases where setStartIndex 
                        // doesn't fire a property change event (e.g., index is already 0).
                        updateView();
                        
                        if (chartPanel != null) chartPanel.setLoading(false, null);
                    }
                } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                    logger.warn("Data loading task was cancelled by a newer request.");
                } catch (ExecutionException e) {
                    logger.error("Failed to load chart data in background", e.getCause());
                } finally {
                    if (isPreFetch) isPreFetching = false;
                    else activeRebuildWorker = null;
                }
            }
        };

        if (isPreFetch) {
            this.preFetchWorker = worker;
        } else {
            this.activeRebuildWorker = worker;
        }
        worker.execute();
    }

    private void applyRebuildResult(RebuildResult result, int targetStartIndex) {
        if (currentDisplayTimeframe == Timeframe.M1 && isConfiguredForReplay) {
            baseDataWindow = result.rawM1Slice();
            finalizedCandles = Collections.emptyList();
        } else {
            finalizedCandles = result.resampledCandles();
            baseDataWindow = result.rawM1Slice();
        }
        currentlyFormingCandle = result.formingCandle();
        dataWindowStartIndex = result.newWindowStart();
        
        if (isConfiguredForReplay) {
            if (currentDisplayTimeframe != Timeframe.M1 && !currentDisplayTimeframe.duration().isZero()) {
                totalCandleCount = (ReplaySessionManager.getInstance().getReplayHeadIndex() + 1) / (int)currentDisplayTimeframe.duration().toMinutes();
            } else {
                totalCandleCount = ReplaySessionManager.getInstance().getReplayHeadIndex() + 1;
            }
        }
    }
    
    private RebuildResult fetchReplayData(int newWindowStartInFinalData) throws Exception {
        ReplaySessionManager manager = ReplaySessionManager.getInstance();
        if (currentDisplayTimeframe == Timeframe.M1) {
            int fetchCount = Math.min(DATA_WINDOW_SIZE, manager.getReplayHeadIndex() + 1 - newWindowStartInFinalData);
            List<KLine> m1HistorySlice = manager.getOneMinuteBars(newWindowStartInFinalData, fetchCount);
            return new RebuildResult(null, null, newWindowStartInFinalData, m1HistorySlice);
        } else {
            long m1BarsPerTargetCandle = currentDisplayTimeframe.duration().toMinutes();
            if (m1BarsPerTargetCandle <= 0) m1BarsPerTargetCandle = 1;

            int m1LookbackForWindow = (int) ((DATA_WINDOW_SIZE + INDICATOR_LOOKBACK_BUFFER) * m1BarsPerTargetCandle);
            int m1FetchStartIndex = (int) (newWindowStartInFinalData * m1BarsPerTargetCandle);
            int availableM1Bars = manager.getReplayHeadIndex() + 1 - m1FetchStartIndex;
            int m1FetchCount = Math.min(m1LookbackForWindow, availableM1Bars);

            List<KLine> m1HistorySlice = manager.getOneMinuteBars(m1FetchStartIndex, m1FetchCount);
            List<KLine> resampledData = DataResampler.resample(m1HistorySlice, currentDisplayTimeframe);

            KLine formingCandle = null;
            if (!resampledData.isEmpty() && !m1HistorySlice.isEmpty()) {
                Instant lastM1TsInSlice = m1HistorySlice.get(m1HistorySlice.size() - 1).timestamp();
                Instant lastResampledCandleTs = resampledData.get(resampledData.size() - 1).timestamp();
                if (getIntervalStart(lastM1TsInSlice, currentDisplayTimeframe).equals(lastResampledCandleTs)) {
                    formingCandle = resampledData.remove(resampledData.size() - 1);
                }
            }
            return new RebuildResult(resampledData, formingCandle, newWindowStartInFinalData, m1HistorySlice);
        }
    }
    
    private RebuildResult fetchStandardData(int newWindowStart) {
        int newWindowSize = Math.min(DATA_WINDOW_SIZE, totalCandleCount - newWindowStart);
        String tfString = currentDisplayTimeframe.displayName();
        List<KLine> candles = dbManager.getKLinesByIndex(new Symbol(currentSource.symbol()), tfString, newWindowStart, newWindowSize);
        return new RebuildResult(candles, null, newWindowStart, candles);
    }
    
    private void checkForPreFetchTrigger() {
        if (isPreFetching || (activeRebuildWorker != null && !activeRebuildWorker.isDone())) {
            return;
        }

        int preFetchTriggerBufferSize = DATA_WINDOW_SIZE / 4;
        List<KLine> sourceData = getAllChartableCandles();
        int currentWindowEnd = dataWindowStartIndex + sourceData.size();
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        int nextWindowStart = -1;

        if (currentStartIndex < dataWindowStartIndex + preFetchTriggerBufferSize && dataWindowStartIndex > 0) {
            nextWindowStart = Math.max(0, dataWindowStartIndex - (DATA_WINDOW_SIZE / 2));
        } else if (currentStartIndex + currentBarsPerScreen > currentWindowEnd - preFetchTriggerBufferSize && currentWindowEnd < totalCandleCount) {
            nextWindowStart = dataWindowStartIndex + (DATA_WINDOW_SIZE / 2);
        }

        if (nextWindowStart != -1 && (preFetchedResult == null || preFetchedResult.newWindowStart() != nextWindowStart)) {
            isPreFetching = true;
            logger.debug("Starting pre-fetch for data window starting at index {}", nextWindowStart);
            rebuildHistoryAsync(nextWindowStart, true);
        }
    }

    private void processNewM1Bar(KLine m1Bar) {
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
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();

        if (isConfiguredForReplay && currentDisplayTimeframe == Timeframe.M1) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int headIndex = manager.getReplayHeadIndex();
            if (headIndex < 0) { this.visibleKLines = Collections.emptyList(); return; }

            int fetchStart = currentStartIndex;
            int fetchCount = Math.min(currentBarsPerScreen, headIndex + 1 - fetchStart);
            this.visibleKLines = manager.getOneMinuteBars(fetchStart, fetchCount);
            this.baseDataWindow = this.visibleKLines;
        } else {
            List<KLine> allChartableCandles = getAllChartableCandles();
            if (!allChartableCandles.isEmpty()) {
                int fromIndex = Math.max(0, currentStartIndex - this.dataWindowStartIndex);
                int toIndex = Math.min(fromIndex + currentBarsPerScreen, allChartableCandles.size());
                this.visibleKLines = (fromIndex < toIndex) ? allChartableCandles.subList(fromIndex, toIndex) : Collections.emptyList();
            } else {
                this.visibleKLines = Collections.emptyList();
            }
        }
    }

    public void centerOnTrade(Trade trade) {
        if (trade == null) return;

        int targetStartIndex;
        if (isConfiguredForReplay) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int entryM1Index = manager.findClosestM1IndexForTimestamp(trade.entryTime());
            if (entryM1Index == -1) {
                logger.warn("Could not find M1 index for trade entry time: {}", trade.entryTime());
                return;
            }
            long m1BarsPerCandle = (currentDisplayTimeframe == Timeframe.M1) ? 1 : currentDisplayTimeframe.duration().toMinutes();
            targetStartIndex = (int) (entryM1Index / m1BarsPerCandle);
        } else {
            if (dbManager == null) return;
            targetStartIndex = dbManager.findClosestTimestampIndex(new Symbol(currentSource.symbol()), currentDisplayTimeframe.displayName(), trade.entryTime());
        }

        if (targetStartIndex < 0) return;

        int centerIndex = Math.max(0, targetStartIndex - (interactionManager.getBarsPerScreen() / 2));
        interactionManager.setStartIndex(centerIndex);
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
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp;
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
    
    private List<KLine> getAllChartableCandles() {
        if (isConfiguredForReplay && currentDisplayTimeframe == Timeframe.M1) {
            return baseDataWindow;
        }
        
        List<KLine> all = new ArrayList<>(finalizedCandles);
        if (currentlyFormingCandle != null) {
            all.add(currentlyFormingCandle);
        }
        return all;
    }
    
    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (isConfiguredForReplay) {
            if (baseDataWindow == null || baseDataWindow.isEmpty()) {
                logger.warn("HTF data requested for {} but the base M1 data window is empty.", targetTimeframe);
                return Collections.emptyList();
            }
            logger.debug("Resampling for HTF {}: Using base M1 data window with {} bars.", targetTimeframe, baseDataWindow.size());
            return DataResampler.resample(this.baseDataWindow, targetTimeframe);
        } else {
            if (dbManager != null && currentSource != null) {
                String tfString = targetTimeframe.displayName();
                return dbManager.getAllKLines(new Symbol(currentSource.symbol()), tfString);
            }
            return Collections.emptyList();
        }
    }

    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    public List<KLine> getVisibleKLines() { return visibleKLines; }
    public DataSourceManager.ChartDataSource getCurrentSymbol() { return this.currentSource; }
    public Timeframe getCurrentDisplayTimeframe() { return this.currentDisplayTimeframe; }
    public int getTotalCandleCount() { return totalCandleCount; }
    public KLine getCurrentReplayKLine() { return ReplaySessionManager.getInstance().getCurrentBar(); }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public boolean isInReplayMode() { return isConfiguredForReplay; }
}