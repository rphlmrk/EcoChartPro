package com.EcoChartPro.api.indicator;

/**
 * Defines whether an indicator is drawn on top of the main price chart (OVERLAY)
 * or in its own dedicated pane below it (PANE).
 * This is part of the stable API for custom indicator plugins.
 */
public enum IndicatorType {
    /**
     * The indicator is drawn on top of the price chart (e.g., Moving Averages, Bollinger Bands).
     */
    OVERLAY,

    /**
     * The indicator is drawn in its own separate pane with its own Y-axis (e.g., RSI, MACD).
     */
    PANE
}