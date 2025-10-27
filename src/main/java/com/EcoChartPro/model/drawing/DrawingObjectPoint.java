package com.EcoChartPro.model.drawing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a point on the chart in data coordinates (time and price).
 *
 * @param timestamp The exact time coordinate of the point.
 * @param price     The exact price coordinate of the point.
 */
public record DrawingObjectPoint(
    Instant timestamp,
    BigDecimal price
) {}