package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorRunner.CalculationResult;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.model.KLine;
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
import java.util.stream.Collectors; // [NEW] Import

public final class IndicatorManager {
    private static final Logger logger = LoggerFactory.getLogger(IndicatorManager.class);
    // [MODIFIED] The manager now holds runners, which in turn hold the indicator and its state.
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

    public void addIndicator(Indicator indicator, ChartDataModel dataModel) { // [MODIFIED] Signature
        if (indicator != null) {
            // [MODIFIED] Create and store an IndicatorRunner.
            IndicatorRunner runner = new IndicatorRunner(indicator, dataModel);
            activeIndicators.put(indicator.getId(), runner);
            logger.info("Added indicator: {} with settings {}", indicator.getName(), indicator.getSettings());
            pcs.firePropertyChange("indicatorAdded", null, indicator);
        }
    }

    public void removeIndicator(UUID id) {
        // [MODIFIED] Retrieve the runner, call the hook, then remove.
        IndicatorRunner removedRunner = activeIndicators.remove(id);
        debugData.remove(id);
        if (removedRunner != null) {
            removedRunner.onRemoved(); // Trigger lifecycle hook.
            Indicator removedIndicator = removedRunner.getIndicator();
            logger.info("Removed indicator: {} (ID: {})", removedIndicator.getName(), id);
            pcs.firePropertyChange("indicatorRemoved", null, removedIndicator.getId());
        }
    }

    public void updateIndicatorSettings(UUID id, Map<String, Object> newSettings) {
        // [MODIFIED] Retrieve the runner and call its hook handler.
        IndicatorRunner runner = activeIndicators.get(id);
        if (runner != null) {
            runner.onSettingsChanged(newSettings); // This calls the hook and updates settings internally.
            Indicator indicator = runner.getIndicator();
            logger.info("Updating settings for indicator: {} (ID: {}). New settings: {}", indicator.getName(), id, newSettings);
            pcs.firePropertyChange("indicatorUpdated", null, indicator);
        }
    }

    public void recalculateAll(ChartDataModel dataModel, List<KLine> dataSlice) {
        // [MODIFIED] Iterate over runners.
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
            
            // [MODIFIED] Create a new adapter with the *preserved UUID*.
            CustomIndicatorAdapter newAdapter = new CustomIndicatorAdapter(
                existingIndicator.getId(), 
                plugin, 
                existingIndicator.getSettings()
            );
            
            // Replace the old runner with a new one. This implicitly resets the state, which is correct for a hot-reload.
            activeIndicators.put(existingIndicator.getId(), new IndicatorRunner(newAdapter, dataModel));
            
            logger.info("Hot-reloaded indicator: {}", plugin.getName());
            pcs.firePropertyChange("indicatorUpdated", null, newAdapter);
        } else {
            Map<String, Object> defaultSettings = new HashMap<>();
            plugin.getParameters().forEach(p -> defaultSettings.put(p.key(), p.defaultValue()));
            // [MODIFIED] Call the updated addIndicator method.
            addIndicator(new CustomIndicatorAdapter(plugin, defaultSettings), dataModel);
        }

        dataModel.triggerIndicatorRecalculation();
        dataModel.fireDataUpdated();
    }

    public List<Indicator> getIndicators() {
        // [MODIFIED] Extract indicators from the runners.
        return activeIndicators.values().stream()
                .map(IndicatorRunner::getIndicator)
                .collect(Collectors.toList());
    }

    public List<IndicatorContext.DebugLogEntry> getDebugDataFor(UUID indicatorId) {
        return debugData.getOrDefault(indicatorId, Collections.emptyList());
    }

    public void clearAllIndicators() {
        // [MODIFIED] Call onRemoved for all indicators before clearing.
        activeIndicators.values().forEach(IndicatorRunner::onRemoved);
        activeIndicators.clear();
        debugData.clear();
        logger.info("All active indicators have been cleared.");
    }
}