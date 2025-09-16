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

public final class IndicatorManager {
    private static final Logger logger = LoggerFactory.getLogger(IndicatorManager.class);
    private final Map<UUID, Indicator> activeIndicators = new ConcurrentHashMap<>();
    private final Map<UUID, List<IndicatorContext.DebugLogEntry>> debugData = new ConcurrentHashMap<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void addIndicator(Indicator indicator) {
        if (indicator != null) {
            activeIndicators.put(indicator.getId(), indicator);
            logger.info("Added indicator: {} with settings {}", indicator.getName(), indicator.getSettings());
            pcs.firePropertyChange("indicatorAdded", null, indicator);
        }
    }

    public void removeIndicator(UUID id) {
        Indicator removed = activeIndicators.remove(id);
        debugData.remove(id);
        if (removed != null) {
            logger.info("Removed indicator: {} (ID: {})", removed.getName(), id);
            pcs.firePropertyChange("indicatorRemoved", null, removed.getId());
        }
    }

    public void updateIndicatorSettings(UUID id, Map<String, Object> newSettings) {
        Indicator indicator = activeIndicators.get(id);
        if (indicator != null) {
            indicator.setSettings(newSettings);
            logger.info("Updating settings for indicator: {} (ID: {}). New settings: {}", indicator.getName(), id, newSettings);
            pcs.firePropertyChange("indicatorUpdated", null, indicator);
        }
    }

    public void recalculateAll(ChartDataModel dataModel, List<KLine> dataSlice) {
        for (Indicator indicator : activeIndicators.values()) {
            IndicatorRunner runner = new IndicatorRunner(indicator, dataModel);
            CalculationResult result = runner.recalculate(dataSlice);
            indicator.results.clear();
            indicator.results.addAll(result.drawables());

            debugData.put(indicator.getId(), result.debugLogs());
            pcs.firePropertyChange("debugDataUpdated", indicator.getId(), result.debugLogs());
        }
    }

    public void addOrUpdateFromLiveCode(CustomIndicator plugin, ChartDataModel dataModel) {
        Optional<Indicator> existing = activeIndicators.values().stream()
                .filter(i -> i instanceof CustomIndicatorAdapter && ((CustomIndicatorAdapter) i).getPlugin().getName().equals(plugin.getName()))
                .findFirst();

        if (existing.isPresent()) {
            Indicator existingIndicator = existing.get();
            activeIndicators.put(existingIndicator.getId(), new CustomIndicatorAdapter(plugin, existingIndicator.getSettings()));
            logger.info("Hot-reloaded indicator: {}", plugin.getName());
            pcs.firePropertyChange("indicatorUpdated", null, existingIndicator);
        } else {
            Map<String, Object> defaultSettings = new HashMap<>();
            plugin.getParameters().forEach(p -> defaultSettings.put(p.key(), p.defaultValue()));
            addIndicator(new CustomIndicatorAdapter(plugin, defaultSettings));
        }

        dataModel.triggerIndicatorRecalculation();
        dataModel.fireDataUpdated();
    }

    public List<Indicator> getIndicators() {
        return new ArrayList<>(activeIndicators.values());
    }

    public List<IndicatorContext.DebugLogEntry> getDebugDataFor(UUID indicatorId) {
        return debugData.getOrDefault(indicatorId, Collections.emptyList());
    }

    public void clearAllIndicators() {
        activeIndicators.clear();
        debugData.clear();
        logger.info("All active indicators have been cleared.");
    }
}