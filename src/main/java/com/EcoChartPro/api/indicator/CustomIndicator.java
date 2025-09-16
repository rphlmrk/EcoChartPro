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
}