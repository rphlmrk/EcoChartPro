package com.EcoChartPro.core.model;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.ReplayStateListener;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.generator.ChartDataGenerator;
import com.EcoChartPro.data.generator.KagiGenerator;
import com.EcoChartPro.data.generator.PointAndFigureGenerator;
import com.EcoChartPro.data.generator.RangeBarGenerator;
import com.EcoChartPro.data.generator.RenkoGenerator;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.core.indicator.IndicatorManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.core.settings.SettingsManager;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ChartDataModel implements ReplayStateListener, PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataModel.class);

    public enum ChartMode { REPLAY, LIVE }
    private ChartMode currentMode = ChartMode.REPLAY;

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int DATA_WINDOW_SIZE = 20000;
    private static final int DATA_WINDOW_TRIGGER_BUFFER = DATA_WINDOW_SIZE / 3;

    private List<? extends AbstractChartData> visibleData;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager;
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private List<? extends AbstractChartData> chartData;
    private KLine currentlyFormingCandle;

    private int totalCandleCount = 0;
    private int dataWindowStartIndex = 0;
    private final IndicatorManager indicatorManager;
    private ChartPanel chartPanel;
    private SwingWorker<RebuildResult, Void> activeRebuildWorker = null;
    private List<KLine> baseDataWindow;

    private volatile boolean isPreFetching = false;
    private RebuildResult preFetchedResult = null;
    private SwingWorker<RebuildResult, Void> preFetchWorker = null;
    private final Map<Timeframe, List<KLine>> htfCache = new ConcurrentHashMap<>();
    private DataProvider liveDataProvider;
    private Consumer<KLine> liveDataConsumer;
    private ChartInteractionManager interactionManager;
    private ChartDataGenerator<?> activeGenerator;

    private record RebuildResult(List<? extends AbstractChartData> chartData, KLine formingCandle, int newWindowStart, List<KLine> rawM1Slice) {}

    public ChartDataModel() {
        this.visibleData = new ArrayList<>();
        this.chartData = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.baseDataWindow = new ArrayList<>();
        DrawingManager.getInstance().addPropertyChangeListener("activeSymbolChanged", this);
        SettingsManager.getInstance().addPropertyChangeListener("chartTypeChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("activeSymbolChanged".equals(evt.getPropertyName())) {
            fireDataUpdated();
        } else if ("viewStateChanged".equals(evt.getPropertyName())) {
            updateView();
        } else if ("chartTypeChanged".equals(evt.getPropertyName())) {
            rebuildChartDataAsync(0, false, () -> {
                if (interactionManager != null) {
                    interactionManager.jumpToLiveEdge();
                }
            });
        }
    }

    public void setInteractionManager(ChartInteractionManager interactionManager) {
        this.interactionManager = interactionManager;
        this.interactionManager.addPropertyChangeListener(this);
    }

    public void cleanup() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            activeRebuildWorker.cancel(true);
        }
        DrawingManager.getInstance().removePropertyChangeListener("activeSymbolChanged", this);
        SettingsManager.getInstance().removePropertyChangeListener(this);
        if (currentMode == ChartMode.LIVE && liveDataProvider != null && liveDataConsumer != null && currentSource != null && currentDisplayTimeframe != null) {
            Timeframe streamTf = SettingsManager.getInstance().getCurrentChartType().isTimeBased() ? currentDisplayTimeframe : Timeframe.M1;
            liveDataProvider.disconnectFromLiveStream(currentSource.symbol(), streamTf.displayName(), liveDataConsumer);
        }
        if (currentMode == ChartMode.REPLAY) {
            ReplaySessionManager.getInstance().removeListener(this);
        }
        if (this.interactionManager != null) {
            this.interactionManager.removePropertyChangeListener(this);
        }
    }

    public void setView(ChartPanel chartPanel) {
        this.chartPanel = chartPanel;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }
    public void fireDataUpdated() { pcs.firePropertyChange("dataUpdated", null, null); }
    
    public void setDatabaseManager(DatabaseManager dbManager, DataSourceManager.ChartDataSource source) {
        this.dbManager = dbManager;
        this.currentSource = source;
    }

    public DatabaseManager getDbManager() { return this.dbManager; }

    private void updateView() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return;
        }
        if (currentMode == ChartMode.REPLAY && handleDataWindowLoading()) {
            return; 
        }
        assembleVisibleData();
        calculateBoundaries();
        fireDataUpdated();
        if (currentMode == ChartMode.REPLAY) checkForPreFetchTrigger();
    }
    
    private boolean handleDataWindowLoading() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return true; 
        }
        if (!SettingsManager.getInstance().getCurrentChartType().isTimeBased()) {
            return false;
        }
        List<? extends AbstractChartData> sourceData = getAllChartableCandles();
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
                rebuildChartDataAsync(currentStartIndex, false);
                return true; 
            }
        }
        return false;
    }

    public void triggerIndicatorRecalculation() {
        indicatorManager.recalculateAll(this, getIndicatorDataSlice());
    }
    
    private List<KLine> getIndicatorDataSlice() {
        if (currentMode == ChartMode.REPLAY) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int m1HeadIndex = manager.getReplayHeadIndex();
            int m1Lookback = (int) (INDICATOR_LOOKBACK_BUFFER * (currentDisplayTimeframe != null ? currentDisplayTimeframe.duration().toMinutes() : 1));
            int fetchStart = Math.max(0, m1HeadIndex - m1Lookback);
            int fetchCount = m1HeadIndex - fetchStart + 1;
            return manager.getOneMinuteBars(fetchStart, fetchCount);
        } else {
            return this.baseDataWindow;
        }
    }

    public void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        if (currentSource != null && currentSource.equals(source) && 
            currentDisplayTimeframe != null && currentDisplayTimeframe.equals(timeframe)) {
            return;
        }
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            activeRebuildWorker.cancel(true);
        }
        if (currentMode == ChartMode.LIVE && liveDataProvider != null && liveDataConsumer != null && currentSource != null && currentDisplayTimeframe != null) {
            Timeframe streamTf = SettingsManager.getInstance().getCurrentChartType().isTimeBased() ? currentDisplayTimeframe : Timeframe.M1;
            liveDataProvider.disconnectFromLiveStream(currentSource.symbol(), streamTf.displayName(), liveDataConsumer);
        }

        clearData();
        this.currentSource = source;
        this.currentMode = ChartMode.LIVE;
        this.currentDisplayTimeframe = timeframe;

        if (source.providerName() != null) {
            switch (source.providerName()) {
                case "Binance": this.liveDataProvider = new BinanceProvider(); break;
                case "OKX": this.liveDataProvider = new OkxProvider(); break;
                default: this.liveDataProvider = null;
            }
        } else {
            this.liveDataProvider = null;
        }
        
        if (timeframe == null) return;
        setDisplayTimeframe(timeframe, true);
    }

    private void onLiveKLineUpdate(KLine newTick) {
        SwingUtilities.invokeLater(() -> {
            if (currentMode != ChartMode.LIVE) return;
            
            ChartType chartType = SettingsManager.getInstance().getCurrentChartType();

            if (chartType.isTimeBased()) {
                Instant intervalStart = getIntervalStart(newTick.timestamp(), currentDisplayTimeframe);
                if (currentlyFormingCandle == null) {
                    currentlyFormingCandle = newTick;
                } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
                    ((List<KLine>)chartData).add(currentlyFormingCandle);
                    currentlyFormingCandle = newTick;
                    htfCache.clear();
                } else {
                    currentlyFormingCandle = new KLine(currentlyFormingCandle.timestamp(), currentlyFormingCandle.open(), currentlyFormingCandle.high().max(newTick.high()), currentlyFormingCandle.low().min(newTick.low()), newTick.close(), currentlyFormingCandle.volume().add(newTick.volume()));
                }
            } else {
                baseDataWindow.add(newTick);
                if (activeGenerator != null) {
                    // Call the stateful generator with only the new tick.
                    // The generator will incrementally update and return the full list.
                    this.chartData = activeGenerator.generate(List.of(newTick));
                    this.totalCandleCount = this.chartData.size();
    
                    if (interactionManager != null) {
                        // This updates the start index to keep the latest bar in view
                        // and triggers the repaint via updateView().
                        interactionManager.jumpToLiveEdge();
                    } else {
                        updateView();
                    }
                }
                return; // We're done for non-time-based charts.
            }

            if (interactionManager != null) interactionManager.onReplayTick(newTick);
            triggerIndicatorRecalculation();
            updateView();
        });
    }

    @Override public void onReplayTick(KLine newBar) { /* Now handled by onReplayStateChanged */ }
    @Override public void onReplaySessionStart() { if (currentMode == ChartMode.REPLAY) setDisplayTimeframe(this.currentDisplayTimeframe, true); }
    
    @Override 
    public void onReplayStateChanged() {
        // --- FIX: This is the main driver for replay updates. ---
        // It triggers a full data regeneration for the current view, which works for ALL chart types.
        if (currentMode != ChartMode.REPLAY) return;
        rebuildChartDataAsync(interactionManager.getStartIndex(), false);
    }

    public void setDisplayTimeframe(Timeframe newTimeframe) { setDisplayTimeframe(newTimeframe, false); }

    public void setDisplayTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && this.currentDisplayTimeframe != null && this.currentDisplayTimeframe.equals(newTimeframe)) return;
        
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
        htfCache.clear();
        this.currentDisplayTimeframe = newTimeframe;
        
        if (currentMode == ChartMode.LIVE) {
            ChartType chartType = SettingsManager.getInstance().getCurrentChartType();
            boolean needsM1Base = !chartType.isTimeBased();
            Timeframe tfToFetch = needsM1Base ? Timeframe.M1 : newTimeframe;
            int barsToFetch = needsM1Base ? 5000 : 1000;

            if (chartPanel != null) chartPanel.setLoading(true, "Loading " + tfToFetch.displayName() + " history...");
            new SwingWorker<List<KLine>, Void>() {
                @Override protected List<KLine> doInBackground() { return fetchDirectTfData(tfToFetch, barsToFetch); }
                @Override protected void done() {
                    try {
                        baseDataWindow = get();
                        if (baseDataWindow == null) baseDataWindow = new ArrayList<>();

                        if (needsM1Base) {
                            activeGenerator = createGeneratorForType(chartType);
                        } else {
                            activeGenerator = null;
                        }

                        rebuildChartDataAsync(0, false, () -> {
                            if (interactionManager != null) interactionManager.jumpToLiveEdge();
                            if (liveDataProvider != null) {
                                if (liveDataConsumer != null) liveDataProvider.disconnectFromLiveStream(currentSource.symbol(), needsM1Base ? newTimeframe.displayName() : tfToFetch.displayName(), liveDataConsumer);
                                liveDataConsumer = ChartDataModel.this::onLiveKLineUpdate;
                                liveDataProvider.connectToLiveStream(currentSource.symbol(), tfToFetch.displayName(), liveDataConsumer);
                            }
                            if (chartPanel != null) chartPanel.setLoading(false, null);
                        });
                    } catch (Exception e) { logger.error("Failed to load live data.", e); if (chartPanel != null) chartPanel.setLoading(false, null); }
                }
            }.execute();
        } else {
            resetDataForTimeframeSwitch();
            if (interactionManager != null) interactionManager.setAutoScalingY(true);
            rebuildChartDataAsync(0, false, () -> {
                if (interactionManager != null) interactionManager.jumpToLiveEdge();
            });
        }
    }
    
    public void clearData() {
        this.visibleData = Collections.emptyList();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
        this.chartData = Collections.emptyList();
        this.currentlyFormingCandle = null;
        this.baseDataWindow.clear();
        this.indicatorManager.clearAllIndicators();
        this.totalCandleCount = 0;
        this.dataWindowStartIndex = 0;
        this.htfCache.clear();
        this.activeGenerator = null;
        fireDataUpdated();
    }

    private void resetDataForTimeframeSwitch() {
        clearData();
    }

    public void configureForReplay(Timeframe initialDisplayTimeframe, DataSourceManager.ChartDataSource source) {
        clearData();
        this.currentMode = ChartMode.REPLAY;
        this.currentDisplayTimeframe = initialDisplayTimeframe;
        this.currentSource = source;
    }
    
    private void rebuildChartDataAsync(final int targetStartIndex, boolean isPreFetch, Runnable onCompleteCallback) {
        if (!isPreFetch) {
            if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
            if (chartPanel != null) chartPanel.setLoading(true, "Building " + SettingsManager.getInstance().getCurrentChartType().getDisplayName() + " chart...");
        }

        SwingWorker<RebuildResult, Void> worker = new SwingWorker<>() {
            @Override protected RebuildResult doInBackground() throws Exception { return generateChartDataForView(targetStartIndex); }
            @Override protected void done() {
                try {
                    RebuildResult result = get();
                    if (isPreFetch) {
                        preFetchedResult = result; isPreFetching = false;
                        logger.debug("Pre-fetch complete for window starting at {}", result.newWindowStart());
                    } else {
                        applyRebuildResult(result, targetStartIndex);
                        triggerIndicatorRecalculation();
                        updateView();
                        if (onCompleteCallback != null) onCompleteCallback.run();
                    }
                } catch (InterruptedException | java.util.concurrent.CancellationException e) {
                    logger.warn("Data loading task was cancelled by a newer request.");
                } catch (ExecutionException e) {
                    logger.error("Failed to load chart data in background", e.getCause());
                } finally {
                    if (isPreFetch) isPreFetching = false;
                    else { activeRebuildWorker = null; if (chartPanel != null) chartPanel.setLoading(false, null); }
                }
            }
        };

        if (isPreFetch) this.preFetchWorker = worker;
        else this.activeRebuildWorker = worker;
        worker.execute();
    }

    private void rebuildChartDataAsync(final int targetStartIndex, boolean isPreFetch) { rebuildChartDataAsync(targetStartIndex, isPreFetch, null); }
    
    private RebuildResult generateChartDataForView(int targetStartIndex) {
        ChartType chartType = SettingsManager.getInstance().getCurrentChartType();
        List<KLine> sourceData;

        if (currentMode == ChartMode.REPLAY) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            sourceData = manager.getOneMinuteBars(0, manager.getReplayHeadIndex() + 1);
        } else {
            sourceData = this.baseDataWindow;
        }

        if (sourceData == null || sourceData.isEmpty()) {
            return new RebuildResult(Collections.emptyList(), null, 0, Collections.emptyList());
        }

        if (chartType.isTimeBased()) {
            if (currentMode == ChartMode.REPLAY) {
                return fetchReplayData(targetStartIndex);
            }
            List<KLine> resampledData = DataResampler.resample(sourceData, currentDisplayTimeframe);
            KLine formingCandle = null;
            if (!resampledData.isEmpty()) {
                formingCandle = resampledData.remove(resampledData.size() - 1);
            }
            this.totalCandleCount = resampledData.size() + (formingCandle != null ? 1 : 0);
            return new RebuildResult(resampledData, formingCandle, 0, sourceData);

        } else {
            List<? extends AbstractChartData> generatedData;
            ChartDataGenerator<?> generator = (currentMode == ChartMode.LIVE && activeGenerator != null) ? activeGenerator : createGeneratorForType(chartType);

            if (generator != null) {
                generatedData = generator.generate(sourceData);
            } else {
                generatedData = Collections.emptyList();
            }

            this.totalCandleCount = generatedData.size();
            return new RebuildResult(generatedData, null, 0, sourceData);
        }
    }

    private ChartDataGenerator<?> createGeneratorForType(ChartType chartType) {
        SettingsManager settings = SettingsManager.getInstance();
        switch (chartType) {
            case RENKO: return new RenkoGenerator(settings.getRenkoBrickSize());
            case RANGE_BARS: return new RangeBarGenerator(settings.getRangeBarSize());
            case KAGI: return new KagiGenerator(BigDecimal.valueOf(10)); // TODO: Make configurable
            case POINT_AND_FIGURE: return new PointAndFigureGenerator(settings.getPfBoxSize(), settings.getPfReversalAmount());
            default: return null;
        }
    }
    
    private void applyRebuildResult(RebuildResult result, int targetStartIndex) {
        chartData = result.chartData() != null ? result.chartData() : new ArrayList<>();
        currentlyFormingCandle = result.formingCandle();
        dataWindowStartIndex = result.newWindowStart();
        
        if (currentMode == ChartMode.REPLAY) {
            this.baseDataWindow = result.rawM1Slice() != null ? new ArrayList<>(result.rawM1Slice()) : new ArrayList<>();
        }

        if (SettingsManager.getInstance().getCurrentChartType().isTimeBased()) {
            if (currentMode == ChartMode.REPLAY && currentDisplayTimeframe != Timeframe.M1) {
                long durationMinutes = currentDisplayTimeframe.duration().toMinutes();
                if (durationMinutes > 0) totalCandleCount = (ReplaySessionManager.getInstance().getReplayHeadIndex() + 1) / (int)durationMinutes;
            }
        } else {
            totalCandleCount = chartData.size();
        }
    }
    
    private RebuildResult fetchReplayData(int newWindowStartInFinalData) {
        ReplaySessionManager manager = ReplaySessionManager.getInstance();
        if (currentDisplayTimeframe == Timeframe.M1) {
            int fetchCount = Math.min(DATA_WINDOW_SIZE, manager.getReplayHeadIndex() + 1 - newWindowStartInFinalData);
            List<KLine> m1HistorySlice = manager.getOneMinuteBars(newWindowStartInFinalData, fetchCount);
            return new RebuildResult(m1HistorySlice, null, newWindowStartInFinalData, m1HistorySlice);
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
    
    private void checkForPreFetchTrigger() {
        if (isPreFetching || (activeRebuildWorker != null && !activeRebuildWorker.isDone())) return;
        if (currentMode == ChartMode.LIVE || !SettingsManager.getInstance().getCurrentChartType().isTimeBased()) return;

        int preFetchTriggerBufferSize = DATA_WINDOW_SIZE / 4;
        List<? extends AbstractChartData> sourceData = getAllChartableCandles();
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
            rebuildChartDataAsync(nextWindowStart, true);
        }
    }

    private void assembleVisibleData() {
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();
        
        List<? extends AbstractChartData> allChartableData = getAllChartableCandles();
        if (!allChartableData.isEmpty()) {
            int fromIndex = Math.max(0, currentStartIndex - this.dataWindowStartIndex);
            int toIndex = Math.min(fromIndex + currentBarsPerScreen, allChartableData.size());
            this.visibleData = (fromIndex < toIndex) ? allChartableData.subList(fromIndex, toIndex) : Collections.emptyList();
        } else {
            this.visibleData = Collections.emptyList();
        }
    }

    public void centerOnTrade(Trade trade) {
        if (trade == null) return;
        int targetStartIndex;
        if (currentMode == ChartMode.REPLAY) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int entryM1Index = manager.findClosestM1IndexForTimestamp(trade.entryTime());
            if (entryM1Index == -1) { logger.warn("Could not find M1 index for trade entry time: {}", trade.entryTime()); return; }
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
        if (visibleData.isEmpty()) { minPrice = BigDecimal.ZERO; maxPrice = BigDecimal.ONE; return; }
        minPrice = visibleData.get(0).low();
        maxPrice = visibleData.get(0).high();
        for (AbstractChartData d : visibleData) {
            if (d.low().compareTo(minPrice) < 0) minPrice = d.low();
            if (d.high().compareTo(maxPrice) > 0) maxPrice = d.high();
        }
    }

    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp;
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
    
    private List<? extends AbstractChartData> getAllChartableCandles() {
        List<AbstractChartData> all = new ArrayList<>(chartData);
        if (currentlyFormingCandle != null) all.add(currentlyFormingCandle);
        return all;
    }
    
    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (htfCache.containsKey(targetTimeframe)) return htfCache.get(targetTimeframe);
        if (currentMode == ChartMode.LIVE) {
            List<KLine> directHtf = fetchDirectTfData(targetTimeframe, 50);
            if (!directHtf.isEmpty()) { htfCache.put(targetTimeframe, directHtf); return directHtf; }
        }
        if (baseDataWindow == null || baseDataWindow.isEmpty()) return Collections.emptyList();
        return DataResampler.resample(this.baseDataWindow, targetTimeframe);
    }

    private List<KLine> fetchDirectTfData(Timeframe target, int barsBack) {
        if (liveDataProvider == null || currentSource == null) return Collections.emptyList();
        return liveDataProvider.getHistoricalData(currentSource.symbol(), target.displayName(), barsBack);
    }
    
    public ChartMode getCurrentMode() { return currentMode; }
    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    public List<? extends AbstractChartData> getVisibleData() { return visibleData; }
    public List<KLine> getVisibleKLines() {
        if (visibleData == null || visibleData.isEmpty() || !(visibleData.get(0) instanceof KLine)) return Collections.emptyList();
        return (List<KLine>) visibleData;
    }
    public DataSourceManager.ChartDataSource getCurrentSymbol() { return this.currentSource; }
    public Timeframe getCurrentDisplayTimeframe() { return this.currentDisplayTimeframe; }
    public int getTotalCandleCount() { return totalCandleCount; }
    public KLine getCurrentReplayKLine() { return (currentMode == ChartMode.REPLAY) ? ReplaySessionManager.getInstance().getCurrentBar() : currentlyFormingCandle; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public boolean isInReplayMode() { return currentMode == ChartMode.REPLAY; }
}