package com.EcoChartPro.core.manager;

import java.math.BigDecimal;

/**
 * A simple record to represent a range of prices.
 * Used for defining the visible price-axis of a chart.
 *
 * @param min The minimum price of the range (inclusive).
 * @param max The maximum price of the range (inclusive).
 */
public record PriceRange(
    BigDecimal min,
    BigDecimal max
) {
    /**
     * Checks if a given price is within this price range.
     * @param price The BigDecimal price to check.
     * @return true if the price is within the range, false otherwise.
     */
    public boolean contains(BigDecimal price) {
        return price.compareTo(min) >= 0 && price.compareTo(max) <= 0;
    }
}