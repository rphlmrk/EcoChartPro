package com.EcoChartPro.model.chart;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A common interface for all types of chart data points,
 * both time-based (KLine) and non-time-based (Renko, Kagi, etc.).
 */
public interface AbstractChartData {
    /**
     * @return The timestamp marking the beginning of this data point's formation.
     */
    Instant startTime();

    /**
     * @return The timestamp marking the end of this data point's formation.
     */
    Instant endTime();

    /**
     * @return The highest price reached during the formation of this data point.
     */
    BigDecimal high();

    /**
     * @return The lowest price reached during the formation of this data point.
     */
    BigDecimal low();
}