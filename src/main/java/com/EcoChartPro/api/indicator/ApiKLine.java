package com.EcoChartPro.api.indicator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A public API record representing a single immutable K-Line (candlestick).
 * This is part of the stable API for custom indicator plugins.
 *
 * @param timestamp The exact start time of the k-line period.
 * @param open      The opening price for the period.
 * @param high      The highest price for the period.
 * @param low       The lowest price for the period.
 * @param close     The closing price for the period.
 * @param volume    The trading volume for the period.
 */
public record ApiKLine(
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}