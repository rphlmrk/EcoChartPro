package com.EcoChartPro.model;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a chart timeframe with an associated duration.
 * Converted from enum to record to support custom user-defined timeframes.
 */
public record Timeframe(String displayName, Duration duration) {

    // --- Standard Timeframes (Binance/OKX Supersets) ---

    // Minutes
    public static final Timeframe M1 = new Timeframe("1m", Duration.ofMinutes(1));
    public static final Timeframe M3 = new Timeframe("3m", Duration.ofMinutes(3));
    public static final Timeframe M5 = new Timeframe("5m", Duration.ofMinutes(5));
    public static final Timeframe M15 = new Timeframe("15m", Duration.ofMinutes(15));
    public static final Timeframe M30 = new Timeframe("30m", Duration.ofMinutes(30));

    // Hours
    public static final Timeframe H1 = new Timeframe("1H", Duration.ofHours(1));
    public static final Timeframe H2 = new Timeframe("2H", Duration.ofHours(2));
    public static final Timeframe H4 = new Timeframe("4H", Duration.ofHours(4));
    public static final Timeframe H6 = new Timeframe("6H", Duration.ofHours(6));
    public static final Timeframe H8 = new Timeframe("8H", Duration.ofHours(8));
    public static final Timeframe H12 = new Timeframe("12H", Duration.ofHours(12));

    // Days
    public static final Timeframe D1 = new Timeframe("1D", Duration.ofDays(1));
    public static final Timeframe D3 = new Timeframe("3D", Duration.ofDays(3));

    // Weeks & Months
    public static final Timeframe W1 = new Timeframe("1W", Duration.ofDays(7));
    public static final Timeframe MO1 = new Timeframe("1M", Duration.ofDays(30)); // Approx

    // Comprehensive list for UI and Logic checks
    private static final List<Timeframe> STANDARD_TIMEFRAMES = List.of(
            M1, M3, M5, M15, M30,
            H1, H2, H4, H6, H8, H12,
            D1, D3, W1, MO1);

    // Updated regex to support m, h, d, w, M (case sensitive for m/M distinction)
    private static final Pattern TIMEFRAME_PATTERN = Pattern.compile("(\\d+)([mhdwM])");

    @Override
    public String toString() {
        return this.displayName;
    }

    public static Optional<Timeframe> of(int value, char unit) {
        if (value <= 0)
            return Optional.empty();

        String suffix;
        Duration d;

        switch (unit) { // Case sensitive switch
            case 'm':
                d = Duration.ofMinutes(value);
                suffix = "m";
                break;
            case 'H':
            case 'h':
                d = Duration.ofHours(value);
                suffix = "H";
                break;
            case 'D':
            case 'd':
                d = Duration.ofDays(value);
                suffix = "D";
                break;
            case 'W':
            case 'w':
                d = Duration.ofDays(value * 7L);
                suffix = "W";
                break;
            case 'M':
                d = Duration.ofDays(value * 30L);
                suffix = "M";
                break;
            default:
                return Optional.empty();
        }

        String name = value + suffix;
        return Optional.of(new Timeframe(name, d));
    }

    public static List<Timeframe> getStandardTimeframes() {
        return STANDARD_TIMEFRAMES;
    }

    /**
     * Determines the most efficient standard base timeframe required to construct
     * the given target timeframe via resampling.
     */
    public static Timeframe getSmartBaseTimeframe(Timeframe target) {
        if (STANDARD_TIMEFRAMES.contains(target)) {
            return target;
        }

        long minutes = target.duration().toMinutes();

        // 1. Sub-Hour Optimization (< 60m)
        if (minutes < 60) {
            if (minutes % 15 == 0)
                return M15;
            if (minutes % 5 == 0)
                return M5;
            if (minutes % 3 == 0)
                return M3;
            return M1;
        }

        // 2. Sub-Day Optimization (< 24h)
        else if (minutes < 1440) {
            if (minutes % 720 == 0)
                return H12;
            if (minutes % 480 == 0)
                return H8;
            if (minutes % 360 == 0)
                return H6;
            if (minutes % 240 == 0)
                return H4;
            if (minutes % 120 == 0)
                return H2;
            return H1;
        }

        // 3. Daily+ Optimization
        else {
            return D1;
        }
    }

    public static Timeframe fromString(String text) {
        if (text == null || text.isBlank())
            return null;

        for (Timeframe tf : STANDARD_TIMEFRAMES) {
            if (tf.displayName.equals(text))
                return tf;
        }

        for (Timeframe tf : STANDARD_TIMEFRAMES) {
            if (tf.displayName.equalsIgnoreCase(text))
                return tf;
        }

        Matcher matcher = TIMEFRAME_PATTERN.matcher(text);
        if (matcher.matches()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                char unit = matcher.group(2).charAt(0);
                return of(value, unit).orElse(null);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}