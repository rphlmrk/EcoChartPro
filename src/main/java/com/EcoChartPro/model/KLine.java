package com.EcoChartPro.model;

import com.EcoChartPro.model.chart.AbstractChartData;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single immutable K-Line (candlestick).
 *
 * @param timestamp The exact start time of the k-line period.
 * @param open      The opening price for the period.
 * @param high      The highest price for the period.
 * @param low       The lowest price for the period.
 * @param close     The closing price for the period.
 * @param volume    The trading volume for the period.
 */
public record KLine(
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) implements AbstractChartData {
    @Override
    public Instant startTime() {
        return timestamp;
    }

    @Override
    public Instant endTime() {
        // For a standard KLine, the end time is implicitly defined by its timeframe.
        // We return the start time here as a convention, as the object itself lacks duration info.
        return timestamp;
    }
}