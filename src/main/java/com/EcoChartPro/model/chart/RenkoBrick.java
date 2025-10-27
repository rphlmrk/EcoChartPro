package com.EcoChartPro.model.chart;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single brick in a Renko chart.
 * A brick is either bullish (close > open) or bearish (close < open).
 */
public record RenkoBrick(
    Instant startTime,
    Instant endTime,
    BigDecimal open,
    BigDecimal close
) implements AbstractChartData {

    public enum Type {
        UP,
        DOWN
    }

    public Type getType() {
        return close.compareTo(open) > 0 ? Type.UP : Type.DOWN;
    }

    @Override
    public BigDecimal high() {
        return open.max(close);
    }

    @Override
    public BigDecimal low() {
        return open.min(close);
    }
}