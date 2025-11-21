package com.EcoChartPro.core.model;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.indicator.IndicatorManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.model.calculators.FootprintCalculator;
import com.EcoChartPro.core.model.providers.IHistoryProvider;
import com.EcoChartPro.core.model.providers.LiveHistoryProvider;
import com.EcoChartPro.core.model.providers.ReplayHistoryProvider;
import com.EcoChartPro.data.DataProvider;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.data.DataTransformer;
import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.provider.OkxProvider;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.chart.FootprintBar;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central data model for a specific chart view.
 * Refactored to support Phase 4: Passing DatabaseManager to Live Provider for persistence.
 */
public class ChartDataModel implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataModel.class);
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int LIVE_PAN_TRIGGER_THRESHOLD = 500;

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // --- Core State ---
    private List<KLine> visibleKLines;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager; // Reference to the active DB
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private ChartInteractionManager interactionManager;
    private ChartPanel chartPanel;
    private final IndicatorManager indicatorManager;
    private final DrawingManager drawingManager;

    // --- Providers ---
    private IHistoryProvider historyProvider;
    private final FootprintCalculator footprintCalculator;

    // --- Caching ---
    private final Map<Timeframe, List<KLine>> htfCache = new ConcurrentHashMap<>();
    private List<KLine> heikinAshiCandlesCache;
    private boolean isHaCacheDirty = true;

    public ChartDataModel(DrawingManager drawingManager) {
        this.visibleKLines = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.footprintCalculator = new FootprintCalculator();
        this.drawingManager = drawingManager;
        this.drawingManager.addPropertyChangeListener("activeSymbolChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("activeSymbolChanged".equals(evt.getPropertyName())) {
            fireDataUpdated();
        } else if ("viewStateChanged".equals(evt.getPropertyName())) {
            updateView();
        }
    }

    public void setInteractionManager(ChartInteractionManager interactionManager) {
        this.interactionManager = interactionManager;
        this.interactionManager.addPropertyChangeListener(this);
    }

    // --- Data Loading ---

    public void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        loadDataset(source, timeframe, false);
    }

    private void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe, boolean forceReload) {
        clearData();
        this.currentSource = source;
        this.currentDisplayTimeframe = timeframe;

        DataProvider liveDataProvider;
        switch (source.providerName()) {
            case "Binance":
                liveDataProvider = new BinanceProvider();
                break;
            case "OKX":
                liveDataProvider = new OkxProvider();
                break;
            default:
                liveDataProvider = null;
        }

        if (liveDataProvider != null) {
            boolean isFootprint = chartPanel != null && chartPanel.getChartType() == ChartType.FOOTPRINT;
            
            // [PHASE 4] Pass the dbManager to the provider so it can save live data
            this.historyProvider = new LiveHistoryProvider(
                chartPanel, 
                source, 
                liveDataProvider, 
                timeframe, 
                isFootprint, 
                footprintCalculator,
                this.dbManager // <-- Injection point
            );
        } else {
            logger.error("Cannot create LiveHistoryProvider without a valid DataProvider for '{}'.", source.providerName());
        }
    }

    public void configureForReplay(Timeframe initialDisplayTimeframe, DataSourceManager.ChartDataSource source) {
        clearData();
        this.currentDisplayTimeframe = initialDisplayTimeframe;
        this.currentSource = source;
        this.historyProvider = new ReplayHistoryProvider(interactionManager, chartPanel, initialDisplayTimeframe);
    }

    // --- Data Access ---

    public List<KLine> getAllChartableCandles() {
        if (historyProvider == null) return Collections.emptyList();
        List<KLine> all = new ArrayList<>(historyProvider.getFinalizedCandles());
        KLine forming = historyProvider.getFormingCandle();
        if (forming != null) {
            all.add(forming);
        }
        return all;
    }

    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (htfCache.containsKey(targetTimeframe)) {
            return htfCache.get(targetTimeframe);
        }

        if (historyProvider instanceof LiveHistoryProvider) {
            if (targetTimeframe.equals(this.currentDisplayTimeframe)) {
                return getAllChartableCandles();
            } else {
                List<KLine> resampled = DataResampler.resample(historyProvider.getFinalizedCandles(), targetTimeframe);
                htfCache.put(targetTimeframe, resampled);
                return resampled;
            }
        }
        return Collections.emptyList();
    }

    // --- Live Update Handling ---

    public void fireLiveCandleAdded(KLine finalizedCandle) {
        Runnable updateTask = () -> {
            if (interactionManager != null) {
                interactionManager.onReplayTick(finalizedCandle);
            }
            updateView();
            pcs.firePropertyChange("liveCandleAdded", null, finalizedCandle);
        };
        runOnUIThread(updateTask);
    }

    public void fireLiveTickReceived(KLine formingCandle) {
        Runnable updateTask = () -> {
            if (interactionManager != null) {
                interactionManager.onReplayTick(formingCandle);
            }
            updateView();
            pcs.firePropertyChange("liveTickReceived", null, formingCandle);
        };
        runOnUIThread(updateTask);
    }
    
    private void runOnUIThread(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void updateView() {
        if (historyProvider instanceof LiveHistoryProvider liveProvider) {
            checkForLivePanBack(liveProvider);
        }
        assembleVisibleKLines();
        calculateBoundaries();
        triggerIndicatorRecalculation();
        fireDataUpdated();
    }

    private void assembleVisibleKLines() {
        if (historyProvider == null) {
            this.visibleKLines = Collections.emptyList();
            return;
        }
        
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();
        int dataWindowStart = historyProvider.getDataWindowStartIndex();
        
        List<KLine> allChartableCandles = getAllChartableCandles();
        
        if (!allChartableCandles.isEmpty()) {
            int fromIndex = Math.max(0, currentStartIndex - dataWindowStart);
            int toIndex = Math.min(fromIndex + currentBarsPerScreen, allChartableCandles.size());
            
            if (fromIndex < toIndex) {
                this.visibleKLines = allChartableCandles.subList(fromIndex, toIndex);
            } else {
                this.visibleKLines = Collections.emptyList();
            }
        } else {
            this.visibleKLines = Collections.emptyList();
        }
    }

    public void setDisplayTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && this.currentDisplayTimeframe != null && this.currentDisplayTimeframe.equals(newTimeframe)) return;

        Timeframe oldTimeframe = this.currentDisplayTimeframe;
        this.currentDisplayTimeframe = newTimeframe;
        htfCache.clear();
        isHaCacheDirty = true;

        if (historyProvider != null) {
            historyProvider.setTimeframe(newTimeframe, forceReload);
        }
        pcs.firePropertyChange("displayTimeframeChanged", oldTimeframe, newTimeframe);
    }

    public void setDisplayTimeframe(Timeframe newTimeframe) {
        setDisplayTimeframe(newTimeframe, false);
    }

    public void clearData() {
        this.visibleKLines.clear();
        this.minPrice = BigDecimal.ZERO;
        this.maxPrice = BigDecimal.ZERO;
        this.indicatorManager.resetAllIndicators();
        this.footprintCalculator.clear();
        this.htfCache.clear();
        this.isHaCacheDirty = true;
        if (historyProvider != null) {
            historyProvider.cleanup();
            historyProvider = null;
        }
        fireDataUpdated();
    }

    private void calculateBoundaries() {
        if (visibleKLines.isEmpty()) {
            minPrice = BigDecimal.ZERO;
            maxPrice = BigDecimal.ONE;
            return;
        }
        minPrice = visibleKLines.get(0).low();
        maxPrice = visibleKLines.get(0).high();
        for (KLine k : visibleKLines) {
            if (k.low().compareTo(minPrice) < 0) minPrice = k.low();
            if (k.high().compareTo(maxPrice) > 0) maxPrice = k.high();
        }
    }

    public void triggerIndicatorRecalculation() {
        indicatorManager.recalculateAll(this, getIndicatorDataSlice());
    }

    private List<KLine> getIndicatorDataSlice() {
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();
        int dataWindowStart = (historyProvider != null) ? historyProvider.getDataWindowStartIndex() : 0;
        
        List<KLine> sourceData = getAllChartableCandles();
        if (sourceData.isEmpty()) return Collections.emptyList();
        
        int viewStartInWindow = currentStartIndex - dataWindowStart;
        int calculationEndIndex = Math.min(viewStartInWindow + currentBarsPerScreen, sourceData.size());
        int calculationStartIndex = Math.max(0, viewStartInWindow - INDICATOR_LOOKBACK_BUFFER);
        
        if (calculationStartIndex >= calculationEndIndex || calculationStartIndex < 0 || calculationEndIndex > sourceData.size()) {
            return Collections.emptyList();
        }
        return sourceData.subList(calculationStartIndex, calculationEndIndex);
    }

    private void checkForLivePanBack(LiveHistoryProvider liveProvider) {
        if (interactionManager.getStartIndex() < LIVE_PAN_TRIGGER_THRESHOLD) {
            List<KLine> candles = liveProvider.getFinalizedCandles();
            if (candles.isEmpty()) return;
            KLine oldestCandle = candles.get(0);
            long endTimeForFetch = oldestCandle.timestamp().toEpochMilli();
            liveProvider.fetchOlderHistoryAsync(endTimeForFetch, 1000);
        }
    }

    public void fireDataUpdated() {
        pcs.firePropertyChange("dataUpdated", null, null);
    }

    public void setDatabaseManager(DatabaseManager dbManager, DataSourceManager.ChartDataSource source) {
        this.dbManager = dbManager;
        // If a provider already exists (e.g., switch was called separately), we might need to update it, 
        // but usually setDatabaseManager happens before loadDataset.
    }

    public void cleanup() {
        if (this.drawingManager != null) {
            this.drawingManager.removePropertyChangeListener("activeSymbolChanged", this);
        }
        if (this.interactionManager != null)
            this.interactionManager.removePropertyChangeListener(this);
        if (this.historyProvider != null) {
            this.historyProvider.cleanup();
            historyProvider = null;
        }
    }

    public void onChartTypeChanged(ChartType oldType, ChartType newType) {
        if (isInReplayMode()) {
            if (newType == ChartType.FOOTPRINT) {
                logger.warn("Footprint chart type is not supported in Replay mode.");
                if (chartPanel != null)
                    chartPanel.setChartType(oldType);
            }
            return;
        }
        boolean switchedToOrFromFootprint = (newType == ChartType.FOOTPRINT && oldType != ChartType.FOOTPRINT) ||
                (newType != ChartType.FOOTPRINT && oldType == ChartType.FOOTPRINT);
        if (switchedToOrFromFootprint) {
            loadDataset(currentSource, currentDisplayTimeframe, true);
        }
    }

    // Standard Getters/Setters
    public void setView(ChartPanel chartPanel) { this.chartPanel = chartPanel; }
    public ChartPanel getChartPanel() { return chartPanel; }
    public IndicatorManager getIndicatorManager() { return indicatorManager; }
    public List<KLine> getVisibleKLines() { return visibleKLines; }
    public DataSourceManager.ChartDataSource getCurrentSymbol() { return this.currentSource; }
    public Timeframe getCurrentDisplayTimeframe() { return this.currentDisplayTimeframe; }
    public int getTotalCandleCount() { return (historyProvider != null) ? historyProvider.getTotalCandleCount() : 0; }
    public KLine getCurrentReplayKLine() { return (historyProvider != null) ? historyProvider.getFormingCandle() : null; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public boolean isInReplayMode() { return historyProvider instanceof ReplayHistoryProvider; }
    public ChartInteractionManager getInteractionManager() { return this.interactionManager; }
    
    public List<KLine> getHeikinAshiCandles() {
        if (!isHaCacheDirty && heikinAshiCandlesCache != null) return heikinAshiCandlesCache;
        List<KLine> allRaw = getAllChartableCandles();
        heikinAshiCandlesCache = DataTransformer.transformToHeikinAshi(allRaw);
        isHaCacheDirty = false;
        return heikinAshiCandlesCache;
    }

    public Map<Instant, FootprintBar> getFootprintData() {
        return this.footprintCalculator.getFootprintData();
    }
    
    public void centerOnTrade(Trade trade) { /* Placeholder */ }

    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.addPropertyChangeListener(propertyName, listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.removePropertyChangeListener(propertyName, listener); }
}