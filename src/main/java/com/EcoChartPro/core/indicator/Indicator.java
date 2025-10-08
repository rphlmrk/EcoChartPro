package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import java.time.Instant;
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
     * [NEW] Overloaded constructor to allow preserving the UUID.
     * This is essential for hot-reloading indicators from the live editor.
     *
     * @param id The existing UUID to assign to this new indicator instance.
     * @param name The name of the indicator.
     * @param settings The initial settings.
     */
    public Indicator(UUID id, String name, Map<String, Object> settings) {
        this.id = id;
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

    /**
     * Searches through the indicator's calculated results to find a displayable
     * value for a specific timestamp.
     * <p>
     * This implementation primarily looks for {@link com.EcoChartPro.api.indicator.drawing.DrawablePolyline}
     * objects, as they are the common representation for line-based indicators like SMA or RSI.
     *
     * @param timestamp The timestamp of the K-line to find the value for.
     * @return A formatted string of the indicator's value at that time, or null if not found.
     */
    public String getValueAsStringAt(Instant timestamp) {
        if (timestamp == null || results.isEmpty()) {
            return null;
        }

        for (DrawableObject drawable : results) {
            if (drawable instanceof com.EcoChartPro.api.indicator.drawing.DrawablePolyline polyline) {
                // Find the specific data point for the given timestamp
                for (com.EcoChartPro.api.indicator.drawing.DataPoint point : polyline.getPoints()) {
                    if (timestamp.equals(point.time())) {
                        // Found the point, format its price (value)
                        // Using a sensible default formatting
                        return point.price().setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
                    }
                }
            }
            // Can add more handlers for other DrawableObject types here if needed
        }

        return null; // No value found for this timestamp
    }
}