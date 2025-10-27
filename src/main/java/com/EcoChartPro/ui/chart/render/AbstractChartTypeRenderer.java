package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.Graphics2D;
import java.util.List;

/**
 * An interface defining the contract for a renderer that can draw a specific chart type
 * (e.g., Candlesticks, Bars, Line) onto the chart panel.
 */
public interface AbstractChartTypeRenderer {

    /**
     * Draws the main chart series onto the graphics context.
     *
     * @param g2d The graphics context to draw on.
     * @param axis The configured chart axis for coordinate mapping.
     * @param visibleData The list of chart data points that are currently visible on the screen.
     * @param viewStartIndex The absolute starting index of the visible data from the full dataset.
     */
    void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex);
}