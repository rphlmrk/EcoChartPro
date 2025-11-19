package com.EcoChartPro.data.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class for converting between EcoChartPro formats and Binance API
 * formats.
 */
public final class BinanceDataUtils {

    private static final Logger logger = LoggerFactory.getLogger(BinanceDataUtils.class);

    private BinanceDataUtils() {
    }

    public static String toBinanceSymbol(String symbol) {
        return symbol.replace("/", "").toLowerCase();
    }

    public static String toBinanceInterval(String timeframe) {
        if (timeframe == null) {
            return "1h";
        }

        return switch (timeframe) {
            case "1m" -> "1m";
            case "3m" -> "3m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1H" -> "1h";
            case "2H" -> "2h";
            case "4H" -> "4h";
            case "6H" -> "6h";
            case "8H" -> "8h";
            case "12H" -> "12h";
            case "1D" -> "1d";
            case "3D" -> "3d";
            case "1W" -> "1w";
            case "1M" -> "1M";
            default -> {
                logger.warn("Unsupported/Custom timeframe {} requested from Binance. Falling back to 1h.", timeframe);
                yield "1h";
            }
        };
    }
}