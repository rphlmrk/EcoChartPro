package com.EcoChartPro.model.drawing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a point on the chart in data coordinates.
 * It stores both time/price and an optional index for non-time-based charts.
 *
 * @param timestamp The exact time coordinate of the point.
 * @param price     The exact price coordinate of the point.
 * @param index     The optional absolute index of the data point on the x-axis.
 */
public record DrawingObjectPoint(
    Instant timestamp,
    BigDecimal price,
    Integer index
) {
    // Convenience constructor for time-based creation
    public DrawingObjectPoint(Instant timestamp, BigDecimal price) {
        this(timestamp, price, null);
    }

    // Jackson constructor for robust deserialization
    @JsonCreator
    public DrawingObjectPoint(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("index") Integer index
    ) {
        this.timestamp = timestamp;
        this.price = price;
        this.index = index;
    }
}