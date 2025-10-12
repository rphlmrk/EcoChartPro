package com.EcoChartPro.model;

/**
 * A generic, standardized record for representing a trading symbol.
 * This ensures that symbols from different data providers (files, Binance, etc.)
 * are handled consistently throughout the application.
 *
 * @param symbol      The unique identifier for the symbol, typically in a format like "BTC/USDT".
 * @param description A user-friendly description of the symbol.
 * @param assetClass  The category of the asset (e.g., "Crypto", "Stock", "Forex").
 * @param exchange    The exchange or data source where this symbol is traded.
 */
public record SymbolInfo(
    String symbol,
    String description,
    String assetClass,
    String exchange
) {}