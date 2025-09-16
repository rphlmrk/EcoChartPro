package com.EcoChartPro.api.indicator.drawing;

import java.awt.Color;
import java.awt.Font;

/**
 * A public API record that describes a text label to be drawn on the chart.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param position The {@link DataPoint} (time and price) where the text is anchored.
 * @param text     The string of text to display.
 * @param font     The font to use for the text.
 * @param color    The color of the text.
 * @param anchor   The {@link TextAnchor} that defines how the text is aligned relative to its position.
 */
public record DrawableText(
    DataPoint position,
    String text,
    Font font,
    Color color,
    TextAnchor anchor
) implements DrawableObject {}