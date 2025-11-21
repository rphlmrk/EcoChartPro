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
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages state, data fetching, and resampling for a chart in Live mode.
 * <p>
 * PHASE 4 FEATURES:
 * - Live Data Persistence: Automatically buffers and flushes 1m socket data to the local database.
 */
public class LiveHistoryProvider implements IHistoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveHistoryProvider.class);
    
    private static final int MAX_BUFFER_SIZE = 5000; 
    private static final int AUTO_SAVE_INTERVAL_MS = 60_000; // Save live data every 1 minute

    // --- Dependencies ---
    private final ChartPanel chartPanel;
    private final DataSourceManager.ChartDataSource source;
    private final DataProvider dataProvider;
    private final FootprintCalculator footprintCalculator;
    private final boolean isFootprintMode;
    private final DatabaseManager dbManager; // For persistence

    // --- State ---
    private Timeframe targetTimeframe;
    private final Timeframe baseTimeframe = Timeframe.M1;

    // BUFFER: Holds 1m candles for the current forming Target candle (UI Logic)
    private final List<KLine> formingBuffer = new ArrayList<>();
    
    // PERSISTENCE BUFFER: Holds 1m candles waiting to be saved to DB (Storage Logic)
    // Synchronized because socket thread adds, timer thread reads/clears.
    private final List<KLine> pendingSaveBuffer = Collections.synchronizedList(new ArrayList<>());

    // VIEW: Finalized Target candles
    private List<KLine> finalizedCandles = new ArrayList<>();
    private KLine currentlyFormingCandle;

    private volatile boolean isFetchingHistory = false;
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

        // Start the auto-save timer if we have a DB connection
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
    public List<KLine> getFinalizedCandles() {
        return finalizedCandles;
    }

    @Override
    public KLine getFormingCandle() {
        return currentlyFormingCandle;
    }

    @Override
    public int getTotalCandleCount() {
        return finalizedCandles.size() + (currentlyFormingCandle != null ? 1 : 0);
    }

    @Override
    public int getDataWindowStartIndex() {
        return 0;
    }

    @Override
    public void setTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null)
            return;
        if (!forceReload && newTimeframe.equals(this.targetTimeframe))
            return;

        cleanupSubscriptions();

        this.targetTimeframe = newTimeframe;
        
        logger.info("Timeframe set to {}. Bridging with Base: {}", targetTimeframe, baseTimeframe);

        this.formingBuffer.clear();
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        if (isFootprintMode)
            this.footprintCalculator.clear();

        loadInitialHistory();
    }

    @Override
    public void cleanup() {
        if (autoSaveTimer != null && autoSaveTimer.isRunning()) {
            autoSaveTimer.stop();
            savePendingData(); // Final flush
        }
        cleanupSubscriptions();
    }

    private void onLiveBaseKLineUpdate(KLine incomingM1Tick) {
        SwingUtilities.invokeLater(() -> {
            processNewM1Tick(incomingM1Tick);
            chartPanel.getDataModel().fireLiveTickReceived(currentlyFormingCandle);
            chartPanel.getDataModel().fireDataUpdated();
        });
    }

    private void onLiveFootprintKLineUpdate(KLine incomingM1Tick) {
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

    private void onLiveTradeUpdate(TradeTick newTrade) {
        SwingUtilities.invokeLater(() -> {
            if (currentlyFormingCandle == null)
                return;
            footprintCalculator.addLiveTrade(newTrade, currentlyFormingCandle);
            chartPanel.getDataModel().fireDataUpdated();
        });
    }

    private void processNewM1Tick(KLine tick) {
        // 1. UI Buffer (Resampling Logic)
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
        
        // 2. Persistence Buffer (DB Logic)
        // We buffer the raw M1 tick. Similar replace/add logic to avoid duplicates.
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

        // 3. View Processing
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
        if (dbManager == null || source.dbPath() == null) return;

        final List<KLine> batchToSave;
        synchronized(pendingSaveBuffer) {
            if (pendingSaveBuffer.isEmpty()) return;
            batchToSave = new ArrayList<>(pendingSaveBuffer);
            // We intentionally keep the very last forming candle in the buffer
            // because it is still updating. We only remove finalized ones, 
            // OR we overwrite the last one on the next tick. 
            // For simplicity in this design: we clear, and the next tick re-adds 
            // or updates. Since saveKLines uses REPLACE, it's safe to save an incomplete bar.
            pendingSaveBuffer.clear();
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    // We explicitly save to "1m" timeframe table, as this is our base data
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

        new SwingWorker<Void, Void>() {
            private List<KLine> loadedTargetHistory;
            private List<KLine> loadedGapM1Data;

            @Override
            protected Void doInBackground() {
                Timeframe fetchTimeframe = Timeframe.getSmartBaseTimeframe(targetTimeframe);
                boolean requiresResampling = !fetchTimeframe.equals(targetTimeframe);

                int fetchLimit = 1000;
                if (requiresResampling) {
                    long targetDuration = targetTimeframe.duration().toMinutes();
                    long fetchDuration = Math.max(1, fetchTimeframe.duration().toMinutes());
                    double multiplier = (double) targetDuration / fetchDuration;
                    fetchLimit = (int) Math.min(1000 * multiplier, 5000);
                }

                logger.info("Loading history for {}. Fetching {} candles of {} (Smart Base).",
                        targetTimeframe.displayName(), fetchLimit, fetchTimeframe.displayName());

                List<KLine> rawFetchedData = dataProvider.getHistoricalData(source.symbol(), fetchTimeframe.displayName(), fetchLimit);

                if (rawFetchedData == null || rawFetchedData.isEmpty()) {
                    return null;
                }

                if (requiresResampling) {
                    loadedTargetHistory = DataResampler.resample(rawFetchedData, targetTimeframe);
                } else {
                    loadedTargetHistory = rawFetchedData;
                }

                if (loadedTargetHistory.isEmpty()) return null;

                KLine lastBulkCandle = loadedTargetHistory.remove(loadedTargetHistory.size() - 1);
                Instant gapStartTime = lastBulkCandle.timestamp();
                
                if (dataProvider instanceof BinanceProvider bp) {
                    loadedGapM1Data = bp.backfillHistoricalData(source.symbol(), baseTimeframe.displayName(), gapStartTime.toEpochMilli());
                } else if (dataProvider instanceof OkxProvider op) {
                    loadedGapM1Data = op.backfillHistoricalData(source.symbol(), baseTimeframe.displayName(), gapStartTime.toEpochMilli());
                } else {
                    loadedGapM1Data = dataProvider.getHistoricalData(source.symbol(), baseTimeframe.displayName(), 1000);
                    if (loadedGapM1Data != null) {
                        loadedGapM1Data = loadedGapM1Data.stream()
                            .filter(k -> !k.timestamp().isBefore(gapStartTime))
                            .collect(Collectors.toList());
                    }
                }
                
                return null;
            }

            @Override
            protected void done() {
                try {
                    if (loadedTargetHistory != null) {
                        finalizedCandles.addAll(loadedTargetHistory);
                    }

                    if (loadedGapM1Data != null && !loadedGapM1Data.isEmpty()) {
                        processGapData(loadedGapM1Data);
                    }

                    if (isFootprintMode) {
                        List<KLine> all = new ArrayList<>(finalizedCandles);
                        if (currentlyFormingCandle != null)
                            all.add(currentlyFormingCandle);
                        footprintCalculator.calculateHistoricalFootprints(all);
                    }

                    LiveDataManager.getInstance().subscribeToKLine(source.symbol(), baseTimeframe.displayName(),
                            liveKLineConsumer);
                    if (isFootprintMode)
                        LiveDataManager.getInstance().subscribeToTrades(source.symbol(), liveTradeConsumer);

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
    
    private void processGapData(List<KLine> gapData) {
        for (KLine k : gapData) {
            processNewM1Tick(k);
        }
    }

    public void fetchOlderHistoryAsync(long endTimeForFetch, int limit) {
        if (isFetchingHistory)
            return;
        isFetchingHistory = true;
        if (chartPanel != null)
            chartPanel.setLoading(true, "Loading older history...");

        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() throws Exception {
                Timeframe fetchTimeframe = Timeframe.getSmartBaseTimeframe(targetTimeframe);
                boolean requiresResampling = !fetchTimeframe.equals(targetTimeframe);
                
                int fetchLimit = limit;
                if (requiresResampling) {
                     long targetDuration = targetTimeframe.duration().toMinutes();
                     long fetchDuration = Math.max(1, fetchTimeframe.duration().toMinutes());
                     double multiplier = (double) targetDuration / fetchDuration;
                     fetchLimit = (int) Math.min(limit * multiplier, 1000);
                }

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
                            if (currentlyFormingCandle != null)
                                all.add(currentlyFormingCandle);
                            footprintCalculator.calculateHistoricalFootprints(all);
                        }

                        chartPanel.getDataModel().getInteractionManager().setStartIndex(
                                chartPanel.getDataModel().getInteractionManager().getStartIndex() + olderData.size());
                    }
                } catch (Exception e) {
                    logger.error("Failed to fetch older live data.", e);
                } finally {
                    isFetchingHistory = false;
                    if (chartPanel != null)
                        chartPanel.setLoading(false, null);
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