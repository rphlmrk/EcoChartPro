package com.EcoChartPro.core.indicator;

import com.EcoChartPro.model.KLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A service class containing the calculation logic for various technical indicators.
 * Methods in this class are designed for high performance, operating on entire datasets.
 */
public class IndicatorCalculator {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorCalculator.class);

    // Using a high precision for intermediate calculations, can be adjusted.
    private static final int CALCULATION_SCALE = 8;

    /**
     * Calculates the Simple Moving Average (SMA) for a given list of K-lines.
     * This implementation uses a highly efficient O(n) sliding window algorithm.
     * It avoids recalculating the sum for the entire window at each step.
     * <p>
     * Note: This implementation currently hardcodes the calculation source to the 'close' price.
     *
     * @param data The list of KLine data, sorted chronologically.
     * @param period The number of periods for the SMA (e.g., 20).
     * @return A list of {@link IndicatorPoint} objects representing the SMA values. The list will
     *         be shorter than the input data list by (period - 1) elements.
     */
    public List<IndicatorPoint> calculateSMA(List<KLine> data, int period) {
        // --- Input Validation ---
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        if (period <= 0) {
            logger.warn("SMA calculation requested with an invalid period of {}. Returning empty list.", period);
            return Collections.emptyList();
        }
        if (data.size() < period) {
            // Not enough data to calculate even one SMA point.
            return Collections.emptyList();
        }

        List<IndicatorPoint> results = new ArrayList<>();
        BigDecimal periodDecimal = BigDecimal.valueOf(period);

        // --- Step 1: Calculate the sum of the very first window ---
        BigDecimal windowSum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            windowSum = windowSum.add(data.get(i).close());
        }

        // --- Step 2: Calculate the first SMA point ---
        // The first point corresponds to the K-line at index (period - 1).
        BigDecimal firstSma = windowSum.divide(periodDecimal, CALCULATION_SCALE, RoundingMode.HALF_UP);
        results.add(new IndicatorPoint(data.get(period - 1).timestamp(), firstSma));

        // --- Step 3: Slide the window across the rest of the data ---
        // Start from the next K-line, which is at index `period`.
        for (int i = period; i < data.size(); i++) {
            // The efficient part: update the sum instead of re-calculating.
            // Add the new element entering the window.
            windowSum = windowSum.add(data.get(i).close());
            // Subtract the old element leaving the window.
            windowSum = windowSum.subtract(data.get(i - period).close());

            // Calculate the new SMA and add it to the results.
            BigDecimal newSma = windowSum.divide(periodDecimal, CALCULATION_SCALE, RoundingMode.HALF_UP);
            results.add(new IndicatorPoint(data.get(i).timestamp(), newSma));
        }

        return results;
    }

    // Future indicator calculation methods (e.g., calculateEMA, calculateRSI) will be added here.
}