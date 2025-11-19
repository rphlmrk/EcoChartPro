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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages state, data fetching, and resampling for a chart in Live mode.
 * Acts as an engine that converts a Base Data Stream (e.g., 5m) into a Target
 * View (e.g., 20m).
 */
public class LiveHistoryProvider implements IHistoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(LiveHistoryProvider.class);
    private static final int MAX_BASE_CACHE_SIZE = 10000; // [NEW] Memory limit constant

    // --- Dependencies ---
    private final ChartPanel chartPanel;
    private final DataSourceManager.ChartDataSource source;
    private final DataProvider dataProvider;
    private final FootprintCalculator footprintCalculator;
    private final boolean isFootprintMode;

    // --- State ---
    private Timeframe targetTimeframe;
    private Timeframe baseTimeframe;

    // CACHE: Raw data from the provider
    private final List<KLine> baseDataCache = new ArrayList<>();

    // VIEW: Resampled data for the chart
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
        this.baseTimeframe = Timeframe.getSmartBaseTimeframe(newTimeframe);

        logger.info("Timeframe set to {}. Using Base: {}", targetTimeframe, baseTimeframe);

        this.baseDataCache.clear();
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

    private void onLiveBaseKLineUpdate(KLine incomingBaseTick) {
        SwingUtilities.invokeLater(() -> {
            updateBaseCache(incomingBaseTick);
            refreshViewFromCache();
            chartPanel.getDataModel().fireLiveTickReceived(currentlyFormingCandle);
            chartPanel.getDataModel().fireDataUpdated();
        });
    }

    private void onLiveFootprintKLineUpdate(KLine incomingBaseTick) {
        SwingUtilities.invokeLater(() -> {
            updateBaseCache(incomingBaseTick);
            refreshViewFromCache();
            if (currentlyFormingCandle != null) {
                footprintCalculator.addLiveTrade(
                        new TradeTick(Instant.now(), incomingBaseTick.close(), BigDecimal.ZERO, "buy"),
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

    private void updateBaseCache(KLine tick) {
        if (baseDataCache.isEmpty()) {
            baseDataCache.add(tick);
            return;
        }

        KLine lastBase = baseDataCache.get(baseDataCache.size() - 1);
        if (tick.timestamp().equals(lastBase.timestamp())) {
            // Update existing forming base candle
            baseDataCache.set(baseDataCache.size() - 1, tick);
        } else {
            // New base candle started
            baseDataCache.add(tick);

            // [NEW] Memory Management: Prune old data if cache exceeds limit
            // We only remove data from the head (oldest) if it's not needed for the view.
            // Since the View is resampled from this cache, removing the head might shift
            // the view indices, but since this is live mode, we typically look at the tail.
            if (baseDataCache.size() > MAX_BASE_CACHE_SIZE) {
                // Remove the oldest candle
                baseDataCache.remove(0);

                // We must also adjust the startIndex in ChartInteractionManager to prevent
                // the chart from "jumping" visually, as the total count effectively decreases
                // relative to the viewport if we are scrolled back.
                // However, for Live mode defaulting to the right edge, this is less critical
                // than preventing OOM.
            }
        }
    }

    private void refreshViewFromCache() {
        List<KLine> resampled = DataResampler.resample(baseDataCache, targetTimeframe);

        if (!resampled.isEmpty()) {
            KLine newForming = resampled.get(resampled.size() - 1);

            if (currentlyFormingCandle != null && !newForming.timestamp().equals(currentlyFormingCandle.timestamp())) {
                finalizedCandles.add(currentlyFormingCandle);
                chartPanel.getDataModel().fireLiveCandleAdded(currentlyFormingCandle);
            }

            if (finalizedCandles.size() != resampled.size() - 1) {
                if (resampled.size() > 1) {
                    finalizedCandles = new ArrayList<>(resampled.subList(0, resampled.size() - 1));
                } else {
                    finalizedCandles.clear();
                }
            }

            currentlyFormingCandle = newForming;
        }
    }

    private void loadInitialHistory() {
        if (chartPanel != null)
            chartPanel.setLoading(true, "Optimizing " + targetTimeframe.displayName() + " view...");

        new SwingWorker<List<KLine>, Void>() {
            @Override
            protected List<KLine> doInBackground() {
                long targetMin = targetTimeframe.duration().toMinutes();
                long baseMin = baseTimeframe.duration().toMinutes();
                double multiplier = (double) targetMin / Math.max(1, baseMin);
                int baseLimit = (int) Math.min(1000 * multiplier, 5000);

                return dataProvider.getHistoricalData(source.symbol(), baseTimeframe.displayName(), baseLimit);
            }

            @Override
            protected void done() {
                try {
                    List<KLine> initialBaseData = get();
                    if (initialBaseData != null && !initialBaseData.isEmpty()) {
                        baseDataCache.addAll(initialBaseData);
                        refreshViewFromCache();

                        if (isFootprintMode) {
                            List<KLine> all = new ArrayList<>(finalizedCandles);
                            if (currentlyFormingCandle != null)
                                all.add(currentlyFormingCandle);
                            footprintCalculator.calculateHistoricalFootprints(all);
                        }
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
                    logger.error("Failed to load live history for timeframe {}", targetTimeframe, e);
                } finally {
                    if (chartPanel != null)
                        chartPanel.setLoading(false, null);
                }
            }
        }.execute();
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
                long targetMin = targetTimeframe.duration().toMinutes();
                long baseMin = baseTimeframe.duration().toMinutes();
                double multiplier = (double) targetMin / Math.max(1, baseMin);
                int baseLimit = (int) Math.min(limit * multiplier, 1000);

                if (dataProvider instanceof BinanceProvider bp) {
                    return bp.getHistoricalData(source.symbol(), baseTimeframe.displayName(), baseLimit, null,
                            endTimeForFetch);
                } else if (dataProvider instanceof OkxProvider op) {
                    return op.getHistoricalData(source.symbol(), baseTimeframe.displayName(), baseLimit, null,
                            endTimeForFetch);
                }
                return Collections.emptyList();
            }

            @Override
            protected void done() {
                try {
                    List<KLine> olderData = get();
                    if (olderData != null && !olderData.isEmpty()) {
                        baseDataCache.addAll(0, olderData);
                        refreshViewFromCache();

                        if (isFootprintMode) {
                            List<KLine> all = new ArrayList<>(finalizedCandles);
                            if (currentlyFormingCandle != null)
                                all.add(currentlyFormingCandle);
                            footprintCalculator.calculateHistoricalFootprints(all);
                        }

                        long multiplier = Math.max(1,
                                targetTimeframe.duration().toMinutes() / baseTimeframe.duration().toMinutes());
                        int addedTargetBars = olderData.size() / (int) multiplier;
                        chartPanel.getDataModel().getInteractionManager().setStartIndex(
                                chartPanel.getDataModel().getInteractionManager().getStartIndex() + addedTargetBars);
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
        if (source != null && baseTimeframe != null) {
            if (liveKLineConsumer != null)
                LiveDataManager.getInstance().unsubscribeFromKLine(source.symbol(), baseTimeframe.displayName(),
                        liveKLineConsumer);
            if (liveTradeConsumer != null)
                LiveDataManager.getInstance().unsubscribeFromTrades(source.symbol(), liveTradeConsumer);
        }
    }
}