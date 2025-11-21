package com.EcoChartPro.core.model.providers;

import com.EcoChartPro.core.model.calculators.FootprintCalculator;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.utils.DataSourceManager;
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
 * FEATURES:
 * 1. Hybrid Loading: Bulk History (Smart Base) + 1m Gap Fill.
 * 2. Custom Timeframe Support: Automatically fetches divisible base data (e.g., 9m -> fetch 3m) and resamples.
 * 3. 1m Base Stream: Always subscribes to 1m data for live updates.
 * 4. Live Aggregation: Accumulates 1m bars to build/finalize Target bars.
 */
public class LiveHistoryProvider implements IHistoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveHistoryProvider.class);
    
    private static final int MAX_BUFFER_SIZE = 5000; 

    // --- Dependencies ---
    private final ChartPanel chartPanel;
    private final DataSourceManager.ChartDataSource source;
    private final DataProvider dataProvider;
    private final FootprintCalculator footprintCalculator;
    private final boolean isFootprintMode;

    // --- State ---
    private Timeframe targetTimeframe;
    private final Timeframe baseTimeframe = Timeframe.M1; // Always bridge from M1 for live updates

    // BUFFER: Holds 1m candles for the current forming Target candle
    private final List<KLine> formingBuffer = new ArrayList<>();

    // VIEW: Finalized Target candles
    private List<KLine> finalizedCandles = new ArrayList<>();
    private KLine currentlyFormingCandle;

    private volatile boolean isFetchingHistory = false;

    private final Consumer<KLine> liveKLineConsumer;
    private final Consumer<TradeTick> liveTradeConsumer;

    public LiveHistoryProvider(ChartPanel chartPanel, DataSourceManager.ChartDataSource source,
            DataProvider dataProvider, Timeframe initialTimeframe, boolean isFootprintMode,
            FootprintCalculator footprintCalculator) {
        this.chartPanel = chartPanel;
        this.source = source;
        this.dataProvider = dataProvider;
        this.isFootprintMode = isFootprintMode;
        this.footprintCalculator = footprintCalculator;

        this.liveKLineConsumer = isFootprintMode ? this::onLiveFootprintKLineUpdate : this::onLiveBaseKLineUpdate;
        this.liveTradeConsumer = isFootprintMode ? this::onLiveTradeUpdate : null;

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

    /**
     * Loads history intelligently.
     * If Target is "9m", it fetches "3m" from API and resamples.
     * If Target is "7m", it fetches "1m" from API and resamples.
     */
    private void loadInitialHistory() {
        if (chartPanel != null)
            chartPanel.setLoading(true, "Loading " + targetTimeframe.displayName() + "...");

        new SwingWorker<Void, Void>() {
            private List<KLine> loadedTargetHistory;
            private List<KLine> loadedGapM1Data;

            @Override
            protected Void doInBackground() {
                // 1. Determine Smart Base Timeframe for Fetching
                // E.g. if Target=9m, SmartBase=3m. If Target=7m, SmartBase=1m.
                Timeframe fetchTimeframe = Timeframe.getSmartBaseTimeframe(targetTimeframe);
                boolean requiresResampling = !fetchTimeframe.equals(targetTimeframe);

                int fetchLimit = 1000;
                // If we are fetching a smaller timeframe to resample, we need more bars 
                // to cover the same time period.
                if (requiresResampling) {
                    long targetDuration = targetTimeframe.duration().toMinutes();
                    long fetchDuration = Math.max(1, fetchTimeframe.duration().toMinutes());
                    double multiplier = (double) targetDuration / fetchDuration;
                    fetchLimit = (int) Math.min(1000 * multiplier, 5000); // Cap at 5000 for API safety
                }

                logger.info("Loading history for {}. Fetching {} candles of {} (Smart Base). Resampling needed: {}",
                        targetTimeframe.displayName(), fetchLimit, fetchTimeframe.displayName(), requiresResampling);

                // 2. Fetch History using the Smart Base
                List<KLine> rawFetchedData = dataProvider.getHistoricalData(source.symbol(), fetchTimeframe.displayName(), fetchLimit);

                if (rawFetchedData == null || rawFetchedData.isEmpty()) {
                    return null;
                }

                // 3. Resample if necessary (e.g., 3m -> 9m)
                if (requiresResampling) {
                    loadedTargetHistory = DataResampler.resample(rawFetchedData, targetTimeframe);
                } else {
                    loadedTargetHistory = rawFetchedData;
                }

                if (loadedTargetHistory.isEmpty()) return null;

                // 4. Identify Gap & Backfill
                KLine lastBulkCandle = loadedTargetHistory.remove(loadedTargetHistory.size() - 1);
                Instant gapStartTime = lastBulkCandle.timestamp();
                
                // Gap fill always uses 1m for precision
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

                    // Subscribe to 1m stream
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
                // Determine Smart Base for older history too
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