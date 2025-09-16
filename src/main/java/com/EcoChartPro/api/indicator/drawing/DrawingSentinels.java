package com.EcoChartPro.api.indicator.drawing;

import java.time.Instant;

/**
 * A public API class containing sentinel values for special rendering modes.
 * Indicators should use these constants to create DataPoints that the
 * internal rendering engine will interpret in a special way.
 */
public final class DrawingSentinels {

    private DrawingSentinels() {} // Prevent instantiation

    /**
     * Use this Instant's epochSecond in a DataPoint to signal that the nano field
     * should be interpreted as a pixel offset from the RIGHT EDGE of the chart pane.
     */
    public static final Instant RIGHT_EDGE_PIXEL_SENTINEL = Instant.ofEpochSecond(-1337);

    /**
     * Use this Instant's epochSecond in a DataPoint to signal that the nano field
     * should be interpreted as an ANCHOR TIME (in epoch seconds), and the price field
     * should be interpreted as a PIXEL OFFSET from that anchor time's X-coordinate.
     */
    public static final Instant TIME_ANCHORED_PIXEL_SENTINEL = Instant.ofEpochSecond(-1339);
}