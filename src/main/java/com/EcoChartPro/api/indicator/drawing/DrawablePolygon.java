package com.EcoChartPro.api.indicator.drawing;

import java.awt.Color;
import java.util.List;

/**
 * A public API record that describes a closed, filled polygon to be drawn on the chart.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param vertices    A list of DataPoints representing the corners of the polygon.
 * @param fillColor   The color to fill the polygon with. Can be null for no fill.
 * @param strokeColor The color of the polygon's border. Can be null for no border.
 * @param strokeWidth The width of the polygon's border.
 */
public record DrawablePolygon(
    List<DataPoint> vertices,
    Color fillColor,
    Color strokeColor,
    float strokeWidth
) implements DrawableObject {}