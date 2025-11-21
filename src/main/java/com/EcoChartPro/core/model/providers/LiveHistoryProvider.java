package com.EcoChartPro.core.model.providers;

import com.EcoChartPro.core.model.calculators.FootprintCalculator;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages state, data fetching, and resampling for a chart in Live mode.
 * <p>
 * UPDATES:
 * - Added missing 'isFetchingHistory' field.
 * - Fixed Gap Filling logic.
 * - Zero Loss Init implementation.
 */
public class LiveHistoryProvider implements IHistoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveHistoryProvider.class);
    
    private static final int MAX_BUFFER_SIZE = 5000; 
    private static final int AUTO_SAVE_INTERVAL_MS = 60_000;
    private static final long CACHE_STALE_THRESHOLD_MS = 12 * 60 * 60 * 1000; 

    private final ChartPanel chartPanel;
    private final DataSourceManager.ChartDataSource source;
    private final DataProvider dataProvider;
    private final FootprintCalculator footprintCalculator;
    private final boolean isFootprintMode;
    private final DatabaseManager dbManager;

    private Timeframe targetTimeframe;
    private final Timeframe baseTimeframe = Timeframe.M1;

    private final List<KLine> formingBuffer = new ArrayList<>();
    private final List<KLine> pendingSaveBuffer = Collections.synchronizedList(new ArrayList<>());
    private List<KLine> finalizedCandles = new ArrayList<>();
    private KLine currentlyFormingCandle;

    // Initialization & Loading State
    private volatile boolean isInitializing = false;
    private volatile boolean isFetchingHistory = false; // [FIX] Added missing field
    private final List<KLine> liveTickBuffer = Collections.synchronizedList(new ArrayList<>());

    private final Timer autoSaveTimer;
    private final Consumer<KLine> liveKLineConsumer;
    private final Consumer<TradeTick> liveTradeConsumer;

    public LiveHistoryProvider(ChartPanel chartPanel, DataSourceManager.ChartDataSource source,
            DataProvider dataProvider, Timeframe initialTimeframe, boolean isFootprintMode,
            FootprintCalculator footprintCalculator, DatabaseManager dbManager) {
        this.chartPanel = chartPanel;
        this.source = source;
        this.dataProvider = dataProvider;
        this.isFootprintMode = isFootprintMode;
        this.footprintCalculator = footprintCalculator;
        this.dbManager = dbManager;

        this.liveKLineConsumer = isFootprintMode ? this::onLiveFootprintKLineUpdate : this::onLiveBaseKLineUpdate;
        this.liveTradeConsumer = isFootprintMode ? this::onLiveTradeUpdate : null;

        if (this.dbManager != null) {
            this.autoSaveTimer = new Timer(AUTO_SAVE_INTERVAL_MS, e -> savePendingData());
            this.autoSaveTimer.setRepeats(true);
            this.autoSaveTimer.start();
        } else {
            this.autoSaveTimer = null;
        }

        setTimeframe(initialTimeframe, true);
    }

    @Override
    public List<KLine> getFinalizedCandles() { return finalizedCandles; }
    @Override
    public KLine getFormingCandle() { return currentlyFormingCandle; }
    @Override
    public int getTotalCandleCount() { return finalizedCandles.size() + (currentlyFormingCandle != null ? 1 : 0); }
    @Override
    public int getDataWindowStartIndex() { return 0; }

    @Override
    public void setTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && newTimeframe.equals(this.targetTimeframe)) return;

        cleanupSubscriptions();
        this.targetTimeframe = newTimeframe;
        
        logger.info("Timeframe set to {}. Bridging with Base: {}", targetTimeframe, baseTimeframe);

        this.formingBuffer.clear();
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        if (isFootprintMode) this.footprintCalculator.clear();

        loadInitialHistory();
    }

    @Override
    public void cleanup() {
        if (autoSaveTimer != null && autoSaveTimer.isRunning()) {
            autoSaveTimer.stop();
            savePendingData();
        }
        cleanupSubscriptions();
    }

    private void onLiveBaseKLineUpdate(KLine incomingM1Tick) {
        if (isInitializing) {
            liveTickBuffer.add(incomingM1Tick);
        } else {
            SwingUtilities.invokeLater(() -> {
                processNewM1Tick(incomingM1Tick);
                chartPanel.getDataModel().fireLiveTickReceived(currentlyFormingCandle);
                chartPanel.getDataModel().fireDataUpdated();
            });
        }
    }

    private void onLiveFootprintKLineUpdate(KLine incomingM1Tick) {
        if (isInitializing) {
            liveTickBuffer.add(incomingM1Tick);
        } else {
            SwingUtilities.invokeLater(() -> {
                processNewM1Tick(incomingM1Tick);
                if (currentlyFormingCandle != null) {
                    footprintCalculator.addLiveTrade(
                            new TradeTick(Instant.now(), incomingM1Tick.close(), incomingM1Tick.volume(), 
                            incomingM1Tick.close().compareTo(incomingM1Tick.open()) >= 0 ? "buy" : "sell"),
                            currentlyFormingCandle);
                }
                chartPanel.getDataModel().fireDataUpdated();
            });
        }
    }

    private void onLiveTradeUpdate(TradeTick newTrade) {
        if (!isInitializing) {
            SwingUtilities.invokeLater(() -> {
                if (currentlyFormingCandle == null) return;
                footprintCalculator.addLiveTrade(newTrade, currentlyFormingCandle);
                chartPanel.getDataModel().fireDataUpdated();
            });
        }
    }

    private void processNewM1Tick(KLine tick) {
        if (formingBuffer.isEmpty()) {
            formingBuffer.add(tick);
        } else {
            KLine lastTick = formingBuffer.get(formingBuffer.size() - 1);
            if (tick.timestamp().equals(lastTick.timestamp())) {
                formingBuffer.set(formingBuffer.size() - 1, tick);
            } else {
                formingBuffer.add(tick);
            }
        }
        
        synchronized(pendingSaveBuffer) {
            if (pendingSaveBuffer.isEmpty()) {
                pendingSaveBuffer.add(tick);
            } else {
                KLine lastSaved = pendingSaveBuffer.get(pendingSaveBuffer.size() - 1);
                if (tick.timestamp().equals(lastSaved.timestamp())) {
                    pendingSaveBuffer.set(pendingSaveBuffer.size() - 1, tick);
                } else {
                    pendingSaveBuffer.add(tick);
                }
            }
        }

        List<KLine> resampled = DataResampler.resample(formingBuffer, targetTimeframe);

        if (resampled.isEmpty()) return;
        
        if (resampled.size() > 1) {
            List<KLine> newlyFinalized = resampled.subList(0, resampled.size() - 1);
            finalizedCandles.addAll(newlyFinalized);
            
            for(KLine k : newlyFinalized) {
                chartPanel.getDataModel().fireLiveCandleAdded(k);
            }

            KLine newForming = resampled.get(resampled.size() - 1);
            formingBuffer.removeIf(m1 -> m1.timestamp().isBefore(newForming.timestamp()));
            
            currentlyFormingCandle = newForming;
        } else {
            currentlyFormingCandle = resampled.get(0);
        }
        
        if (formingBuffer.size() > MAX_BUFFER_SIZE) {
            formingBuffer.subList(0, formingBuffer.size() - 1000).clear();
        }
    }
    
    private void savePendingData() {
        if (dbManager == null) return;

        final List<KLine> batchToSave;
        synchronized(pendingSaveBuffer) {
            if (pendingSaveBuffer.isEmpty()) return;
            batchToSave = new ArrayList<>(pendingSaveBuffer);
            pendingSaveBuffer.clear();
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    dbManager.saveKLines(batchToSave, new Symbol(source.symbol()), "1m");
                    logger.debug("Auto-saved {} live 1m candles to database.", batchToSave.size());
                } catch (Exception e) {
                    logger.error("Failed to auto-save live data for {}", source.symbol(), e);
                }
                return null;
            }
        }.execute();
    }

    private void loadInitialHistory() {
        if (chartPanel != null)
            chartPanel.setLoading(true, "Loading " + targetTimeframe.displayName() + "...");

        this.isInitializing = true;
        this.liveTickBuffer.clear();
        LiveDataManager.getInstance().subscribeToKLine(source.symbol(), baseTimeframe.displayName(), liveKLineConsumer);
        if (isFootprintMode)
            LiveDataManager.getInstance().subscribeToTrades(source.symbol(), liveTradeConsumer);

        new SwingWorker<Void, Void>() {
            private List<KLine> historyData = new ArrayList<>();
            private List<KLine> gapData = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                long targetDuration = targetTimeframe.duration().toMinutes();
                int requiredTargetBars = 1000;
                long requiredM1Bars = requiredTargetBars * targetDuration;
                
                List<KLine> localM1Data = Collections.emptyList();
                if (dbManager != null) {
                    Optional<DatabaseManager.DataRange> range = dbManager.getDataRange(new Symbol(source.symbol()), "1m");
                    if (range.isPresent()) {
                        localM1Data = dbManager.getKLinesByIndex(new Symbol(source.symbol()), "1m", 
                                Math.max(0, dbManager.getTotalKLineCount(new Symbol(source.symbol()), "1m") - (int)requiredM1Bars), 
                                (int)requiredM1Bars);
                    }
                }

                if (!localM1Data.isEmpty()) {
                    long lastTimestamp = localM1Data.get(localM1Data.size() - 1).timestamp().toEpochMilli();
                    if (System.currentTimeMillis() - lastTimestamp > CACHE_STALE_THRESHOLD_MS) {
                        logger.info("Cache stale. Forcing full API fetch.");
                        localM1Data = Collections.emptyList(); 
                    }
                }

                long cachedTargetBars = localM1Data.size() / Math.max(1, targetDuration);
                if (!localM1Data.isEmpty() && cachedTargetBars < 50) {
                    logger.info("Cache insufficient ({} bars). Forcing full API fetch.", cachedTargetBars);
                    localM1Data = Collections.emptyList(); 
                }

                long startTimeForGap = 0;

                if (localM1Data.isEmpty()) {
                    Timeframe fetchTimeframe = Timeframe.getSmartBaseTimeframe(targetTimeframe);
                    boolean requiresResampling = !fetchTimeframe.equals(targetTimeframe);
                    int fetchLimit = 1000;

                    List<KLine> rawFetched = dataProvider.getHistoricalData(source.symbol(), fetchTimeframe.displayName(), fetchLimit);
                    
                    if (rawFetched != null && !rawFetched.isEmpty()) {
                        List<KLine> resampled;
                        if (requiresResampling) {
                            resampled = DataResampler.resample(rawFetched, targetTimeframe);
                        } else {
                            resampled = new ArrayList<>(rawFetched);
                        }
                        
                        if (!resampled.isEmpty()) {
                            KLine last = resampled.remove(resampled.size()-1);
                            startTimeForGap = last.timestamp().toEpochMilli();
                        }
                        
                        historyData = resampled;
                    }
                } else {
                    List<KLine> resampled = DataResampler.resample(localM1Data, targetTimeframe);
                    
                    if (!resampled.isEmpty()) {
                        KLine last = resampled.remove(resampled.size()-1);
                        startTimeForGap = last.timestamp().toEpochMilli(); 
                    } else {
                        startTimeForGap = System.currentTimeMillis() - (60000 * 1000); 
                    }
                    
                    historyData = resampled;
                }

                if (startTimeForGap > 0 && (System.currentTimeMillis() - startTimeForGap > 0)) {
                    logger.info("Filling gap for {} from {}", source.symbol(), Instant.ofEpochMilli(startTimeForGap));
                    if (dataProvider instanceof BinanceProvider bp) {
                        gapData = bp.backfillHistoricalData(source.symbol(), "1m", startTimeForGap);
                    } else if (dataProvider instanceof OkxProvider op) {
                        gapData = op.backfillHistoricalDataForward(source.symbol(), "1m", startTimeForGap);
                    } else {
                        gapData = dataProvider.getHistoricalData(source.symbol(), "1m", 1000);
                    }
                }
                
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (!historyData.isEmpty()) {
                        finalizedCandles.addAll(historyData);
                    }

                    if (gapData != null) {
                        for(KLine k : gapData) {
                            processNewM1Tick(k);
                        }
                    }

                    synchronized(liveTickBuffer) {
                        for(KLine k : liveTickBuffer) {
                            processNewM1Tick(k);
                        }
                        liveTickBuffer.clear();
                        isInitializing = false; 
                    }

                    if (isFootprintMode) {
                        List<KLine> all = new ArrayList<>(finalizedCandles);
                        if (currentlyFormingCandle != null) all.add(currentlyFormingCandle);
                        footprintCalculator.calculateHistoricalFootprints(all);
                    }

                    int dataBarsOnScreen = (int) (chartPanel.getDataModel().getInteractionManager().getBarsPerScreen()
                            * (1.0 - chartPanel.getDataModel().getInteractionManager().getRightMarginRatio()));
                    int initialStartIndex = Math.max(0, finalizedCandles.size() - dataBarsOnScreen);
                    chartPanel.getDataModel().getInteractionManager().setStartIndex(initialStartIndex);

                    chartPanel.getDataModel().fireDataUpdated();
                } catch (Exception e) {
                    logger.error("Failed to load hybrid history for {}", targetTimeframe, e);
                } finally {
                    if (chartPanel != null)
                        chartPanel.setLoading(false, null);
                }
            }
        }.execute();
    }
    
    public void fetchOlderHistoryAsync(long endTimeForFetch, int limit) {
        if (isFetchingHistory) return;
        isFetchingHistory = true;
        if (chartPanel != null) chartPanel.setLoading(true, "Loading older history...");

        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() throws Exception {
                Timeframe fetchTimeframe = Timeframe.getSmartBaseTimeframe(targetTimeframe);
                boolean requiresResampling = !fetchTimeframe.equals(targetTimeframe);
                int fetchLimit = 1000;

                List<KLine> rawFetched;
                if (dataProvider instanceof BinanceProvider bp) {
                    rawFetched = bp.getHistoricalData(source.symbol(), fetchTimeframe.displayName(), fetchLimit, null, endTimeForFetch);
                } else if (dataProvider instanceof OkxProvider op) {
                    rawFetched = op.getHistoricalData(source.symbol(), fetchTimeframe.displayName(), fetchLimit, null, endTimeForFetch);
                } else {
                    return Collections.emptyList();
                }

                if (requiresResampling && rawFetched != null) {
                    return DataResampler.resample(rawFetched, targetTimeframe);
                }
                return rawFetched;
            }

            @Override
            protected void done() {
                try {
                    List<KLine> olderData = get();
                    if (olderData != null && !olderData.isEmpty()) {
                        if (!finalizedCandles.isEmpty() && !olderData.isEmpty()) {
                             KLine firstCurrent = finalizedCandles.get(0);
                             KLine lastOlder = olderData.get(olderData.size()-1);
                             if (lastOlder.timestamp().equals(firstCurrent.timestamp())) {
                                 olderData.remove(olderData.size()-1);
                             }
                        }
                        finalizedCandles.addAll(0, olderData);
                        if (isFootprintMode) {
                            List<KLine> all = new ArrayList<>(finalizedCandles);
                            if (currentlyFormingCandle != null) all.add(currentlyFormingCandle);
                            footprintCalculator.calculateHistoricalFootprints(all);
                        }
                        chartPanel.getDataModel().getInteractionManager().setStartIndex(
                                chartPanel.getDataModel().getInteractionManager().getStartIndex() + olderData.size());
                    }
                } catch (Exception e) {
                    logger.error("Failed to fetch older live data.", e);
                } finally {
                    isFetchingHistory = false;
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                    chartPanel.getDataModel().fireDataUpdated();
                }
            }
        }.execute();
    }

    private void cleanupSubscriptions() {
        if (source != null) {
            if (liveKLineConsumer != null)
                LiveDataManager.getInstance().unsubscribeFromKLine(source.symbol(), baseTimeframe.displayName(),
                        liveKLineConsumer);
            if (liveTradeConsumer != null)
                LiveDataManager.getInstance().unsubscribeFromTrades(source.symbol(), liveTradeConsumer);
        }
    }
}