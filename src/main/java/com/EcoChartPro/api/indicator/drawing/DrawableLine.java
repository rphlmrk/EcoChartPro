package com.EcoChartPro.api.indicator.drawing;

import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Color;

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
) implements DrawableObject {}