package com.EcoChartPro.model;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a chart timeframe with an associated duration.
 * This has been converted from an enum to a record to support custom, user-defined timeframes.
 */
public record Timeframe(String displayName, Duration duration) {

    // Predefined standard instances for convenience and backward compatibility
    public static final Timeframe M1 = new Timeframe("1m", Duration.ofMinutes(1));
    public static final Timeframe M5 = new Timeframe("5m", Duration.ofMinutes(5));
    public static final Timeframe M15 = new Timeframe("15m", Duration.ofMinutes(15));
    public static final Timeframe M30 = new Timeframe("30m", Duration.ofMinutes(30));
    public static final Timeframe H1 = new Timeframe("1H", Duration.ofHours(1));
    public static final Timeframe H2 = new Timeframe("2H", Duration.ofHours(2));
    public static final Timeframe H4 = new Timeframe("4H", Duration.ofHours(4));
    public static final Timeframe D1 = new Timeframe("1D", Duration.ofDays(1));

    private static final List<Timeframe> STANDARD_TIMEFRAMES = List.of(M1, M5, M15, M30, H1, H2, H4, D1);
    private static final Pattern TIMEFRAME_PATTERN = Pattern.compile("(\\d+)([mhd])", Pattern.CASE_INSENSITIVE);

    @Override
    public String toString() {
        return this.displayName;
    }
    
    public static Optional<Timeframe> of(int value, char unit) {
        if (value <= 0) {
            return Optional.empty();
        }
        String name = "" + value + (Character.toLowerCase(unit) == 'm' ? 'm' : Character.toUpperCase(unit));
        Duration d = switch (Character.toLowerCase(unit)) {
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            case 'd' -> Duration.ofDays(value);
            default -> null;
        };
        return d == null ? Optional.empty() : Optional.of(new Timeframe(name, d));
    }
    
    public static List<Timeframe> getStandardTimeframes() {
        return STANDARD_TIMEFRAMES;
    }

    /**
     * [MODIFIED] Parses a string to find a matching standard timeframe or create a custom one.
     * This now includes a fallback to check for old enum-style names for backward compatibility.
     * @param text The string representation (e.g., "5m", "H1", or the old "M1").
     * @return The corresponding Timeframe object, or null if parsing fails.
     */
    public static Timeframe fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        // First, check for a direct match against the displayName (e.g., "5m", "1H").
        for (Timeframe tf : STANDARD_TIMEFRAMES) {
            if (tf.displayName.equalsIgnoreCase(text)) {
                return tf;
            }
        }
        
        // Add a fallback for old enum names (e.g., "M1", "H4") for backward compatibility.
        switch (text.toUpperCase()) {
            case "M1": return M1;
            case "M5": return M5;
            case "M15": return M15;
            case "M30": return M30;
            case "H1": return H1;
            case "H2": return H2;
            case "H4": return H4;
            case "D1": return D1;
        }

        // If still not found, try to parse it as a custom timeframe (e.g., "7m", "2h").
        Matcher matcher = TIMEFRAME_PATTERN.matcher(text);
        if (matcher.matches()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                char unit = matcher.group(2).charAt(0);
                return of(value, unit).orElse(null);
            } catch (NumberFormatException e) {
                // Should not happen with this regex, but good practice.
                return null;
            }
        }
        
        return null;
    }
}