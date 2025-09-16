package com.EcoChartPro.core.manager;

import java.time.Instant;

/**
 * A simple record to represent a range of time.
 * Used for defining the visible time-axis of a chart.
 *
 * @param start The start time of the range (inclusive).
 * @param end   The end time of the range (inclusive).
 */
public record TimeRange(
    Instant start,
    Instant end
) {
    /**
     * Checks if a given Instant is within this time range.
     * @param time The Instant to check.
     * @return true if the time is within the range, false otherwise.
     */
    public boolean contains(Instant time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }
}