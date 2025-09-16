package com.EcoChartPro.api.indicator.drawing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A public API record that represents a single point.
 * The `time` field can be used in one of three ways:
 * 1. Standard Mode: A normal Instant representing a point in time.
 * 2. Right-Edge Pixel Offset Mode: A sentinel value in epoch seconds, with the nano field
 *    holding a pixel offset from the right edge of the chart.
 * 3. Time-Anchored Pixel Offset Mode: The epoch second of an anchor time, with the nano
 *    field holding a pixel offset from that anchor time's on-screen X coordinate.
 *
 * @param time  The time or anchor/offset data.
 * @param price The price (Y-coordinate).
 */
public record DataPoint(
    Instant time,
    BigDecimal price
) implements DrawableObject {
    public DataPoint withPrice(BigDecimal newPrice) {
        return new DataPoint(this.time, newPrice);
    }
}