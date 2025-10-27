package com.EcoChartPro.ui.chart.axis;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Dimension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An axis implementation where the X-coordinate is directly proportional to time.
 * This is the standard behavior for candlestick, bar, and line charts.
 */
public class TimeBasedAxis implements IChartAxis {
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
        this.chartWidth = xAxisSource instanceof TimeBasedAxis ? ((TimeBasedAxis) xAxisSource).chartWidth : 0; // Simplified
        this.minPrice = yAxisSource.getMinPrice();
        this.maxPrice = yAxisSource.getMaxPrice();
        this.chartHeight = yAxisSource instanceof TimeBasedAxis ? ((TimeBasedAxis) yAxisSource).chartHeight : 0;
        this.isInverted = yAxisSource instanceof TimeBasedAxis && ((TimeBasedAxis) yAxisSource).isInverted;
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
    public Instant xToTime(int x, List<? extends AbstractChartData> visibleData, Timeframe timeframe) {
        if (!isConfigured || visibleData == null || timeframe == null) return null;
        double barWidth = getBarWidth();
        if (barWidth <= 0) return null;
        int slotIndex = xToSlotIndex(x);
        if (!visibleData.isEmpty() && slotIndex < visibleData.size()) {
            slotIndex = Math.max(0, slotIndex);
            return visibleData.get(slotIndex).startTime();
        } else {
            if (visibleData.isEmpty()) return null;
            AbstractChartData lastData = visibleData.get(visibleData.size() - 1);
            int futureBarDelta = slotIndex - (visibleData.size() - 1);
            Duration timePerBar = timeframe.duration();
            Duration durationToAdd = timePerBar.multipliedBy(futureBarDelta);
            return lastData.startTime().plus(durationToAdd);
        }
    }

    @Override
    public int timeToX(Instant time, List<? extends AbstractChartData> visibleData, Timeframe timeframe) {
        if (!isConfigured || visibleData == null || visibleData.isEmpty() || time == null || timeframe == null) return -1;
        Instant firstVisibleTime = visibleData.get(0).startTime();
        Duration timePerBar = timeframe.duration();
        if (timePerBar.isZero()) return -1;
        Duration timeDelta = Duration.between(firstVisibleTime, time);
        double slotDelta = (double) timeDelta.toMillis() / timePerBar.toMillis();
        double barWidth = getBarWidth();
        int firstBarX = slotToX(0);
        return (int) (firstBarX + (slotDelta * barWidth));
    }

    @Override
    public int timeToSlotIndex(Instant time, List<? extends AbstractChartData> visibleData, Timeframe timeframe) {
        if (!isConfigured || visibleData == null || visibleData.isEmpty() || time == null || timeframe == null) return -1;
        Instant firstVisibleTime = visibleData.get(0).startTime();
        Duration timePerBar = timeframe.duration();
        if (timePerBar.isZero()) return -1;
        Duration timeDelta = Duration.between(firstVisibleTime, time);
        double slotDelta = (double) timeDelta.toMillis() / timePerBar.toMillis();
        return (int) Math.round(slotDelta);
    }

    @Override
    public DrawingObjectPoint screenToDataPoint(int x, int y, List<? extends AbstractChartData> visibleData, Timeframe timeframe, int viewStartIndex) {
        if (!isConfigured) return null;
        Instant time = xToTime(x, visibleData, timeframe);
        BigDecimal price = yToPrice(y);
        if (time == null || price == null) return null;
        
        int relativeIndex = timeToSlotIndex(time, visibleData, timeframe);
        Integer absoluteIndex = relativeIndex >= 0 ? viewStartIndex + relativeIndex : null;
        
        return new DrawingObjectPoint(time, price, absoluteIndex);
    }
}