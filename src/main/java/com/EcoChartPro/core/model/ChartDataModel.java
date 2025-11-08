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

public class ChartDataModel implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ChartDataModel.class);

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private static final int INDICATOR_LOOKBACK_BUFFER = 500;
    private static final int LIVE_PAN_TRIGGER_THRESHOLD = 500;

    // --- Core State and Dependencies ---
    private List<KLine> visibleKLines;
    private BigDecimal minPrice, maxPrice;
    private DatabaseManager dbManager; // This field is used by auxiliary functions
    private DataSourceManager.ChartDataSource currentSource;
    private Timeframe currentDisplayTimeframe;
    private ChartInteractionManager interactionManager;
    private ChartPanel chartPanel;
    private final IndicatorManager indicatorManager;
    
    // --- Provider and Calculator Abstractions ---
    private IHistoryProvider historyProvider;
    private final FootprintCalculator footprintCalculator;

    // --- Caching ---
    private final Map<Timeframe, List<KLine>> htfCache = new ConcurrentHashMap<>();
    private List<KLine> heikinAshiCandlesCache;
    private boolean isHaCacheDirty = true;

    public ChartDataModel() {
        this.visibleKLines = new ArrayList<>();
        this.currentDisplayTimeframe = Timeframe.M1;
        this.indicatorManager = new IndicatorManager();
        this.footprintCalculator = new FootprintCalculator();
        DrawingManager.getInstance().addPropertyChangeListener("activeSymbolChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("activeSymbolChanged".equals(evt.getPropertyName())) {
            fireDataUpdated();
        } else if ("viewStateChanged".equals(evt.getPropertyName())) {
            updateView();
        } else if ("historyRebuilt".equals(evt.getPropertyName())) {
            updateView();
        }
    }

    public void setInteractionManager(ChartInteractionManager interactionManager) {
        this.interactionManager = interactionManager;
        this.interactionManager.addPropertyChangeListener(this);
    }

    public void cleanup() {
        DrawingManager.getInstance().removePropertyChangeListener("activeSymbolChanged", this);
        if (this.interactionManager != null) this.interactionManager.removePropertyChangeListener(this);
        if (this.historyProvider != null) {
            this.historyProvider.removePropertyChangeListener(this);
        }
        if (this.historyProvider != null) {
            this.historyProvider.cleanup();
            historyProvider = null;
        }
    }

    public void onChartTypeChanged(ChartType oldType, ChartType newType) {
        if (isInReplayMode()) {
            if (newType == ChartType.FOOTPRINT) {
                logger.warn("Footprint chart type is not supported in Replay mode.");
                if (chartPanel != null) chartPanel.setChartType(oldType);
            }
            return;
        }

        boolean switchedToOrFromFootprint = (newType == ChartType.FOOTPRINT && oldType != ChartType.FOOTPRINT) ||
                                            (newType != ChartType.FOOTPRINT && oldType == ChartType.FOOTPRINT);
        
        if (switchedToOrFromFootprint) {
            logger.info("Chart type changed to/from Footprint. Triggering data reload.");
            loadDataset(currentSource, currentDisplayTimeframe, true);
        }
    }
    
    // --- Data Loading Entry Points ---

    public void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe) {
        loadDataset(source, timeframe, false);
    }
    
    private void loadDataset(DataSourceManager.ChartDataSource source, Timeframe timeframe, boolean forceReload) {
        clearData();
        this.currentSource = source;
        this.currentDisplayTimeframe = timeframe;
        
        DataProvider liveDataProvider;
        switch (source.providerName()) {
            case "Binance": liveDataProvider = new BinanceProvider(); break;
            case "OKX": liveDataProvider = new OkxProvider(); break;
            default: liveDataProvider = null;
        }

        if (liveDataProvider != null) {
            boolean isFootprint = chartPanel != null && chartPanel.getChartType() == ChartType.FOOTPRINT;
            this.historyProvider = new LiveHistoryProvider(chartPanel, source, liveDataProvider, timeframe, isFootprint, footprintCalculator);
        } else {
            logger.error("Cannot create LiveHistoryProvider without a valid DataProvider for '{}'.", source.providerName());
        }
    }
    
    public void configureForReplay(Timeframe initialDisplayTimeframe, DataSourceManager.ChartDataSource source) {
        clearData();
        this.currentDisplayTimeframe = initialDisplayTimeframe;
        this.currentSource = source;
        ReplayHistoryProvider provider = new ReplayHistoryProvider(interactionManager, chartPanel, initialDisplayTimeframe);
        provider.addPropertyChangeListener(this);
        this.historyProvider = provider;
    }

    // --- Other Public Methods ---
    
    public void setDisplayTimeframe(Timeframe newTimeframe, boolean forceReload) {
        if (newTimeframe == null) return;
        if (!forceReload && this.currentDisplayTimeframe != null && this.currentDisplayTimeframe.equals(newTimeframe)) return;
        
        this.currentDisplayTimeframe = newTimeframe;
        htfCache.clear();
        isHaCacheDirty = true;
        
        if (historyProvider != null) {
            historyProvider.setTimeframe(newTimeframe, forceReload);
        }
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
    
    // --- Getters and Setters ---

    // [FIX] Restored this method to allow MainWindow to set the DB manager for auxiliary functions.
    public void setDatabaseManager(DatabaseManager dbManager, DataSourceManager.ChartDataSource source) {
        this.dbManager = dbManager;
    }

    public List<KLine> getAllChartableCandles() {
        if (historyProvider == null) return Collections.emptyList();
        List<KLine> all = new ArrayList<>(historyProvider.getFinalizedCandles());
        if (historyProvider.getFormingCandle() != null) all.add(historyProvider.getFormingCandle());
        return all;
    }

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
    
    public List<KLine> getResampledDataForView(Timeframe targetTimeframe) {
        if (htfCache.containsKey(targetTimeframe)) {
            return htfCache.get(targetTimeframe);
        }

        if (historyProvider instanceof ReplayHistoryProvider) {
            logger.warn("Resampling for HTF {} in replay mode is not yet fully supported post-refactor.", targetTimeframe);
            return Collections.emptyList(); 
        } else {
            logger.warn("Live MTF data not fully implemented. Returning empty list for {}.", targetTimeframe);
            return Collections.emptyList();
        }
    }
    
    // --- Private Helper Methods ---

    private void updateView() {
        if (historyProvider instanceof LiveHistoryProvider liveProvider) {
            checkForLivePanBack(liveProvider);
        }
        assembleVisibleKLines();
        calculateBoundaries();
        triggerIndicatorRecalculation();
        fireDataUpdated();
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
        if (calculationStartIndex >= calculationEndIndex || calculationStartIndex < 0 || calculationEndIndex > sourceData.size()) return Collections.emptyList();
        return sourceData.subList(calculationStartIndex, calculationEndIndex);
    }
    
    private void assembleVisibleKLines() {
        if (historyProvider == null) { this.visibleKLines = Collections.emptyList(); return; }
        int currentStartIndex = interactionManager.getStartIndex();
        int currentBarsPerScreen = interactionManager.getBarsPerScreen();
        int dataWindowStart = historyProvider.getDataWindowStartIndex();
        List<KLine> allChartableCandles = getAllChartableCandles();
        if (!allChartableCandles.isEmpty()) {
            int fromIndex = Math.max(0, currentStartIndex - dataWindowStart);
            int toIndex = Math.min(fromIndex + currentBarsPerScreen, allChartableCandles.size());
            this.visibleKLines = (fromIndex < toIndex) ? allChartableCandles.subList(fromIndex, toIndex) : Collections.emptyList();
        } else {
            this.visibleKLines = Collections.emptyList();
        }
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
    
    // Boilerplate getters, setters, and event firing
    public void setDisplayTimeframe(Timeframe newTimeframe) { setDisplayTimeframe(newTimeframe, false); }
    public void centerOnTrade(Trade trade) { /* Placeholder */ }
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

    // --- Property Change Listeners & Event Firing ---
    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.addPropertyChangeListener(propertyName, listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }
    
    public void fireDataUpdated() { pcs.firePropertyChange("dataUpdated", null, null); }
    public void fireLiveCandleAdded(KLine finalizedCandle) { pcs.firePropertyChange("liveCandleAdded", null, finalizedCandle); }
    public void fireLiveTickReceived(KLine formingCandle) { pcs.firePropertyChange("liveTickReceived", null, formingCandle); }
}