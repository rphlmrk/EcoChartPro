package com.EcoChartPro.api.indicator.drawing;

/**
 * A public API enum that defines how a {@link DrawableText} object should be
 * positioned relative to its {@link DataPoint}.
 * This is part of the stable API for custom indicator plugins.
 */
public enum TextAnchor {
    /** Position the top-left corner of the text at the data point. */
    TOP_LEFT,
    /** Position the top-center of the text at the data point. */
    TOP_CENTER,
    /** Position the top-right corner of the text at the data point. */
    TOP_RIGHT,
    /** Position the middle of the left edge of the text at the data point. */
    CENTER_LEFT,
    /** Center the text exactly on the data point. */
    CENTER,
    /** Position the middle of the right edge of the text at the data point. */
    CENTER_RIGHT,
    /** Position the bottom-left corner of the text at the data point. */
    BOTTOM_LEFT,
    /** Position the bottom-center of the text at the data point. */
    BOTTOM_CENTER,
    /** Position the bottom-right corner of the text at the data point. */
    BOTTOM_RIGHT
}