package com.EcoChartPro.api.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A public utility class providing high-performance, stateless calculation helpers
 * for building custom indicators. These methods form the core building blocks
 * for plugin developers.
 * This is part of the stable API for custom indicator plugins.
 */
public final class IndicatorUtils {

    // A standard, high-precision scale for internal calculations.
    private static final int CALCULATION_SCALE = 10;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private IndicatorUtils() {}

    /**
     * Extracts a specific series of data (e.g., close prices, high prices) from a list of K-lines.
     * This is typically the first step before performing calculations.
     *
     * @param klineData The list of {@link ApiKLine} objects.
     * @param extractor A function that specifies which BigDecimal value to extract from each K-line.
     *                  Example usages:
     *                  <pre>{@code
     *                  // To get a list of close prices:
     *                  List<BigDecimal> closePrices = IndicatorUtils.extractSourceData(klineData, ApiKLine::close);
     *                  // To get a list of typical prices ( (H+L+C)/3 ):
     *                  List<BigDecimal> typicalPrices = IndicatorUtils.extractSourceData(klineData, k ->
     *                      k.high().add(k.low()).add(k.close()).divide(BigDecimal.valueOf(3), RoundingMode.HALF_UP)
     *                  );
     *                  }</pre>
     * @return A new list containing the extracted BigDecimal values, or an empty list if the input is null or empty.
     */
    public static List<BigDecimal> extractSourceData(List<ApiKLine> klineData, Function<ApiKLine, BigDecimal> extractor) {
        if (klineData == null || klineData.isEmpty()) {
            return Collections.emptyList();
        }
        return klineData.stream()
                .map(extractor)
                .collect(Collectors.toList());
    }

    /**
     * Calculates the Simple Moving Average (SMA) for a given series of data.
     * This implementation uses a highly efficient O(n) sliding window algorithm.
     *
     * The returned list will have `data.size() - period + 1` elements. The first
     * element in the result corresponds to the moving average of the first `period`
     * elements in the input data.
     *
     * @param data The list of BigDecimal data (e.g., a list of close prices).
     * @param period The number of periods for the SMA (e.g., 20).
     * @return A new list of {@link BigDecimal} objects representing the SMA values.
     *         Returns an empty list if the input data is insufficient or the period is invalid.
     */
    public static List<BigDecimal> calculateSMA(List<BigDecimal> data, int period) {
        // --- Input Validation ---
        if (data == null || data.isEmpty() || period <= 0 || data.size() < period) {
            return Collections.emptyList();
        }

        List<BigDecimal> results = new ArrayList<>();
        BigDecimal periodDecimal = BigDecimal.valueOf(period);

        // --- Step 1: Calculate the sum of the very first window ---
        BigDecimal windowSum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            windowSum = windowSum.add(data.get(i));
        }

        // --- Step 2: Calculate the first SMA point ---
        BigDecimal firstSma = windowSum.divide(periodDecimal, CALCULATION_SCALE, RoundingMode.HALF_UP);
        results.add(firstSma);

        // --- Step 3: Slide the window across the rest of the data ---
        for (int i = period; i < data.size(); i++) {
            // Efficient update: add the new element, subtract the old one.
            windowSum = windowSum.add(data.get(i)).subtract(data.get(i - period));

            // Calculate the new SMA and add it to the results.
            BigDecimal newSma = windowSum.divide(periodDecimal, CALCULATION_SCALE, RoundingMode.HALF_UP);
            results.add(newSma);
        }

        return results;
    }

    /**
     * Calculates the sum of a list of BigDecimal values.
     * This is a convenient helper for scripts to avoid complex loops over Java objects.
     *
     * @param data The list of BigDecimal data.
     * @return The sum as a BigDecimal, or BigDecimal.ZERO if the list is null or empty.
     */
    public static BigDecimal sum(List<BigDecimal> data) {
        if (data == null || data.isEmpty()) {
            return BigDecimal.ZERO;
        }
        // Use Java's native stream for high performance and reliability.
        return data.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}