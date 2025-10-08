package com.EcoChartPro.api.indicator;

import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * [NEW ARCHITECTURE]
 * The core public API interface that all custom user-defined indicators must implement.
 */
public interface CustomIndicator {

    String getName();
    IndicatorType getType();
    List<Parameter> getParameters();

    /**
     * The single entry point for all indicator logic. The framework calls this
     * method whenever the chart view changes, providing a context object with all
     * the data needed for the visible area.
     *
     * @param context The context containing k-line data, settings, and MTF data access.
     * @return A list of all drawable objects to be rendered on the chart.
     */
    default List<DrawableObject> calculate(IndicatorContext context) {
        return Collections.emptyList();
    }

    /**
     * [NEW] A lifecycle hook called by the framework *after* the user has changed
     * this indicator's settings in the UI. This provides a crucial opportunity to
     * manage the indicator's state.
     * <p>
     * The primary use case is to invalidate any cached data by calling {@code state.clear()}.
     * This ensures that on the next {@code calculate()} call, the indicator will re-compute
     * its values from scratch using the new settings.
     *
     * @param newSettings The new map of settings that has just been applied.
     * @param state The indicator's persistent state map, which can be cleared or modified here.
     */
    default void onSettingsChanged(Map<String, Object> newSettings, Map<String, Object> state) {
        // Default implementation does nothing, ensuring backward compatibility.
    }

    /**
     * [NEW] A lifecycle hook called by the framework just before the indicator instance
     * is removed from the chart. This allows for any final resource cleanup if necessary.
     *
     * @param state The indicator's persistent state map at the time of removal.
     */
    default void onRemoved(Map<String, Object> state) {
        // Default implementation does nothing, ensuring backward compatibility.
    }
}