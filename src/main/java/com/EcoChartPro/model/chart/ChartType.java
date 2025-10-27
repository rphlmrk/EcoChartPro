package com.EcoChartPro.model.chart;

/**
 * An enum representing the different types of chart visualizations available.
 */
public enum ChartType {
    CANDLES("Candles", true),
    BARS("Bars", true),
    HOLLOW_CANDLES("Hollow Candles", true),
    LINE("Line", true),
    LINE_WITH_MARKERS("Line with Markers", true),
    AREA("Area", true),
    VOLUME_CANDLES("Volume Candles", true),
    HEIKIN_ASHI("Heikin Ashi", true),
    // New Non-Time-Based Chart Types
    RENKO("Renko", false),
    RANGE_BARS("Range Bars", false),
    KAGI("Kagi", false),
    POINT_AND_FIGURE("Point & Figure", false);

    private final String displayName;
    private final boolean isTimeBased;

    ChartType(String displayName, boolean isTimeBased) {
        this.displayName = displayName;
        this.isTimeBased = isTimeBased;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTimeBased() {
        return isTimeBased;
    }

    @Override
    public String toString() {
        return displayName;
    }
}