package com.EcoChartPro.data.provider;

import com.EcoChartPro.model.Timeframe;
import java.time.Duration;

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
     * [FIXED] Converts an EcoChartPro Timeframe display name to OKX's 'bar' string format.
     * This method now explicitly maps common timeframes to ensure the correct case and format
     * are used for WebSocket channel subscriptions.
     * @param timeframe The timeframe string from the application (e.g., "5m", "1H").
     * @return The interval string for the OKX API.
     */
    public static String toOkxInterval(String timeframe) {
        // OKX requires specific formats like "1m", "1H", "1D".
        // This mapping ensures that any internal variations (like "h1" vs "1H") are resolved correctly.
        switch (timeframe.toUpperCase()) {
            case "1M": return "1m";
            case "3M": return "3m";
            case "5M": return "5m";
            case "15M": return "15m";
            case "30M": return "30m";
            case "1H": return "1H";
            case "2H": return "2H";
            case "4H": return "4H";
            case "1D": return "1D";
            case "1W": return "1W";
            // Add other officially supported OKX intervals if needed.
            default:
                // Fallback logic for any other custom or unlisted timeframe.
                Timeframe tf = Timeframe.fromString(timeframe);
                if (tf == null) return "1H"; // Default fallback

                Duration d = tf.duration();
                if (d.toDays() > 0) return d.toDays() + "D";
                if (d.toHours() > 0) return d.toHours() + "H";
                if (d.toMinutes() > 0) return d.toMinutes() + "m";

                return "1H"; // Final fallback
        }
    }
}