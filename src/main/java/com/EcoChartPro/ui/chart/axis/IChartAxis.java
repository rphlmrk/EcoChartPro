package com.EcoChartPro.ui.chart.axis;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;

import java.awt.Dimension;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An interface defining the contract for mapping data coordinates (price, time/index)
 * to screen coordinates (pixels). This supports both time-based and index-based charts.
 */
public interface IChartAxis {

    /** A special value used in a DataPoint's price to anchor a drawing to the top of the chart panel's drawing area. */
    BigDecimal ANCHOR_TOP = new BigDecimal("-100000001.123");
    /** A special value used in a DataPoint's price to anchor a drawing to the bottom of the chart panel's drawing area. */
    BigDecimal ANCHOR_BOTTOM = new BigDecimal("-100000002.456");

    /** Configures the axis with the current view parameters. */
    void configure(BigDecimal minPrice, BigDecimal maxPrice, int barsPerScreen, Dimension dimensions, boolean isInverted);

    /** Configures an axis (like for an indicator panel) to match the properties of other axes. */
    void configureForRendering(IChartAxis xAxisSource, IChartAxis yAxisSource);

    /** Checks if the axis has been configured with valid data. */
    boolean isConfigured();

    // --- Y-Axis (Price) methods ---
    int priceToY(BigDecimal price);
    BigDecimal yToPrice(int y);
    BigDecimal getMinPrice();
    BigDecimal getMaxPrice();

    // --- X-Axis (Slot/Index) methods ---
    double getBarWidth();
    int getBarsPerScreen();
    int slotToX(int slotIndex);
    int xToSlotIndex(int x);

    // --- Cross-domain mapping methods ---
    int timeToX(Instant time, List<? extends AbstractChartData> visibleData, Timeframe tf);
    Instant xToTime(int x, List<? extends AbstractChartData> visibleData, Timeframe tf);
    DrawingObjectPoint screenToDataPoint(int x, int y, List<? extends AbstractChartData> visibleData, Timeframe tf, int viewStartIndex);
    int timeToSlotIndex(Instant time, List<? extends AbstractChartData> visibleData, Timeframe tf);
}