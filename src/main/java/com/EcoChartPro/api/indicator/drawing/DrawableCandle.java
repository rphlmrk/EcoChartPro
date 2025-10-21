package com.EcoChartPro.api.indicator.drawing;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A public API record that describes a single candlestick to be drawn on the chart.
 * This is useful for indicators that overlay data from other timeframes or instruments.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param timestamp The start time of the candle period.
 * @param open      The opening price.
 * @param high      The highest price.
 * @param low       The lowest price.
 * @param close     The closing price.
 * @param bodyColor The color for the candle's body (the rectangle between open and close).
 * @param wickColor The color for the candle's wicks (the lines extending to high and low).
 */
public record DrawableCandle(
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    Color bodyColor,
    Color wickColor
) implements DrawableObject {}