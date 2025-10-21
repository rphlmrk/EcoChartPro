package com.EcoChartPro.api.indicator.drawing;

import java.awt.Color;
import java.math.BigDecimal;

/**
 * A public API record that describes a single line segment to be drawn on the chart.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param start       The starting point of the line.
 * @param end         The ending point of the line.
 * @param color       The color of the line.
 * @param strokeWidth The width of the line.
 */
public record DrawableLine(
    DataPoint start,
    DataPoint end,
    Color color,
    float strokeWidth
) implements DrawableObject {

    /**
     * [NEW] A public API record that describes a horizontal line to be drawn across the chart at a specific price level.
     *
     * @param price       The price level for the horizontal line.
     * @param color       The color of the line.
     * @param strokeWidth The width of the line.
     * @param isDashed    If true, the line will be rendered with a dashed pattern.
     */
    public record Horizontal(
        BigDecimal price,
        Color color,
        float strokeWidth,
        boolean isDashed
    ) implements DrawableObject {}
}