package com.EcoChartPro.model.chart;

import java.math.BigDecimal;
import java.time.Instant;

public record KagiLine(
    Instant startTime,
    Instant endTime,
    BigDecimal open,
    BigDecimal close,
    Type type
) implements AbstractChartData {

    public enum Type {
        YANG, // Thick, rising line
        YIN   // Thin, falling line
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