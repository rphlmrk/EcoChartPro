package com.EcoChartPro.model;

/**
 * Represents a trading instrument, identified by its name.
 *
 * @param name The unique identifier for the symbol (e.g., "BTC/USDT", "AAPL").
 */
public record Symbol(String name) {}