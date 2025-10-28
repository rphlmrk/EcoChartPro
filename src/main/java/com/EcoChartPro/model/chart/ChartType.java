package com.EcoChartPro.model.chart;

/**
 * An enum representing the different types of chart visualizations available.
 */
public enum ChartType {
    CANDLES("Candles"),
    BARS("Bars"),
    HOLLOW_CANDLES("Hollow Candles"),
    LINE("Line"),
    LINE_WITH_MARKERS("Line with Markers"),
    AREA("Area"),
    VOLUME_CANDLES("Volume Candles"),
    HEIKIN_ASHI("Heikin Ashi"),
    FOOTPRINT("Footprint");

    private final String displayName;

    ChartType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}