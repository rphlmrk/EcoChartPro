package com.EcoChartPro.model.drawing;

import java.awt.Color;

/**
 * A dedicated record to hold the specific styling properties for a TextObject.
 *
 * @param showBackground If true, a background color is rendered.
 * @param backgroundColor The color of the background.
 * @param showBorder If true, a border is rendered.
 * @param borderColor The color of the border.
 * @param wrapText If true, text will wrap at a predefined width.
 * @param screenAnchored If true, the text is anchored to a fixed screen position, not data coordinates.
 */
public record TextProperties(
    boolean showBackground,
    Color backgroundColor,
    boolean showBorder,
    Color borderColor,
    boolean wrapText,
    boolean screenAnchored
) {}