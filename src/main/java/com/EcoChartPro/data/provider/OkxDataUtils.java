package com.EcoChartPro.data.provider;

import com.EcoChartPro.model.Timeframe;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for converting between EcoChartPro formats and OKX API
 * formats.
 */
public final class OkxDataUtils {

    private static final Logger logger = LoggerFactory.getLogger(OkxDataUtils.class);

    private OkxDataUtils() {
    }

    /**
     * Converts an application symbol (like "btc-usdt-swap") to the OKX API format
     * "BTC-USDT-SWAP".
     * 
     * @param symbol The symbol from the application.
     * @return The symbol in OKX's uppercase format.
     */
    public static String toOkxSymbol(String symbol) {
        return symbol.toUpperCase();
    }

    /**
     * Converts an EcoChartPro Timeframe display name to OKX's 'bar' string format.
     * 
     * @param timeframe The timeframe string from the application (e.g., "5m",
     *                  "1H").
     * @return The interval string for the OKX API.
     */
    public static String toOkxInterval(String timeframe) {
        if (timeframe == null)
            return "1H";

        // 1. Direct Mapping for Standard EcoChartPro strings
        switch (timeframe) {
            case "1m":
                return "1m";
            case "3m":
                return "3m";
            case "5m":
                return "5m";
            case "15m":
                return "15m";
            case "30m":
                return "30m";
            case "1H":
                return "1H";
            case "2H":
                return "2H";
            case "4H":
                return "4H";
            case "6H":
                return "6H";
            case "12H":
                return "12H";
            case "1D":
                return "1D";
            case "2D":
                return "2D";
            case "3D":
                return "3D";
            case "1W":
                return "1W";
            case "1M":
                return "1M"; // Month
        }

        // 2. Fallback / Case-Insensitive Handling
        return switch (timeframe.toUpperCase()) {
            case "1M" -> "1m"; // Assume 1m input means Minute if not matched above
            case "3M" -> "3m";
            case "5M" -> "5m";
            case "15M" -> "15m";
            case "30M" -> "30m";
            case "1H" -> "1H";
            case "2H" -> "2H";
            case "4H" -> "4H";
            case "6H" -> "6H";
            case "12H" -> "12H";
            case "1D" -> "1D";
            case "2D" -> "2D";
            case "3D" -> "3D";
            case "5D" -> "5D";
            case "1W" -> "1W";
            case "1MO" -> "1M"; // Alternate for Month
            case "3MO" -> "3M"; // Alternate for 3 Months
            default -> {
                // 3. Logic for Custom Timeframes not explicitly listed
                Timeframe tf = Timeframe.fromString(timeframe);
                if (tf == null) {
                    logger.warn("Unsupported timeframe '{}' for OKX. Defaulting to 1H.", timeframe);
                    yield "1H";
                }

                Duration d = tf.duration();
                long minutes = d.toMinutes();

                // Try to map custom duration to nearest OKX format
                if (minutes < 60) {
                    yield "1m"; // Fallback to base 1m
                } else if (minutes < 1440) {
                    yield "1H"; // Fallback to base 1H
                } else {
                    // Check for multi-day
                    long days = d.toDays();
                    if (days == 1)
                        yield "1D";
                    if (days == 2)
                        yield "2D";
                    if (days == 3)
                        yield "3D";
                    if (days == 5)
                        yield "5D";
                    if (days == 7)
                        yield "1W";
                    if (days >= 28 && days <= 31)
                        yield "1M";
                    if (days >= 89 && days <= 92)
                        yield "3M";

                    yield "1D"; // Fallback
                }
            }
        };
    }
}