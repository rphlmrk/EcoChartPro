package com.EcoChartPro.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single executed trade (tick data).
 *
 * @param timestamp The exact time of the trade.
 * @param price The price at which the trade occurred.
 * @param quantity The amount traded.
 * @param side The side of the aggressive party (taker), e.g., "buy" or "sell".
 */
public record TradeTick(Instant timestamp, BigDecimal price, BigDecimal quantity, String side) {
}