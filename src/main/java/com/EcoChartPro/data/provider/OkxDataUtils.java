package com.EcoChartPro.data.provider;

import com.EcoChartPro.model.Timeframe;

/**
 * A utility class for converting between EcoChartPro formats and OKX API formats.
 */
public final class OkxDataUtils {

    private OkxDataUtils() {}

    /**
     * Converts an application symbol (like "btc-usdt-swap") to the OKX API format "BTC-USDT-SWAP".
     * @param symbol The symbol from the application.
     * @return The symbol in OKX's uppercase format.
     */
    public static String toOkxSymbol(String symbol) {
        return symbol.toUpperCase();
    }

    /**
     * Converts an EcoChartPro Timeframe display name to OKX's 'bar' string format.
     * @param timeframe The timeframe string from the application (e.g., "5m", "1H").
     * @return The interval string for the OKX API.
     */
    public static String toOkxInterval(String timeframe) {
        Timeframe tf = Timeframe.fromString(timeframe);
        if (tf == null) {
            return "1H"; // Fallback
        }
        // OKX format (e.g., "1m", "1H", "1D") matches our displayName format.
        return tf.displayName();
    }
}