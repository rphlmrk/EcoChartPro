package com.EcoChartPro.model.chart;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single bar in a Range Bar chart.
 * Each bar has the same high-to-low range.
 */
public record RangeBar(
    Instant startTime,
    Instant endTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) implements AbstractChartData {
}