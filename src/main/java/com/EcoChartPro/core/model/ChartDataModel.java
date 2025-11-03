package com.EcoChartPro.core.model;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.ReplayStateListener;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.core.indicator.IndicatorManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.data.DataTransformer;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.chart.FootprintBar;
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
import java.math.RoundingMode;
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
    private static final int LIVE_PAN_TRIGGER_THRESHOLD = 500; // Bars remaining before triggering history fetch

    private List<KLine> visibleKLines;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager;
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private List<KLine> finalizedCandles;
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
    private Consumer<TradeTick> liveTradeConsumer;
    private Consumer<KLine> liveDataConsumer;
    private ChartInteractionManager interactionManager;
    private final Map<Instant, FootprintBar> footprintData;

    private volatile boolean isFetchingLiveHistory = false;
    private BigDecimal lastCalculatedFootprintStep = new BigDecimal("0.05");

    // Heikin Ashi Caching
    private List<KLine> heikinAshiCandlesCache;
    private boolean isHaCacheDirty = true;

    private record RebuildResult(List<KLine> resampledCandles, KLine formingCandle, int newWindowStart, List<KLine> rawM1Slice) {}

    public ChartDataModel() {
        this.visibleKLines = new ArrayList<>();
        this.footprintData = new ConcurrentHashMap<>();
        this.finalizedCandles = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.baseDataWindow = new ArrayList<>();
        DrawingManager.getInstance().addPropertyChangeListener("activeSymbolChanged", this);
        com.EcoChartPro.core.settings.SettingsManager.getInstance().addPropertyChangeListener("chartTypeChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("activeSymbolChanged".equals(evt.getPropertyName())) {
            fireDataUpdated();
        } else if ("viewStateChanged".equals(evt.getPropertyName())) {
            updateView();
        } else if ("chartTypeChanged".equals(evt.getPropertyName())) {
            ChartType newType = (ChartType) evt.getNewValue();
            ChartType oldType = (ChartType) evt.getOldValue();
            if ((newType == ChartType.FOOTPRINT && oldType != ChartType.FOOTPRINT) ||
                (newType != ChartType.FOOTPRINT && oldType == ChartType.FOOTPRINT)) {
                logger.info("Chart type changed to/from Footprint. Triggering data reload.");
                loadDataset(currentSource, currentDisplayTimeframe, true);
            }
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
        com.EcoChartPro.core.settings.SettingsManager.getInstance().removePropertyChangeListener("chartTypeChanged", this);
        if (currentMode == ChartMode.LIVE && liveDataProvider != null && liveDataConsumer != null && currentSource != null && currentDisplayTimeframe != null) {
            logger.info("Cleaning up live data subscription for chart model on cleanup.");
            LiveDataManager.getInstance().unsubscribeFromKLine(currentSource.symbol(), currentDisplayTimeframe.displayName(), liveDataConsumer);
        }
        if (currentMode == ChartMode.LIVE && liveDataProvider != null && liveTradeConsumer != null && currentSource != null) {
            LiveDataManager.getInstance().unsubscribeFromTrades(currentSource.symbol(), liveTradeConsumer);
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

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }
    
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }
    
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void fireDataUpdated() {
        pcs.firePropertyChange("dataUpdated", null, null);
    }
    
    public void setDatabaseManager(DatabaseManager dbManager, DataSourceManager.ChartDataSource source) {
        this.dbManager = dbManager;
        // REMOVED: this.currentSource = source;
        // This line caused a premature state update, breaking symbol-change detection in `loadDataset`.
        // The currentSource should only be updated by `loadDataset` or `configureForReplay`.
    }

    public DatabaseManager getDbManager() {
        return this.dbManager;
    }

    private void updateView() {
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            return;
        }
        if (currentMode == ChartMode.REPLAY) {
            if (handleDataWindowLoading()) return;
        } else if (currentMode == ChartMode.LIVE) {
            checkForLivePanBack();
        }
        assembleVisibleKLines();
        calculateBoundaries();
        triggerIndicatorRecalculation();
        fireDataUpdated();
        if (currentMode == ChartMode.REPLAY) checkForPreFetchTrigger();
    }
    
    private void checkForLivePanBack() {
        if (isFetchingLiveHistory || finalizedCandles.isEmpty()) return;

        if (interactionManager.getStartIndex() < LIVE_PAN_TRIGGER_THRESHOLD) {
            if (com.EcoChartPro.core.settings.SettingsManager.getInstance().getCurrentChartType() == ChartType.FOOTPRINT) {
                int maxBars = getMaxHistoricalBars();
                if (finalizedCandles.size() >= maxBars) return;
            }
            
            fetchOlderLiveDataAsync();
        }
    }

    private void fetchOlderLiveDataAsync() {
        isFetchingLiveHistory = true;
        KLine oldestCandle = finalizedCandles.get(0);
        long endTimeForFetch = oldestCandle.timestamp().toEpochMilli() - 1;
        int limit = 1000;

        if (chartPanel != null) chartPanel.setLoading(true, "Loading older history...");

        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() throws Exception {
                if (liveDataProvider instanceof BinanceProvider bp) {
                    return bp.getHistoricalData(currentSource.symbol(), currentDisplayTimeframe.displayName(), limit, null, endTimeForFetch);
                } else if (liveDataProvider instanceof OkxProvider op) {
                    return op.getHistoricalData(currentSource.symbol(), currentDisplayTimeframe.displayName(), limit, null, oldestCandle.timestamp().toEpochMilli());
                }
                return Collections.emptyList();
            }

            @Override
            protected void done() {
                try {
                    List<KLine> olderData = get();
                    if (olderData != null && !olderData.isEmpty()) {
                        finalizedCandles.addAll(0, olderData);
                        interactionManager.setStartIndex(interactionManager.getStartIndex() + olderData.size());
                        logger.info("Fetched and prepended {} older candles in Live mode.", olderData.size());
                    }
                } catch (Exception e) {
                    logger.error("Failed to fetch older live data.", e);
                } finally {
                    isFetchingLiveHistory = false;
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                    updateView();
                }
            }
        }.execute();
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
            if (com.EcoChartPro.core.settings.SettingsManager.getInstance().getCurrentChartType() == ChartType.FOOTPRINT) {
                boolean isPanningLeft = currentStartIndex < dataWindowStartIndex + DATA_WINDOW_TRIGGER_BUFFER && dataWindowStartIndex > 0;
                if (isPanningLeft) {
                    int maxBars = getMaxHistoricalBars();
                    List<KLine> currentData = (currentDisplayTimeframe == Timeframe.M1) ? baseDataWindow : finalizedCandles;
                    if (currentData != null && currentData.size() >= maxBars) {
                        logger.info("Footprint mode: Deep panning disabled. Max history limit reached ({} bars).", maxBars);
                        return false;
                    }
                }
            }

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

        if (currentMode == ChartMode.REPLAY) {
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
        } else { // LIVE Mode
            List<KLine> sourceData = getAllChartableCandles();
            if (sourceData.isEmpty()) return Collections.emptyList();
            int calculationEndIndex = Math.min(currentStartIndex + currentBarsPerScreen, sourceData.size());
            int calculationStartIndex = Math.max(0, currentStartIndex - INDICATOR_LOOKBACK_BUFFER);
            return (calculationStartIndex < calculationEndIndex) ? sourceData.subList(calculationStartIndex, calculationEndIndex) : Collections.emptyList();
        }
    }

    public void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        loadDataset(source, timeframe, false);
    }
    
    private void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe, boolean forceReload) {
        DataSourceManager.ChartDataSource oldSource = this.currentSource;
        Timeframe oldTimeframe = this.currentDisplayTimeframe;

        boolean sourceChanged = (oldSource == null) || !oldSource.equals(source);
        boolean timeframeChanged = (oldTimeframe == null) || !oldTimeframe.equals(timeframe);

        if (!forceReload && !sourceChanged && !timeframeChanged) {
            logger.debug("Load dataset skipped: source and timeframe are unchanged.");
            return;
        }

        if (interactionManager != null) {
            interactionManager.setAutoScalingY(true);
        }
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) {
            activeRebuildWorker.cancel(true);
        }

        // Explicitly clear all data and cancel pre-fetches on symbol change for a clean state.
        if (sourceChanged) {
            logger.info("Symbol changed from {} to {}. Clearing all chart data.",
                oldSource != null ? oldSource.symbol() : "none", source.symbol());
            clearData();
            if (preFetchWorker != null && !preFetchWorker.isDone()) {
                preFetchWorker.cancel(true);
                logger.debug("Cancelled ongoing pre-fetch worker during symbol switch.");
            }
            this.preFetchedResult = null;
            this.isPreFetching = false;
        }

        // Unsubscribe from old live streams if source or timeframe has changed.
        if (currentMode == ChartMode.LIVE && oldSource != null && oldTimeframe != null && (sourceChanged || timeframeChanged)) {
            if (liveDataConsumer != null) {
                LiveDataManager.getInstance().unsubscribeFromKLine(oldSource.symbol(), oldTimeframe.displayName(), liveDataConsumer);
                logger.info("Unsubscribed from k-line stream for previous symbol: {} ({})", oldSource.symbol(), oldTimeframe.displayName());
                liveDataConsumer = null;
            }
            if (liveTradeConsumer != null) {
                LiveDataManager.getInstance().unsubscribeFromTrades(oldSource.symbol(), liveTradeConsumer);
                logger.info("Unsubscribed from trade stream for previous symbol: {}", oldSource.symbol());
                liveTradeConsumer = null;
            }
        }
        
        // Set new state
        this.currentSource = source;
        this.currentMode = ChartMode.LIVE;
        this.currentDisplayTimeframe = timeframe;

        if (source.providerName() != null) {
            switch (source.providerName()) {
                case "Binance": this.liveDataProvider = new BinanceProvider(); break;
                case "OKX": this.liveDataProvider = new OkxProvider(); break;
                default:
                    logger.warn("No live data provider implementation for '{}'", source.providerName());
                    this.liveDataProvider = null;
            }
        } else {
            this.liveDataProvider = null;
        }
        
        if (timeframe == null) return;
        
        if (com.EcoChartPro.core.settings.SettingsManager.getInstance().getCurrentChartType() == ChartType.FOOTPRINT) {
            loadFootprintData(source, timeframe);
        } else {
            setDisplayTimeframe(timeframe, true);
        }
    }

    private void loadFootprintData(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        if (chartPanel != null) chartPanel.setLoading(true, "Loading Footprint Data for " + timeframe.displayName() + "...");
        this.currentDisplayTimeframe = timeframe;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<KLine> klineHistory = fetchDirectTfData(timeframe, getMaxHistoricalBars());
                if (klineHistory.isEmpty()) {
                    return null;
                }

                BigDecimal dynamicPriceStep;
                if (klineHistory.size() > 1) {
                    BigDecimal totalRange = BigDecimal.ZERO;
                    int count = 0;
                    for (KLine k : klineHistory) {
                        BigDecimal range = k.high().subtract(k.low());
                        if (range.compareTo(BigDecimal.ZERO) > 0) {
                            totalRange = totalRange.add(range);
                            count++;
                        }
                    }
                    BigDecimal averageRange = (count > 0) ? totalRange.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                    dynamicPriceStep = averageRange.divide(BigDecimal.valueOf(15), 8, RoundingMode.HALF_UP);

                    BigDecimal lastPrice = klineHistory.get(klineHistory.size() - 1).close();
                    if (lastPrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal minReasonableStep = lastPrice.multiply(new BigDecimal("0.00001"));
                        if (dynamicPriceStep.compareTo(minReasonableStep) < 0) {
                            dynamicPriceStep = minReasonableStep;
                        }
                    }
                } else {
                    dynamicPriceStep = new BigDecimal("0.05");
                }

                if (dynamicPriceStep.compareTo(BigDecimal.ZERO) <= 0) {
                    dynamicPriceStep = new BigDecimal("0.00001");
                }
                lastCalculatedFootprintStep = dynamicPriceStep;

                finalizedCandles = new ArrayList<>(klineHistory);
                footprintData.clear();

                for (KLine kline : klineHistory) {
                    FootprintBar fpBar = new FootprintBar(kline.timestamp());
                    fpBar.approximateFromKline(kline, lastCalculatedFootprintStep);
                    footprintData.put(kline.timestamp(), fpBar);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (liveDataProvider != null) {
                        liveTradeConsumer = ChartDataModel.this::onLiveTradeUpdate;
                        LiveDataManager.getInstance().subscribeToTrades(currentSource.symbol(), liveTradeConsumer);

                        liveDataConsumer = ChartDataModel.this::onLiveFootprintKLineUpdate;
                        LiveDataManager.getInstance().subscribeToKLine(currentSource.symbol(), timeframe.displayName(), liveDataConsumer);
                    }
                    if (interactionManager != null && !finalizedCandles.isEmpty()) {
                         int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
                         interactionManager.setStartIndex(Math.max(0, finalizedCandles.size() - dataBarsOnScreen));
                    }
                    updateView();
                } catch (Exception e) {
                    logger.error("Failed to load or approximate footprint data.", e);
                } finally {
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                }
            }
        }.execute();
    }

    private void onLiveKLineUpdate(KLine newTick) {
        SwingUtilities.invokeLater(() -> {
            if (currentMode != ChartMode.LIVE) return;
    
            Instant intervalStart = getIntervalStart(newTick.timestamp(), currentDisplayTimeframe);
    
            if (currentlyFormingCandle == null) {
                currentlyFormingCandle = newTick;
            } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
                KLine finalizedCandle = currentlyFormingCandle;
                finalizedCandles.add(finalizedCandle);
                pcs.firePropertyChange("liveCandleAdded", null, finalizedCandle); // Fire event for new candle
                currentlyFormingCandle = newTick;
                htfCache.clear();
            } else {
                currentlyFormingCandle = new KLine(
                        currentlyFormingCandle.timestamp(),
                        currentlyFormingCandle.open(),
                        currentlyFormingCandle.high().max(newTick.high()),
                        currentlyFormingCandle.low().min(newTick.low()),
                        newTick.close(),
                        currentlyFormingCandle.volume().add(newTick.volume())
                );
            }
            
            pcs.firePropertyChange("liveTickReceived", null, currentlyFormingCandle); // Fire tick event
            
            isHaCacheDirty = true;
            interactionManager.onReplayTick(newTick);
            triggerIndicatorRecalculation();
            updateView();
        });
    }

    private void onLiveFootprintKLineUpdate(KLine newTick) {
        SwingUtilities.invokeLater(() -> {
            if (currentMode != ChartMode.LIVE) return;
    
            Instant intervalStart = getIntervalStart(newTick.timestamp(), currentDisplayTimeframe);
    
            if (currentlyFormingCandle == null || !currentlyFormingCandle.timestamp().equals(intervalStart)) {
                logger.debug("New footprint candle interval detected. Finalizing old candle, starting new.");
                
                if (currentlyFormingCandle != null) {
                    KLine finalizedCandle = currentlyFormingCandle;
                    finalizedCandles.add(finalizedCandle);
                    pcs.firePropertyChange("liveCandleAdded", null, finalizedCandle); // Fire event for new candle
                }
                
                currentlyFormingCandle = newTick;

                footprintData.computeIfAbsent(currentlyFormingCandle.timestamp(), ts -> {
                    FootprintBar newBar = new FootprintBar(ts);
                    newBar.setPriceStep(this.lastCalculatedFootprintStep);
                    return newBar;
                });
                
                interactionManager.onReplayTick(newTick);
                updateView();
            }
        });
    }

    private void onLiveTradeUpdate(TradeTick newTrade) {
        SwingUtilities.invokeLater(() -> {
            if (currentMode != ChartMode.LIVE) return;
            if (currentlyFormingCandle == null) return;
    
            Instant candleTimestamp = currentlyFormingCandle.timestamp();

            FootprintBar currentFpBar = footprintData.computeIfAbsent(candleTimestamp, ts -> {
                FootprintBar newBar = new FootprintBar(ts);
                newBar.setPriceStep(this.lastCalculatedFootprintStep);
                return newBar;
            });
            currentFpBar.addTrade(newTrade);
    
            currentlyFormingCandle = new KLine(
                currentlyFormingCandle.timestamp(),
                currentlyFormingCandle.open(),
                currentlyFormingCandle.high().max(newTrade.price()),
                currentlyFormingCandle.low().min(newTrade.price()),
                newTrade.price(), 
                currentlyFormingCandle.volume().add(newTrade.quantity())
            );
            
            pcs.firePropertyChange("liveTickReceived", null, currentlyFormingCandle);
            
            isHaCacheDirty = true;
            updateView();
        });
    }

    @Override
    public void onReplayTick(KLine newBar) {
        if (currentMode != ChartMode.REPLAY) return;
        if (baseDataWindow != null) baseDataWindow.add(newBar);
        if (currentDisplayTimeframe == Timeframe.M1) totalCandleCount++;
        else processNewM1Bar(newBar);
        
        isHaCacheDirty = true;
        assembleVisibleKLines();
        calculateBoundaries();
        triggerIndicatorRecalculation();
        fireDataUpdated();
    }
    
    @Override
    public void onReplaySessionStart() {
        if (currentMode != ChartMode.REPLAY) return;
        setDisplayTimeframe(this.currentDisplayTimeframe, true);
    }
    
    @Override
    public void onReplayStateChanged() {}

    public void setDisplayTimeframe(Timeframe newTimeframe) {
        setDisplayTimeframe(newTimeframe, false);
    }

    public void setDisplayTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && this.currentDisplayTimeframe != null && this.currentDisplayTimeframe.equals(newTimeframe)) return;
        
        if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
        htfCache.clear();
        
        if (currentMode == ChartMode.LIVE) {
            // Unsubscribe from the OLD timeframe stream
            if (liveDataConsumer != null && currentSource != null && this.currentDisplayTimeframe != null) {
                LiveDataManager.getInstance().unsubscribeFromKLine(currentSource.symbol(), this.currentDisplayTimeframe.displayName(), liveDataConsumer);
            }
            this.currentDisplayTimeframe = newTimeframe;
            loadLiveHistoryForCurrentTimeframe();
        } else { // REPLAY Mode
            resetDataForTimeframeSwitch();
            interactionManager.setAutoScalingY(true);
            this.currentDisplayTimeframe = newTimeframe;
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int m1HeadIndex = manager.getReplayHeadIndex();
            if (m1HeadIndex < 0) { resetDataForTimeframeSwitch(); return; }

            if (currentDisplayTimeframe != Timeframe.M1 && !currentDisplayTimeframe.duration().isZero()) {
                totalCandleCount = (m1HeadIndex + 1) / (int)currentDisplayTimeframe.duration().toMinutes();
            } else {
                totalCandleCount = m1HeadIndex + 1;
            }
            int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
            int targetStartIndex = Math.max(0, totalCandleCount - dataBarsOnScreen);
            rebuildHistoryAsync(targetStartIndex, false);
        }
    }

    private void loadLiveHistoryForCurrentTimeframe() {
        if (chartPanel != null) chartPanel.setLoading(true, "Loading " + currentDisplayTimeframe.displayName() + " history...");

        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() {
                int limit = (com.EcoChartPro.core.settings.SettingsManager.getInstance().getCurrentChartType() == ChartType.FOOTPRINT)
                            ? getMaxHistoricalBars()
                            : 1000;
                return fetchDirectTfData(currentDisplayTimeframe, limit);
            }
            @Override
            protected void done() {
                try {
                    List<KLine> initialData = get();
                    if (initialData != null && !initialData.isEmpty()) {
                        finalizedCandles = new ArrayList<>(initialData.subList(0, initialData.size() - 1));
                        currentlyFormingCandle = initialData.get(initialData.size() - 1);
                    } else {
                        finalizedCandles = new ArrayList<>();
                        currentlyFormingCandle = null;
                    }

                    isHaCacheDirty = true;
                    if (liveDataProvider != null) {
                        liveDataConsumer = ChartDataModel.this::onLiveKLineUpdate;
                        LiveDataManager.getInstance().subscribeToKLine(currentSource.symbol(), currentDisplayTimeframe.displayName(), liveDataConsumer);
                    }

                    int dataBarsOnScreen = (int) (interactionManager.getBarsPerScreen() * (1.0 - interactionManager.getRightMarginRatio()));
                    int initialStartIndex = Math.max(0, finalizedCandles.size() - dataBarsOnScreen);
                    interactionManager.setStartIndex(initialStartIndex);
                    
                    triggerIndicatorRecalculation();
                    updateView();
                } catch (Exception e) {
                    logger.error("Failed to load live history for timeframe {}", currentDisplayTimeframe, e);
                } finally {
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                }
            }
        }.execute();
    }

    public void clearData() {
        this.visibleKLines.clear();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        this.baseDataWindow.clear();
        this.indicatorManager.resetAllIndicators();
        this.totalCandleCount = 0;
        this.dataWindowStartIndex = 0;
        this.footprintData.clear();
        this.htfCache.clear();
        this.isHaCacheDirty = true;
        fireDataUpdated();
    }

    private void resetDataForTimeframeSwitch() {
        this.visibleKLines.clear();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        this.baseDataWindow.clear();
        this.totalCandleCount = 0;
        this.dataWindowStartIndex = 0;
        this.footprintData.clear();
        this.htfCache.clear();
        this.isHaCacheDirty = true;
        fireDataUpdated();
    }

    public void configureForReplay(Timeframe initialDisplayTimeframe, DataSourceManager.ChartDataSource source) {
        clearData();
        this.currentMode = ChartMode.REPLAY;
        this.currentDisplayTimeframe = initialDisplayTimeframe;
        this.currentSource = source;
    }
    
    private void rebuildHistoryAsync(final int targetStartIndex, boolean isPreFetch) {
        rebuildHistoryAsync(targetStartIndex, isPreFetch, null);
    }

    private void rebuildHistoryAsync(final int targetStartIndex, boolean isPreFetch, Runnable onCompleteCallback) {
        if (!isPreFetch) {
            if (activeRebuildWorker != null && !activeRebuildWorker.isDone()) activeRebuildWorker.cancel(true);
            if (chartPanel != null) chartPanel.setLoading(true, "Building " + currentDisplayTimeframe.displayName() + " history...");
        }

        SwingWorker<RebuildResult, Void> worker = new SwingWorker<>() {
            @Override
            protected RebuildResult doInBackground() throws Exception {
                int newWindowCenter = (interactionManager != null) ? targetStartIndex + (interactionManager.getBarsPerScreen() / 2) : targetStartIndex + 100;
                int newWindowStart = Math.max(0, newWindowCenter - (DATA_WINDOW_SIZE / 2));
                newWindowStart = Math.min(newWindowStart, Math.max(0, totalCandleCount - DATA_WINDOW_SIZE));
                return fetchReplayData(newWindowStart);
            }

            @Override
            protected void done() {
                try {
                    RebuildResult result = get();
                    if (isPreFetch) {
                        preFetchedResult = result; isPreFetching = false;
                        logger.debug("Pre-fetch complete for window starting at {}", result.newWindowStart());
                    } else {
                        applyRebuildResult(result, targetStartIndex);
                        triggerIndicatorRecalculation();
                        if (interactionManager != null) interactionManager.setStartIndex(targetStartIndex);
                        updateView();
                        if (onCompleteCallback != null) onCompleteCallback.run();
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

        if (isPreFetch) this.preFetchWorker = worker;
        else this.activeRebuildWorker = worker;
        worker.execute();
    }

    private void applyRebuildResult(RebuildResult result, int targetStartIndex) {
        if (currentDisplayTimeframe == Timeframe.M1 && currentMode == ChartMode.REPLAY) {
            baseDataWindow = result.rawM1Slice() != null ? result.rawM1Slice() : new ArrayList<>();
            finalizedCandles = new ArrayList<>();
        } else {
            finalizedCandles = result.resampledCandles() != null ? result.resampledCandles() : new ArrayList<>();
            baseDataWindow = result.rawM1Slice() != null ? result.rawM1Slice() : new ArrayList<>();
        }
        currentlyFormingCandle = result.formingCandle();
        dataWindowStartIndex = result.newWindowStart();
        isHaCacheDirty = true;
        
        if (currentMode == ChartMode.REPLAY) {
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
    
    private void checkForPreFetchTrigger() {
        if (isPreFetching || (activeRebuildWorker != null && !activeRebuildWorker.isDone())) return;
        if (currentMode == ChartMode.LIVE) return;

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

        if (currentMode == ChartMode.REPLAY && currentDisplayTimeframe == Timeframe.M1) {
            ReplaySessionManager manager = ReplaySessionManager.getInstance();
            int headIndex = manager.getReplayHeadIndex();
            if (headIndex < 0) { this.visibleKLines = Collections.emptyList(); return; }
            int fetchStart = currentStartIndex;
            int fetchCount = Math.min(currentBarsPerScreen, headIndex + 1 - fetchStart);
            this.visibleKLines = manager.getOneMinuteBars(fetchStart, fetchCount);
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
        if (visibleKLines.isEmpty()) { minPrice = BigDecimal.ZERO; maxPrice = BigDecimal.ONE; return; }
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
    
    public List<KLine> getAllChartableCandles() {
        if (currentMode == ChartMode.REPLAY && currentDisplayTimeframe == Timeframe.M1) {
            return baseDataWindow != null ? baseDataWindow : Collections.emptyList();
        }
        List<KLine> all = (finalizedCandles != null) ? new ArrayList<>(finalizedCandles) : new ArrayList<>();
        if (currentlyFormingCandle != null) all.add(currentlyFormingCandle);
        return all;
    }
    
    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (htfCache.containsKey(targetTimeframe)) {
            return htfCache.get(targetTimeframe);
        }

        if (currentMode == ChartMode.LIVE) {
            List<KLine> directHtf = fetchDirectTfData(targetTimeframe, 50);
            if (!directHtf.isEmpty()) {
                htfCache.put(targetTimeframe, directHtf);
                logger.info("Cached {} bars for HTF {}.", directHtf.size(), targetTimeframe.displayName());
                return directHtf;
            }
        }
        
        if (baseDataWindow == null || baseDataWindow.isEmpty()) {
            logger.warn("HTF data requested for {}, but base M1 data is not available for resampling.", targetTimeframe);
            return Collections.emptyList();
        }
        logger.debug("Resampling for HTF {}: Using base M1 data window of {} bars.", targetTimeframe, baseDataWindow.size());
        return DataResampler.resample(this.baseDataWindow, targetTimeframe);
    }

    private List<KLine> fetchDirectTfData(Timeframe target, int barsBack) {
        if (liveDataProvider == null || currentSource == null) return Collections.emptyList();
        String tfString = target.displayName();
        
        List<KLine> directData = Collections.emptyList();

        try {
            if (liveDataProvider instanceof BinanceProvider bp) {
                 directData = bp.getHistoricalData(currentSource.symbol(), tfString, barsBack, null, null);
            } else if (liveDataProvider instanceof OkxProvider op) {
                 directData = op.getHistoricalData(currentSource.symbol(), tfString, barsBack, null, null);
            }
        } catch (Exception e) {
            logger.error("Error fetching direct data from provider", e);
        }

        logger.info("Directly fetched {} bars for timeframe {}.", directData.size(), tfString);
        return directData;
    }
    
    private int getMaxHistoricalBars() {
        if (currentSource == null) return DATA_WINDOW_SIZE;
        String provider = currentSource.providerName();
        if ("Binance".equals(provider)) {
            return 1000;
        } else if ("OKX".equals(provider)) {
            return 300;
        }
        return DATA_WINDOW_SIZE; // Default no limit
    }

    public List<KLine> getHeikinAshiCandles() {
        if (!isHaCacheDirty && heikinAshiCandlesCache != null) {
            return heikinAshiCandlesCache;
        }
        List<KLine> allRaw = getAllChartableCandles();
        logger.debug("Recalculating Heikin Ashi cache for {} raw candles.", allRaw.size());
        heikinAshiCandlesCache = DataTransformer.transformToHeikinAshi(allRaw);
        isHaCacheDirty = false;
        return heikinAshiCandlesCache;
    }

    public ChartMode getCurrentMode() { return currentMode; }
    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    public List<KLine> getVisibleKLines() { return visibleKLines; }
    public Map<Instant, FootprintBar> getFootprintData() { return this.footprintData; }
    public DataSourceManager.ChartDataSource getCurrentSymbol() { return this.currentSource; }
    public Timeframe getCurrentDisplayTimeframe() { return this.currentDisplayTimeframe; }
    public int getTotalCandleCount() { return (currentMode == ChartMode.LIVE) ? (finalizedCandles != null ? finalizedCandles.size() : 0) : totalCandleCount; }
    public KLine getCurrentReplayKLine() { return (currentMode == ChartMode.REPLAY) ? ReplaySessionManager.getInstance().getCurrentBar() : currentlyFormingCandle; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public boolean isInReplayMode() { return currentMode == ChartMode.REPLAY; }
}