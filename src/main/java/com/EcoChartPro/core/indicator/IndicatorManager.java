package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorRunner.CalculationResult;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class IndicatorManager {
    private static final Logger logger = LoggerFactory.getLogger(IndicatorManager.class);
    private final Map<UUID, IndicatorRunner> activeIndicators = new ConcurrentHashMap<>();
    private final Map<UUID, List<IndicatorContext.DebugLogEntry>> debugData = new ConcurrentHashMap<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public IndicatorManager() {
        // Constructor is empty.
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addIndicator(Indicator indicator, ChartDataModel dataModel) {
        if (indicator != null) {
            IndicatorRunner runner = new IndicatorRunner(indicator, dataModel);
            activeIndicators.put(indicator.getId(), runner);
            logger.info("Added indicator: {} with settings {}", indicator.getName(), indicator.getSettings());

            // [NEW] Pre-fetch HTF data for HTF Overlay if its required TF is higher than the current display TF.
            if ("HTF Overlay".equals(indicator.getName())) {
                Object htfSetting = indicator.getSettings().get("Higher Timeframe");
                if (htfSetting instanceof String) {
                    Timeframe htf = Timeframe.fromString((String) htfSetting);
                    Timeframe displayTf = dataModel.getCurrentDisplayTimeframe();
                    if (htf != null && displayTf != null && htf.duration().compareTo(displayTf.duration()) > 0) {
                        logger.info("Pre-fetching data for HTF Overlay ({})...", htf.displayName());
                        // This call will trigger the direct fetch and cache the data.
                        dataModel.getResampledDataForView(htf);
                    }
                }
            }

            pcs.firePropertyChange("indicatorAdded", null, indicator);
        }
    }

    public void removeIndicator(UUID id) {
        IndicatorRunner removedRunner = activeIndicators.remove(id);
        debugData.remove(id);
        if (removedRunner != null) {
            removedRunner.onRemoved(); 
            Indicator removedIndicator = removedRunner.getIndicator();
            logger.info("Removed indicator: {} (ID: {})", removedIndicator.getName(), id);
            pcs.firePropertyChange("indicatorRemoved", null, removedIndicator.getId());
        }
    }

    public void updateIndicatorSettings(UUID id, Map<String, Object> newSettings) {
        IndicatorRunner runner = activeIndicators.get(id);
        if (runner != null) {
            runner.onSettingsChanged(newSettings); 
            Indicator indicator = runner.getIndicator();
            logger.info("Updating settings for indicator: {} (ID: {}). New settings: {}", indicator.getName(), id, newSettings);
            pcs.firePropertyChange("indicatorUpdated", null, indicator);
        }
    }

    public void recalculateAll(ChartDataModel dataModel, List<KLine> dataSlice) {
        for (IndicatorRunner runner : activeIndicators.values()) {
            CalculationResult result = runner.recalculate(dataSlice);
            Indicator indicator = runner.getIndicator();
            indicator.results.clear();
            indicator.results.addAll(result.drawables());

            debugData.put(indicator.getId(), result.debugLogs());
            pcs.firePropertyChange("debugDataUpdated", indicator.getId(), result.debugLogs());
        }
    }

    public void addOrUpdateFromLiveCode(CustomIndicator plugin, ChartDataModel dataModel) {
        Optional<IndicatorRunner> existingRunnerOpt = activeIndicators.values().stream()
                .filter(runner -> runner.getIndicator() instanceof CustomIndicatorAdapter)
                .filter(runner -> ((CustomIndicatorAdapter) runner.getIndicator()).getPlugin().getName().equals(plugin.getName()))
                .findFirst();

        if (existingRunnerOpt.isPresent()) {
            IndicatorRunner existingRunner = existingRunnerOpt.get();
            Indicator existingIndicator = existingRunner.getIndicator();
            
            CustomIndicatorAdapter newAdapter = new CustomIndicatorAdapter(
                existingIndicator.getId(), 
                plugin, 
                existingIndicator.getSettings()
            );
            
            activeIndicators.put(existingIndicator.getId(), new IndicatorRunner(newAdapter, dataModel));
            
            logger.info("Hot-reloaded indicator: {}", plugin.getName());
            pcs.firePropertyChange("indicatorUpdated", null, newAdapter);
        } else {
            Map<String, Object> defaultSettings = new HashMap<>();
            plugin.getParameters().forEach(p -> defaultSettings.put(p.key(), p.defaultValue()));
            addIndicator(new CustomIndicatorAdapter(plugin, defaultSettings), dataModel);
        }

        dataModel.triggerIndicatorRecalculation();
        dataModel.fireDataUpdated();
    }

    public List<Indicator> getIndicators() {
        return activeIndicators.values().stream()
                .map(IndicatorRunner::getIndicator)
                .collect(Collectors.toList());
    }

    public List<IndicatorContext.DebugLogEntry> getDebugDataFor(UUID indicatorId) {
        return debugData.getOrDefault(indicatorId, Collections.emptyList());
    }

    public void clearAllIndicators() {
        activeIndicators.values().forEach(IndicatorRunner::onRemoved);
        activeIndicators.clear();
        debugData.clear();
        logger.info("All active indicators have been cleared.");
    }
}