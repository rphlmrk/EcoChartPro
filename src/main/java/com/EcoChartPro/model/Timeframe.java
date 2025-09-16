package com.EcoChartPro.model;

import java.time.Duration;
import java.util.Arrays;

/**
 * Represents a standardized chart timeframe with an associated duration.
 */
public enum Timeframe {
    M1("1m", Duration.ofMinutes(1)),
    M5("5m", Duration.ofMinutes(5)),
    M15("15m", Duration.ofMinutes(15)),
    M30("30m", Duration.ofMinutes(30)),
    H1("1H", Duration.ofHours(1)),
    H4("4H", Duration.ofHours(4)),
    D1("1D", Duration.ofDays(1));

    private final String displayName;
    private final Duration duration;

    Timeframe(String displayName, Duration duration) {
        this.displayName = displayName;
        this.duration = duration;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Duration getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return this.displayName;
    }

    public static Timeframe fromString(String text) {
        return Arrays.stream(values())
                .filter(tf -> tf.displayName.equalsIgnoreCase(text))
                .findFirst()
                .orElse(null);
    }
}