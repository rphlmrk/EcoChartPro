package com.EcoChartPro.model;

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
) {}