package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The abstract base class for all technical indicators.
 * This new design uses a single, powerful `calculate` method that receives
 * all necessary context on-demand.
 */
public abstract class Indicator {

    protected final UUID id;
    protected final String name;
    protected Map<String, Object> settings;
    protected final List<DrawableObject> results;

    public Indicator(String name, Map<String, Object> settings) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.settings = settings;
        this.results = new ArrayList<>();
    }

    /**
     * The single entry point for all indicator logic.
     * The framework calls this method whenever the chart view changes, providing a
     * context object with all the data needed for the visible area.
     *
     * @param context The context containing k-line data, settings, and MTF data access.
     * @return A list of all drawable objects to be rendered on the chart.
     */
    public abstract List<DrawableObject> calculate(IndicatorContext context);

    public void reset() {
        this.results.clear();
    }

    public abstract IndicatorType getType();

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Map<String, Object> getSettings() { return settings; }
    public List<DrawableObject> getResults() { return results; }

    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }
}