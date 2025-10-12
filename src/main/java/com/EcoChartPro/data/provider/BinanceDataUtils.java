package com.EcoChartPro.data.provider;

import com.EcoChartPro.model.Timeframe;
import java.time.Duration;

/**
 * A utility class for converting between EcoChartPro formats and Binance API formats.
 */
public final class BinanceDataUtils {

    private BinanceDataUtils() {}

    /**
     * Converts a symbol like "BTC/USDT" to the Binance API format "btcusdt".
     * @param symbol The symbol from the application.
     * @return The symbol in Binance's lowercase, no-slash format.
     */
    public static String toBinanceSymbol(String symbol) {
        return symbol.replace("/", "").toLowerCase();
    }

    /**
     * Converts an EcoChartPro Timeframe record to Binance's interval string format.
     * @param timeframe The Timeframe record.
     * @return The interval string for the Binance API (e.g., "1m", "4h", "1d").
     */
    public static String toBinanceInterval(String timeframe) {
        Timeframe tf = Timeframe.fromString(timeframe);
        if (tf == null) return "1h"; // Fallback

        Duration d = tf.duration();
        if (d.toDays() > 0) return d.toDays() + "d";
        if (d.toHours() > 0) return d.toHours() + "h";
        if (d.toMinutes() > 0) return d.toMinutes() + "m";
        
        return "1h"; // Default fallback
    }
}