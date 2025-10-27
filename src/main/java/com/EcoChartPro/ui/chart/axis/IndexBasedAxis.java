package com.EcoChartPro.ui.chart.axis;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * An axis implementation where the X-coordinate is based on the linear index of a data point,
 * rather than its timestamp. Used for non-time-based charts like Renko.
 */
public class IndexBasedAxis implements IChartAxis {
    private BigDecimal minPrice = BigDecimal.ZERO;
    private BigDecimal maxPrice = BigDecimal.ZERO;
    private int barsPerScreen = 0;
    private int chartWidth = 0;
    private int chartHeight = 0;
    private boolean isConfigured = false;
    private boolean isInverted = false;
    private static final int Y_AXIS_PADDING = 20;
    private static final int X_AXIS_HORIZONTAL_PADDING = 10;

    @Override
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

    @Override
    public void configureForRendering(IChartAxis xAxisSource, IChartAxis yAxisSource) {
         if (xAxisSource == null || !xAxisSource.isConfigured() || yAxisSource == null || !yAxisSource.isConfigured()) {
            this.isConfigured = false;
            return;
        }
        this.barsPerScreen = xAxisSource.getBarsPerScreen();
        this.chartWidth = xAxisSource instanceof IndexBasedAxis ? ((IndexBasedAxis) xAxisSource).chartWidth : 0; // Simplified
        this.minPrice = yAxisSource.getMinPrice();
        this.maxPrice = yAxisSource.getMaxPrice();
        this.chartHeight = yAxisSource instanceof IndexBasedAxis ? ((IndexBasedAxis) yAxisSource).chartHeight : 0;
        this.isInverted = yAxisSource instanceof IndexBasedAxis && ((IndexBasedAxis) yAxisSource).isInverted;
        this.isConfigured = true;
    }

    @Override
    public boolean isConfigured() { return isConfigured; }
    @Override
    public BigDecimal getMinPrice() { return minPrice; }
    @Override
    public BigDecimal getMaxPrice() { return maxPrice; }
    @Override
    public int getBarsPerScreen() { return barsPerScreen; }

    @Override
    public int priceToY(BigDecimal price) {
        if (!isConfigured) return 0;
        if (price.equals(ANCHOR_TOP)) return isInverted ? chartHeight - Y_AXIS_PADDING : Y_AXIS_PADDING;
        if (price.equals(ANCHOR_BOTTOM)) return isInverted ? Y_AXIS_PADDING : chartHeight - Y_AXIS_PADDING;

        int drawableHeight = chartHeight - (2 * Y_AXIS_PADDING);
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        if (priceRange.compareTo(BigDecimal.ZERO) <= 0) return Y_AXIS_PADDING + drawableHeight / 2;

        BigDecimal priceOffset = price.subtract(minPrice);
        BigDecimal priceRatio = priceOffset.divide(priceRange, 10, RoundingMode.HALF_UP);
        int pixelOffset = priceRatio.multiply(BigDecimal.valueOf(drawableHeight)).intValue();

        return isInverted ? Y_AXIS_PADDING + pixelOffset : Y_AXIS_PADDING + drawableHeight - pixelOffset;
    }

    @Override
    public BigDecimal yToPrice(int y) {
        if (!isConfigured) return BigDecimal.ZERO;
        int drawableHeight = chartHeight - (2 * Y_AXIS_PADDING);
        if (drawableHeight == 0) return minPrice;
        int pixelOffset = isInverted ? y - Y_AXIS_PADDING : Y_AXIS_PADDING + drawableHeight - y;
        BigDecimal priceRange = maxPrice.subtract(minPrice);
        BigDecimal priceRatio = BigDecimal.valueOf(pixelOffset).divide(BigDecimal.valueOf(drawableHeight), 10, RoundingMode.HALF_UP);
        return minPrice.add(priceRatio.multiply(priceRange));
    }

    @Override
    public double getBarWidth() {
        if (!isConfigured || barsPerScreen == 0) return 0;
        int drawableWidth = chartWidth - (2 * X_AXIS_HORIZONTAL_PADDING);
        return (double) drawableWidth / barsPerScreen;
    }

    @Override
    public int slotToX(int slotIndex) {
        if (!isConfigured) return 0;
        double barWidth = getBarWidth();
        return (int) (X_AXIS_HORIZONTAL_PADDING + (slotIndex * barWidth) + (barWidth / 2));
    }
    
    @Override
    public int xToSlotIndex(int x) {
        if (!isConfigured) return -1;
        double barWidth = getBarWidth();
        if (barWidth <= 0) return -1;
        return (int) Math.round(((double)x - X_AXIS_HORIZONTAL_PADDING - (barWidth / 2)) / barWidth);
    }
    
    @Override
    public Instant xToTime(int x, List<? extends AbstractChartData> visibleData, Timeframe tf) {
        if (!isConfigured || visibleData == null || visibleData.isEmpty()) return null;
        int slotIndex = xToSlotIndex(x);
        slotIndex = Math.max(0, Math.min(slotIndex, visibleData.size() - 1));
        return visibleData.get(slotIndex).startTime();
    }

    @Override
    public int timeToX(Instant time, List<? extends AbstractChartData> visibleData, Timeframe tf) {
        int slot = timeToSlotIndex(time, visibleData, tf);
        return (slot != -1) ? slotToX(slot) : -1;
    }

    @Override
    public int timeToSlotIndex(Instant time, List<? extends AbstractChartData> visibleData, Timeframe tf) {
        if (!isConfigured || visibleData == null || visibleData.isEmpty() || time == null) return -1;
        // This is an approximation. Find the brick that contains this timestamp.
        // A binary search would be better for performance if the data is large.
        for (int i = 0; i < visibleData.size(); i++) {
            AbstractChartData data = visibleData.get(i);
            if (!time.isBefore(data.startTime()) && time.isBefore(data.endTime().plusMillis(1))) {
                return i;
            }
        }
        // If not found within a brick, find the closest one.
        if (time.isBefore(visibleData.get(0).startTime())) return 0;
        if (time.isAfter(visibleData.get(visibleData.size() - 1).endTime())) return visibleData.size() - 1;
        return -1; // Should not happen if time is between start and end.
    }

    @Override
    public DrawingObjectPoint screenToDataPoint(int x, int y, List<? extends AbstractChartData> visibleData, Timeframe tf, int viewStartIndex) {
        if (!isConfigured) return null;
        
        int relativeIndex = xToSlotIndex(x);
        Instant time = xToTime(x, visibleData, tf); // Gets time from the relative index
        BigDecimal price = yToPrice(y);

        if (time == null || price == null) return null;

        Integer absoluteIndex = relativeIndex >= 0 ? viewStartIndex + relativeIndex : null;

        return new DrawingObjectPoint(time, price, absoluteIndex);
    }
}