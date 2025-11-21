package com.EcoChartPro.data;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A utility class for performing high-performance K-line data resampling.
 * This serves as the "Ticker Engine" logic, aggregating lower-timeframe data
 * into higher timeframe representations.
 */
public class DataResampler {

    /**
     * Resamples a list of source K-lines (usually 1m) into a target timeframe.
     * This method is optimized to process the data in a single pass.
     *
     * @param sourceData      A list of K-lines (e.g., 1m bars), sorted chronologically.
     * @param targetTimeframe The timeframe to aggregate the data into (e.g., M5, H1, D1).
     * @return A new list of K-lines aggregated to the target timeframe.
     */
    public static List<KLine> resample(List<KLine> sourceData, Timeframe targetTimeframe) {
        if (sourceData == null || sourceData.isEmpty()) {
            return Collections.emptyList();
        }
        if (targetTimeframe == null) {
            return new ArrayList<>(sourceData);
        }

        // If the target is the same as the source duration (assuming source is M1),
        // we can return a copy. 
        // Note: A more robust check would compare durations, but this catches the common case.
        if (targetTimeframe == Timeframe.M1) {
            return new ArrayList<>(sourceData);
        }

        List<KLine> resampledKLines = new ArrayList<>();
        KLine currentlyFormingCandle = null;

        for (KLine bar : sourceData) {
            Instant barTimestamp = bar.timestamp();
            Instant intervalStart = getIntervalStart(barTimestamp, targetTimeframe);

            if (currentlyFormingCandle == null) {
                // Initialize the first bucket
                currentlyFormingCandle = new KLine(
                    intervalStart, 
                    bar.open(), 
                    bar.high(), 
                    bar.low(), 
                    bar.close(), 
                    bar.volume()
                );
            } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
                // The incoming bar belongs to a NEW interval. 
                // Finalize the previous one and start a new one.
                resampledKLines.add(currentlyFormingCandle);
                currentlyFormingCandle = new KLine(
                    intervalStart, 
                    bar.open(), 
                    bar.high(), 
                    bar.low(), 
                    bar.close(), 
                    bar.volume()
                );
            } else {
                // The incoming bar is within the current forming interval. Aggregate it.
                currentlyFormingCandle = aggregate(currentlyFormingCandle, bar);
            }
        }

        // Add the final forming candle (which might be incomplete/live)
        if (currentlyFormingCandle != null) {
            resampledKLines.add(currentlyFormingCandle);
        }

        return resampledKLines;
    }

    /**
     * Merges a new tick (or sub-bar) into an existing cumulative bar.
     * This logic encapsulates the OHLCV merging rules.
     *
     * @param current The existing aggregated bar.
     * @param tick    The new data to merge into it.
     * @return A new KLine representing the merged state.
     */
    public static KLine aggregate(KLine current, KLine tick) {
        return new KLine(
            current.timestamp(),                        // Timestamp remains the bucket start
            current.open(),                             // Open price never changes
            current.high().max(tick.high()),            // Max of existing high and new high
            current.low().min(tick.low()),              // Min of existing low and new low
            tick.close(),                               // Close is always the latest tick's close
            current.volume().add(tick.volume())         // Volume is accumulated
        );
    }

    /**
     * Calculates the start time of the interval a given timestamp belongs to.
     * Examples for M5: 
     * 10:03 -> 10:00
     * 10:05 -> 10:05
     *
     * @param timestamp The timestamp to check.
     * @param timeframe The target timeframe.
     * @return The exact start time of the interval.
     */
    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp;
        
        long epochMillis = timestamp.toEpochMilli();
        long remainder = epochMillis % durationMillis;
        
        return Instant.ofEpochMilli(epochMillis - remainder);
    }
}