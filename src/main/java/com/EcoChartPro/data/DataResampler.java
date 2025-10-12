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
 * This class contains the logic to aggregate lower-timeframe data into a higher timeframe.
 */
public class DataResampler {

    /**
     * Resamples a list of 1-minute K-lines into a target timeframe.
     * This method is highly optimized to process the data in a single pass.
     *
     * @param oneMinuteData   A list of 1-minute K-lines, sorted chronologically.
     * @param targetTimeframe The timeframe to aggregate the data into (e.g., M5, H1, D1).
     * @return A new list of K-lines aggregated to the target timeframe.
     */
    public static List<KLine> resample(List<KLine> oneMinuteData, Timeframe targetTimeframe) {
        if (oneMinuteData == null || oneMinuteData.isEmpty() || targetTimeframe == null) {
            return Collections.emptyList();
        }

        // If the target is M1, no resampling is needed. Return a copy of the original list.
        if (targetTimeframe == Timeframe.M1) {
            return new ArrayList<>(oneMinuteData);
        }

        List<KLine> resampledKLines = new ArrayList<>();
        KLine currentlyFormingCandle = null;

        for (KLine m1Bar : oneMinuteData) {
            Instant m1Timestamp = m1Bar.timestamp();
            Instant intervalStart = getIntervalStart(m1Timestamp, targetTimeframe);

            if (currentlyFormingCandle == null) {
                // This is the first bar in a new, empty aggregation period.
                currentlyFormingCandle = new KLine(intervalStart, m1Bar.open(), m1Bar.high(), m1Bar.low(), m1Bar.close(), m1Bar.volume());
            } else if (!currentlyFormingCandle.timestamp().equals(intervalStart)) {
                // This m1Bar belongs to a NEW interval. The previous candle is complete.
                resampledKLines.add(currentlyFormingCandle);
                // Start a new candle for the new interval.
                currentlyFormingCandle = new KLine(intervalStart, m1Bar.open(), m1Bar.high(), m1Bar.low(), m1Bar.close(), m1Bar.volume());
            } else {
                // This m1Bar is within the same interval. Aggregate its data.
                currentlyFormingCandle = new KLine(
                    currentlyFormingCandle.timestamp(),                      // Timestamp (and open price) remains the same
                    currentlyFormingCandle.open(),
                    currentlyFormingCandle.high().max(m1Bar.high()),        // Find the new max high
                    currentlyFormingCandle.low().min(m1Bar.low()),          // Find the new min low
                    m1Bar.close(),                                          // The close is always the latest close
                    currentlyFormingCandle.volume().add(m1Bar.volume())     // Accumulate the volume
                );
            }
        }

        // After the loop, the last forming candle needs to be added to the results.
        if (currentlyFormingCandle != null) {
            resampledKLines.add(currentlyFormingCandle);
        }

        return resampledKLines;
    }

    /**
     * Calculates the start time of the interval a given timestamp belongs to.
     * For example, for a 5-minute timeframe, 10:03, 10:04, and 10:00 all belong
     * to the interval starting at 10:00.
     *
     * @param timestamp The timestamp to check.
     * @param timeframe The target timeframe.
     * @return The exact start time of the interval.
     */
    private static Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
        // Use the record's accessor method 'duration()' instead of 'getDuration()'
        long durationMillis = timeframe.duration().toMillis();
        if (durationMillis == 0) return timestamp; // Avoid division by zero
        long epochMillis = timestamp.toEpochMilli();
        return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
    }
}