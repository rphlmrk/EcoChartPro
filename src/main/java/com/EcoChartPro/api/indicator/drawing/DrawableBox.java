package com.EcoChartPro.api.indicator.drawing;

import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Color;

/**
 * A public API record that describes a rectangle to be drawn on the chart.
 * The box is defined by two diagonally opposite corner points.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param corner1     The first corner of the box, defined by a time and a price.
 * @param corner2     The diagonally opposite corner of the box.
 * @param fillColor   The color to fill the inside of the box. Can be null for no fill.
 * @param strokeColor The color of the box's border. Can be null for no border.
 * @param strokeWidth The width of the box's border.
 */
public record DrawableBox(
    DataPoint corner1,
    DataPoint corner2,
    Color fillColor,
    Color strokeColor,
    float strokeWidth
) implements DrawableObject {}