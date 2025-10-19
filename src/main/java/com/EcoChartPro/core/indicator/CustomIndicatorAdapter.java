package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An adapter that wraps a public {@link CustomIndicator} plugin and makes it
 * compatible with the new contextual calculation engine.
 */
public class CustomIndicatorAdapter extends Indicator {

    private final CustomIndicator plugin;

    public CustomIndicatorAdapter(CustomIndicator plugin, Map<String, Object> settings) {
        super(plugin.getName(), settings);
        this.plugin = plugin;
    }

    /**
     * [NEW] Overloaded constructor to support hot-reloading by preserving the indicator's ID.
     */
    public CustomIndicatorAdapter(UUID id, CustomIndicator plugin, Map<String, Object> settings) {
        super(id, plugin.getName(), settings);
        this.plugin = plugin;
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        // Bridges the core engine and the public API by calling the plugin's method.
        return plugin.calculate(context);
    }

    @Override
    public IndicatorType getType() {
        return plugin.getType();
    }

    public CustomIndicator getPlugin() {
        return plugin;
    }
}