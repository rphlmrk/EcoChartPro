package com.EcoChartPro.core.model.providers;

import com.EcoChartPro.core.model.calculators.FootprintCalculator;
import com.EcoChartPro.data.DataProvider;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the state and data fetching/subscription for a chart in Live mode.
 * It interacts with the LiveDataManager to receive real-time ticks and uses
 * a DataProvider to fetch historical data when needed (e.g., on initial load or deep panning).
 */
public class LiveHistoryProvider implements IHistoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveHistoryProvider.class);

    // --- Dependencies ---
    private final ChartPanel chartPanel;
    private final DataSourceManager.ChartDataSource source;
    private final DataProvider dataProvider;
    private final FootprintCalculator footprintCalculator;
    private final boolean isFootprintMode;

    // --- State ---
    private Timeframe currentTimeframe;
    private List<KLine> finalizedCandles = new ArrayList<>();
    private KLine currentlyFormingCandle;
    private volatile boolean isFetchingHistory = false;
    
    // --- Live Subscription ---
    private final Consumer<KLine> liveKLineConsumer;
    private final Consumer<TradeTick> liveTradeConsumer;

    public LiveHistoryProvider(ChartPanel chartPanel, DataSourceManager.ChartDataSource source, DataProvider dataProvider, Timeframe initialTimeframe, boolean isFootprintMode, FootprintCalculator footprintCalculator) {
        this.chartPanel = chartPanel;
        this.source = source;
        this.dataProvider = dataProvider;
        this.currentTimeframe = initialTimeframe;
        this.isFootprintMode = isFootprintMode;
        this.footprintCalculator = footprintCalculator;

        // Create stable references for consumers
        this.liveKLineConsumer = isFootprintMode ? this::onLiveFootprintKLineUpdate : this::onLiveStandardKLineUpdate;
        this.liveTradeConsumer = isFootprintMode ? this::onLiveTradeUpdate : null;

        loadInitialHistory();
    }
    
    // --- IHistoryProvider Implementation (Unchanged) ---
    @Override public List<KLine> getFinalizedCandles() { return finalizedCandles; }
    @Override public KLine getFormingCandle() { return currentlyFormingCandle; }
    @Override public int getTotalCandleCount() { return finalizedCandles.size() + (currentlyFormingCandle != null ? 1 : 0); }
    @Override public int getDataWindowStartIndex() { return 0; }

    @Override
    public void setTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && newTimeframe.equals(this.currentTimeframe)) return;

        cleanupSubscriptions();
        this.currentTimeframe = newTimeframe;
        this.finalizedCandles.clear();
        this.currentlyFormingCandle = null;
        if (isFootprintMode) this.footprintCalculator.clear();
        loadInitialHistory();
    }

    @Override
    public void cleanup() {
        cleanupSubscriptions();
    }

    // --- Live Data Handling ---

    private void onLiveStandardKLineUpdate(KLine newTick) {
        SwingUtilities.invokeLater(() -> {
            Instant intervalStart = getIntervalStart(newTick.timestamp(), currentTimeframe);
            if (currentlyFormingCandle == null) {
                currentlyFormingCandle = newTick;
            } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
                finalizedCandles.add(currentlyFormingCandle);
                chartPanel.getDataModel().fireLiveCandleAdded(currentlyFormingCandle);
                currentlyFormingCandle = newTick;
            } else {
                currentlyFormingCandle = new KLine(currentlyFormingCandle.timestamp(), currentlyFormingCandle.open(), currentlyFormingCandle.high().max(newTick.high()), currentlyFormingCandle.low().min(newTick.low()), newTick.close(), currentlyFormingCandle.volume().add(newTick.volume()));
            }
            chartPanel.getDataModel().fireLiveTickReceived(currentlyFormingCandle);
            chartPanel.getDataModel().fireDataUpdated();
        });
    }

    private void onLiveFootprintKLineUpdate(KLine newTick) {
        SwingUtilities.invokeLater(() -> {
            Instant intervalStart = getIntervalStart(newTick.timestamp(), currentTimeframe);
            if (currentlyFormingCandle == null || !currentlyFormingCandle.timestamp().equals(intervalStart)) {
                if (currentlyFormingCandle != null) {
                    finalizedCandles.add(currentlyFormingCandle);
                    chartPanel.getDataModel().fireLiveCandleAdded(currentlyFormingCandle);
                }
                currentlyFormingCandle = newTick;
                // Pre-create the footprint bar for the new interval
                footprintCalculator.addLiveTrade(new TradeTick(Instant.now(), newTick.open(), BigDecimal.ZERO, "buy"), newTick);
                chartPanel.getDataModel().fireDataUpdated();
            }
        });
    }

    private void onLiveTradeUpdate(TradeTick newTrade) {
        SwingUtilities.invokeLater(() -> {
            if (currentlyFormingCandle == null) return;
            footprintCalculator.addLiveTrade(newTrade, currentlyFormingCandle);
            currentlyFormingCandle = new KLine(currentlyFormingCandle.timestamp(), currentlyFormingCandle.open(), currentlyFormingCandle.high().max(newTrade.price()), currentlyFormingCandle.low().min(newTrade.price()), newTrade.price(), currentlyFormingCandle.volume().add(newTrade.quantity()));
            chartPanel.getDataModel().fireLiveTickReceived(currentlyFormingCandle);
            chartPanel.getDataModel().fireDataUpdated();
        });
    }

    // --- Historical Data Fetching ---

    public void fetchOlderHistoryAsync(long endTimeForFetch, int limit) {
        if (isFetchingHistory) return;
        isFetchingHistory = true;
        if (chartPanel != null) chartPanel.setLoading(true, "Loading older history...");
        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() throws Exception {
                if (dataProvider instanceof BinanceProvider bp) return bp.getHistoricalData(source.symbol(), currentTimeframe.displayName(), limit, null, endTimeForFetch);
                else if (dataProvider instanceof OkxProvider op) return op.getHistoricalData(source.symbol(), currentTimeframe.displayName(), limit, endTimeForFetch, null);
                return Collections.emptyList();
            }
            @Override
            protected void done() {
                try {
                    List<KLine> olderData = get();
                    if (olderData != null && !olderData.isEmpty()) {
                        finalizedCandles.addAll(0, olderData);
                        if(isFootprintMode) footprintCalculator.calculateHistoricalFootprints(finalizedCandles);
                        logger.info("Fetched and prepended {} older candles in Live mode.", olderData.size());
                        chartPanel.getDataModel().getInteractionManager().setStartIndex(chartPanel.getDataModel().getInteractionManager().getStartIndex() + olderData.size());
                    }
                } catch (Exception e) { logger.error("Failed to fetch older live data.", e);
                } finally {
                    isFetchingHistory = false;
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                    chartPanel.getDataModel().fireDataUpdated();
                }
            }
        }.execute();
    }

    private void loadInitialHistory() {
        if (chartPanel != null) chartPanel.setLoading(true, "Loading " + currentTimeframe.displayName() + " history...");
        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() {
                return dataProvider.getHistoricalData(source.symbol(), currentTimeframe.displayName(), 1000);
            }
            @Override
            protected void done() {
                try {
                    List<KLine> initialData = get();
                    if (initialData != null && !initialData.isEmpty()) {
                        finalizedCandles = new ArrayList<>(initialData.subList(0, initialData.size() - 1));
                        currentlyFormingCandle = initialData.get(initialData.size() - 1);
                        if (isFootprintMode) {
                            footprintCalculator.calculateHistoricalFootprints(initialData);
                        }
                    } else { /* Handle empty initial data */ }
                    
                    LiveDataManager.getInstance().subscribeToKLine(source.symbol(), currentTimeframe.displayName(), liveKLineConsumer);
                    if (isFootprintMode) {
                        LiveDataManager.getInstance().subscribeToTrades(source.symbol(), liveTradeConsumer);
                    }

                    int dataBarsOnScreen = (int) (chartPanel.getDataModel().getInteractionManager().getBarsPerScreen() * (1.0 - chartPanel.getDataModel().getInteractionManager().getRightMarginRatio()));
                    int initialStartIndex = Math.max(0, finalizedCandles.size() - dataBarsOnScreen);
                    chartPanel.getDataModel().getInteractionManager().setStartIndex(initialStartIndex);
                    chartPanel.getDataModel().fireDataUpdated();
                } catch (Exception e) {
                    logger.error("Failed to load live history for timeframe {}", currentTimeframe, e);
                } finally {
                    if (chartPanel != null) chartPanel.setLoading(false, null);
                }
            }
        }.execute();
    }

    private void cleanupSubscriptions() {
        if (source != null && currentTimeframe != null) {
            if (liveKLineConsumer != null) {
                LiveDataManager.getInstance().unsubscribeFromKLine(source.symbol(), currentTimeframe.displayName(), liveKLineConsumer);
            }
            if (liveTradeConsumer != null) {
                LiveDataManager.getInstance().unsubscribeFromTrades(source.symbol(), liveTradeConsumer);
            }
        }
    }
    
    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp;
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
}