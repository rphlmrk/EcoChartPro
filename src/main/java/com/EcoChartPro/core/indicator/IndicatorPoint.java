package com.EcoChartPro.core.indicator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single calculated data point for an indicator at a specific time.
 * A point can hold multiple values, accommodating multi-line indicators
 * (e.g., Bollinger Bands with upper, middle, and lower bands).
 *
 * @param time The timestamp corresponding to the K-line this point was calculated from.
 * @param values The calculated value(s) for the indicator. For an SMA, this will have one value.
 *               For Bollinger Bands, it might have three (upper, middle, lower).
 */
public record IndicatorPoint(Instant time, BigDecimal... values) {}