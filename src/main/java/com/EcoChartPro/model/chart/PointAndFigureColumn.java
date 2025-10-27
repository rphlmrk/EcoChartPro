package com.EcoChartPro.model.chart;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a single column of X's or O's in a Point and Figure chart.
 */
public record PointAndFigureColumn(
    Instant startTime,
    Instant endTime,
    BigDecimal high,
    BigDecimal low,
    Type type
) implements AbstractChartData {

    public enum Type {
        COLUMN_OF_X, // Rising prices
        COLUMN_OF_O  // Falling prices
    }
}