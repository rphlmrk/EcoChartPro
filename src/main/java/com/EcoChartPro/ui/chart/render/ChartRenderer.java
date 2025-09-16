package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import java.awt.Graphics2D;
import java.util.List;

public class ChartRenderer {

    private final AxisRenderer axisRenderer;
    private final CandlestickRenderer candlestickRenderer;

    public ChartRenderer() {
        this.axisRenderer = new AxisRenderer();
        this.candlestickRenderer = new CandlestickRenderer();
    }

    /**
     * Signature changed to accept a list of K-lines and timeframe directly.
     */
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines, int startIndex, Timeframe tf) {
        // Draw background grid lines first
        axisRenderer.draw(g2d, axis, visibleKlines, tf);

        // Draw candlesticks on top
        candlestickRenderer.draw(g2d, axis, visibleKlines, startIndex);
    }
}