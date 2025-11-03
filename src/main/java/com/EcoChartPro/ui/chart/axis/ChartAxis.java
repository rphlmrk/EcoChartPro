package com.EcoChartPro.ui.chart.axis;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Manages the logic for mapping data coordinates (price, index) to screen coordinates (pixels).
 */
public class ChartAxis {
    private BigDecimal minPrice = BigDecimal.ZERO;
    private BigDecimal maxPrice = BigDecimal.ZERO;
    private int barsPerScreen = 0;
    private int chartWidth = 0;
    private int chartHeight = 0;
    private boolean isConfigured = false;
    private boolean isInverted = false;
    private static final int Y_AXIS_PADDING = 20;
    private static final int X_AXIS_HORIZONTAL_PADDING = 10;

    /** A special value used in a DataPoint's price to anchor a drawing to the top of the chart panel's drawing area. */
    public static final BigDecimal ANCHOR_TOP = new BigDecimal("-100000001.123");
    /** A special value used in a DataPoint's price to anchor a drawing to the bottom of the chart panel's drawing area. */
    public static final BigDecimal ANCHOR_BOTTOM = new BigDecimal("-100000002.456");

    public void configure(BigDecimal minPrice, BigDecimal maxPrice, int barsPerScreen, Dimension dimensions, boolean isInverted) {
        if (minPrice == null || maxPrice == null) {
            this.isConfigured = false;
            return;
        }

        BigDecimal priceRange = maxPrice.subtract(minPrice);
        if (priceRange.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal buffer = priceRange.multiply(BigDecimal.valueOf(0.05));
            this.minPrice = minPrice.subtract(buffer);
            this.maxPrice = maxPrice.add(buffer);
        } else {
            this.minPrice = minPrice.subtract(BigDecimal.ONE);
            this.maxPrice = maxPrice.add(BigDecimal.ONE);
        }

        this.barsPerScreen = barsPerScreen;
        this.chartWidth = dimensions.width;
        this.chartHeight = dimensions.height;
        this.isInverted = isInverted;
        this.isConfigured = true;
    }

    public void configureForRendering(ChartAxis xAxisSource, ChartAxis yAxisSource) {
        if (xAxisSource == null || !xAxisSource.isConfigured() || yAxisSource == null || !yAxisSource.isConfigured()) {
            this.isConfigured = false;
            return;
        }

        // --- Copy X-axis (time) properties ---
        this.barsPerScreen = xAxisSource.barsPerScreen;
        this.chartWidth = xAxisSource.chartWidth;

        // --- Copy Y-axis (price) properties ---
        this.minPrice = yAxisSource.minPrice;
        this.maxPrice = yAxisSource.maxPrice;
        this.chartHeight = yAxisSource.chartHeight;
        this.isInverted = yAxisSource.isInverted;

        this.isConfigured = true;
    }


    public int priceToY(BigDecimal price) {
        if (!isConfigured) return 0;

        if (price.equals(ANCHOR_TOP)) {
            return isInverted ? chartHeight - Y_AXIS_PADDING : Y_AXIS_PADDING;
        }
        if (price.equals(ANCHOR_BOTTOM)) {
            return isInverted ? Y_AXIS_PADDING : chartHeight - Y_AXIS_PADDING;
        }

        int drawableHeight = chartHeight - (2 * Y_AXIS_PADDING);
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        if (priceRange.compareTo(BigDecimal.ZERO) <= 0) {
            return Y_AXIS_PADDING + drawableHeight / 2;
        }
        BigDecimal priceOffset = price.subtract(minPrice);
        BigDecimal priceRatio = priceOffset.divide(priceRange, 10, RoundingMode.HALF_UP);
        int pixelOffset = priceRatio.multiply(BigDecimal.valueOf(drawableHeight)).intValue();

        if (isInverted) {
            return Y_AXIS_PADDING + pixelOffset;
        } else {
            return Y_AXIS_PADDING + drawableHeight - pixelOffset;
        }
    }

    public BigDecimal yToPrice(int y) {
        if (!isConfigured) return BigDecimal.ZERO;
        int drawableHeight = chartHeight - (2 * Y_AXIS_PADDING);
        if (drawableHeight == 0) return minPrice;

        int pixelOffset;
        if (isInverted) {
            pixelOffset = y - Y_AXIS_PADDING;
        } else {
            pixelOffset = Y_AXIS_PADDING + drawableHeight - y;
        }
        
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        BigDecimal priceRatio = BigDecimal.valueOf(pixelOffset).divide(BigDecimal.valueOf(drawableHeight), 10, RoundingMode.HALF_UP);
        return minPrice.add(priceRatio.multiply(priceRange));
    }

    public double getBarWidth() {
        if (!isConfigured || barsPerScreen == 0) return 0;
        int drawableWidth = chartWidth - (2 * X_AXIS_HORIZONTAL_PADDING);
        return (double) drawableWidth / barsPerScreen;
    }

    /**
     * [NEW] Calculates the vertical pixel height corresponding to a given price range.
     * This is useful for determining the on-screen size of price-based elements.
     * @param priceValue The amount of price to convert to pixels.
     * @return The height in pixels, or 0 if the axis is not configured.
     */
    public double priceToPixel(double priceValue) {
        if (!isConfigured) return 0;

        int drawableHeight = chartHeight - (2 * Y_AXIS_PADDING);
        BigDecimal totalPriceRange = maxPrice.subtract(minPrice);
        if (totalPriceRange.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        double priceRatio = priceValue / totalPriceRange.doubleValue();
        return priceRatio * drawableHeight;
    }

    public int slotToX(int slotIndex) {
        if (!isConfigured) return 0;
        double barWidth = getBarWidth();
        return (int) (X_AXIS_HORIZONTAL_PADDING + (slotIndex * barWidth) + (barWidth / 2));
    }

    // Method signature changed to accept timeframe for extrapolation
    public Instant xToTime(int x, List<KLine> visibleKLines, Timeframe timeframe) {
        if (!isConfigured || visibleKLines == null || timeframe == null) return null;

        double barWidth = getBarWidth();
        if (barWidth <= 0) return null;

        int slotIndex = (int) Math.round(((double)x - X_AXIS_HORIZONTAL_PADDING - (barWidth / 2)) / barWidth);

        // The logic is now split to handle existing candles vs future (extrapolated) time.
        if (!visibleKLines.isEmpty() && slotIndex < visibleKLines.size()) {
            // The cursor is over an existing candle.
            slotIndex = Math.max(0, slotIndex);
            return visibleKLines.get(slotIndex).timestamp();
        } else {
            // The cursor is in the empty right margin or the list is empty. We need to extrapolate.
            if (visibleKLines.isEmpty()) return null; // Can't extrapolate from nothing.

            KLine lastKline = visibleKLines.get(visibleKLines.size() - 1);
            int futureBarDelta = slotIndex - (visibleKLines.size() - 1);

            // Use the record's accessor method 'duration()'
            Duration timePerBar = timeframe.duration();
            Duration durationToAdd = timePerBar.multipliedBy(futureBarDelta);

            return lastKline.timestamp().plus(durationToAdd);
        }
    }

    /**
     * This method is completely rewritten to use a mathematical approach instead of a search.
     * It can now correctly calculate the X-coordinate for a timestamp that is in the future.
     *
     * @param time The timestamp to convert to an X-coordinate.
     * @param visibleKLines The list of visible candles, used to establish a time baseline.
     * @param timeframe The chart's current timeframe, used to calculate time deltas.
     * @return The screen X-coordinate, or -1 if it cannot be calculated.
     */
    public int timeToX(Instant time, List<KLine> visibleKLines, Timeframe timeframe) {
        if (!isConfigured || visibleKLines == null || visibleKLines.isEmpty() || time == null || timeframe == null) {
            return -1;
        }

        Instant firstVisibleTime = visibleKLines.get(0).timestamp();
        // Use the record's accessor method 'duration()'
        Duration timePerBar = timeframe.duration();

        if (timePerBar.isZero()) {
            return -1; // Avoid division by zero
        }

        // Calculate the duration between the start of the visible range and the target time.
        Duration timeDelta = Duration.between(firstVisibleTime, time);

        // Calculate how many "bars" this duration represents. This can be a fractional number.
        double slotDelta = (double) timeDelta.toMillis() / timePerBar.toMillis();

        // Get the pixel width of one bar.
        double barWidth = getBarWidth();

        // The X-coordinate of the first bar's center.
        int firstBarX = slotToX(0);

        // Calculate the final X by adding the pixel offset based on the slot delta.
        return (int) (firstBarX + (slotDelta * barWidth));
    }

    /**
     * Converts a timestamp to a slot index within the visible K-lines. This is the
     * logical inverse of getting a timestamp from a slot index and is more robust
     * than relying on exact timestamp equality.
     * @param time The timestamp to convert.
     * @param visibleKLines The list of visible candles to establish a time baseline.
     * @param timeframe The chart's timeframe for calculating time deltas.
     * @return The integer slot index, or -1 if the time is out of the visible range.
     */
    public int timeToSlotIndex(Instant time, List<KLine> visibleKLines, Timeframe timeframe) {
        if (!isConfigured || visibleKLines == null || visibleKLines.isEmpty() || time == null || timeframe == null) {
            return -1;
        }

        Instant firstVisibleTime = visibleKLines.get(0).timestamp();
        // Use the record's accessor method 'duration()'
        Duration timePerBar = timeframe.duration();
        if (timePerBar.isZero()) {
            return -1;
        }

        Duration timeDelta = Duration.between(firstVisibleTime, time);
        double slotDelta = (double) timeDelta.toMillis() / timePerBar.toMillis();
        
        return (int) Math.round(slotDelta);
    }

    // Method signature changed to pass timeframe down to xToTime
    public DrawingObjectPoint screenToDataPoint(int x, int y, List<KLine> visibleKLines, Timeframe timeframe) {
        if (!isConfigured) return null;
        Instant time = xToTime(x, visibleKLines, timeframe);
        BigDecimal price = yToPrice(y);
        if (time == null || price == null) return null;
        return new DrawingObjectPoint(time, price);
    }

    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }

    public boolean isConfigured() { return isConfigured; }
    public int getBarsPerScreen() { return barsPerScreen; }
}