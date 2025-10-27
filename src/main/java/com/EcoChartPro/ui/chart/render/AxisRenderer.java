package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import com.EcoChartPro.ui.chart.axis.TimeBasedAxis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class AxisRenderer {

    private static final int TARGET_Y_GRID_LINES = 8; // Target number of lines for "nice step" calculation
    private static final int TARGET_X_GRID_LINES = 10; // Target for index-based charts

    /**
     * Draws the full background grid, delegating to the specific methods.
     * @param g2d The graphics context.
     * @param axis The configured chart axis.
     * @param visibleData The list of data points on screen for context.
     * @param tf The current timeframe (used by time-based axis).
     */
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, Timeframe tf) {
        if (!axis.isConfigured()) return;
        drawHorizontalGridLines(g2d, axis);

        // [FIX] Check both axis type AND data type to prevent ClassCastException during chart type switching.
        if (axis instanceof TimeBasedAxis && (visibleData.isEmpty() || visibleData.get(0) instanceof KLine)) {
            // This cast is now safe due to the check.
            @SuppressWarnings("unchecked")
            List<KLine> klines = (List<KLine>) visibleData;
            drawTimeBasedVerticalGridLines(g2d, axis, klines, tf);
        } else {
            // Fallback for index-based axes or during the transition period.
            drawIndexBasedVerticalGridLines(g2d, axis, visibleData.size());
        }
    }

    /**
     * Draws vertical grid lines at "nice" time intervals.
     */
    private void drawTimeBasedVerticalGridLines(Graphics2D g2d, IChartAxis axis, List<KLine> visibleKlines, Timeframe tf) {
        if (visibleKlines == null || visibleKlines.isEmpty() || tf == null) return;

        Instant firstVisibleTime = visibleKlines.get(0).timestamp();
        Instant lastVisibleTime = visibleKlines.get(visibleKlines.size() - 1).timestamp();
        Duration visibleDuration = Duration.between(firstVisibleTime, lastVisibleTime);

        // Determine the appropriate time interval for grid lines based on the visible duration
        Duration labelInterval;
        if (visibleDuration.toDays() > 3) {
            labelInterval = Duration.ofDays(1);
        } else if (visibleDuration.toHours() > 12) {
            labelInterval = Duration.ofHours(4);
        } else if (visibleDuration.toHours() > 6) {
            labelInterval = Duration.ofHours(2);
        } else if (visibleDuration.toHours() > 2) {
            labelInterval = Duration.ofHours(1);
        } else {
            labelInterval = Duration.ofMinutes(30);
        }
        
        long intervalSeconds = labelInterval.toSeconds();
        if (intervalSeconds == 0) return; // Avoid division by zero

        // Calculate the timestamp of the first grid line by rounding up to the next interval
        long firstVisibleEpochSecond = firstVisibleTime.getEpochSecond();
        long startEpochSecond = (long) (Math.ceil((double) firstVisibleEpochSecond / intervalSeconds) * intervalSeconds);

        g2d.setColor(SettingsManager.getInstance().getGridColor());
        Instant currentLineTime = Instant.ofEpochSecond(startEpochSecond);

        // Loop from the first line time until we are past the last visible time
        while (currentLineTime.isBefore(lastVisibleTime)) {
            int x = axis.timeToX(currentLineTime, visibleKlines, tf);
            if (x != -1) {
                g2d.drawLine(x, 0, x, g2d.getClipBounds().height);
            }
            currentLineTime = currentLineTime.plus(labelInterval);
        }
    }

    /**
     * Draws vertical grid lines at regular index intervals for non-time-based charts.
     */
    private void drawIndexBasedVerticalGridLines(Graphics2D g2d, IChartAxis axis, int visibleDataSize) {
        if (visibleDataSize <= 0) return;
        g2d.setColor(SettingsManager.getInstance().getGridColor());
        int step = Math.max(1, axis.getBarsPerScreen() / TARGET_X_GRID_LINES);
        for (int i = 0; i < visibleDataSize; i += step) {
            int x = axis.slotToX(i);
            g2d.drawLine(x, 0, x, g2d.getClipBounds().height);
        }
    }
    
    /**
     * Draws horizontal grid lines at "nice" price intervals.
     */
    public void drawHorizontalGridLines(Graphics2D g2d, IChartAxis axis) {
        if (!axis.isConfigured()) return;
        BigDecimal minPrice = axis.getMinPrice();
        BigDecimal maxPrice = axis.getMaxPrice();
        if (minPrice == null || maxPrice == null || maxPrice.compareTo(minPrice) <= 0) return;

        BigDecimal priceRange = maxPrice.subtract(minPrice);
        BigDecimal niceStep = calculateNiceStep(priceRange, TARGET_Y_GRID_LINES);

        if (niceStep.compareTo(BigDecimal.ZERO) <= 0) return;

        // Calculate the first grid line value on or above the minimum visible price
        BigDecimal startPrice = minPrice.divide(niceStep, 0, RoundingMode.CEILING).multiply(niceStep);
        
        g2d.setColor(SettingsManager.getInstance().getGridColor());
        int endX = g2d.getClipBounds().width;
        BigDecimal currentPrice = startPrice;

        while (currentPrice.compareTo(maxPrice) <= 0) {
            int y = axis.priceToY(currentPrice);
            g2d.drawLine(0, y, endX, y);
            currentPrice = currentPrice.add(niceStep);
        }
    }

    /**
     * Calculates a "human-friendly" step value for grid lines.
     * @param range The total range of the data to be covered.
     * @param targetSteps The desired number of steps/lines.
     * @return A BigDecimal representing a nice, round step value.
     */
    private BigDecimal calculateNiceStep(BigDecimal range, int targetSteps) {
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        // Calculate a rough step size
        double roughStep = range.doubleValue() / targetSteps;
        // Calculate a magnitude of the rough step (e.g., 10, 100, 0.1)
        double magnitude = Math.pow(10, Math.floor(Math.log10(roughStep)));
        // Normalize the rough step to be between 1 and 10
        double normalizedStep = roughStep / magnitude;

        // Snap the normalized step to the nearest "nice" value (1, 2, 5, or 10)
        double niceNormalizedStep;
        if (normalizedStep < 1.5) {
            niceNormalizedStep = 1;
        } else if (normalizedStep < 3.5) {
            niceNormalizedStep = 2;
        } else if (normalizedStep < 7.5) {
            niceNormalizedStep = 5;
        } else {
            niceNormalizedStep = 10;
        }
        
        return BigDecimal.valueOf(niceNormalizedStep * magnitude);
    }
}