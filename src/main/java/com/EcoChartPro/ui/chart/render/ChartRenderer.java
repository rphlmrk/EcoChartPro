package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartRenderer {

    private final Map<ChartType, AbstractChartTypeRenderer> renderers = new HashMap<>();

    public ChartRenderer() {
        // Instantiate and register all available renderers
        renderers.put(ChartType.CANDLES, new CandleRenderer());
        renderers.put(ChartType.BARS, new BarRenderer());
        renderers.put(ChartType.HOLLOW_CANDLES, new HollowCandleRenderer());
        renderers.put(ChartType.LINE, new LineRenderer(false));
        renderers.put(ChartType.LINE_WITH_MARKERS, new LineRenderer(true));
        renderers.put(ChartType.AREA, new AreaRenderer());
        renderers.put(ChartType.VOLUME_CANDLES, new VolumeCandleRenderer());
        renderers.put(ChartType.HEIKIN_ASHI, new HeikinAshiRenderer());
        // New renderers for non-time-based charts
        renderers.put(ChartType.RENKO, new RenkoRenderer());
        renderers.put(ChartType.RANGE_BARS, new RangeBarRenderer());
        renderers.put(ChartType.KAGI, new KagiRenderer());
        renderers.put(ChartType.POINT_AND_FIGURE, new PointAndFigureRenderer());
    }

    /**
     * Draws the main chart series by delegating to the appropriate renderer based on the selected ChartType.
     *
     * @param g2d The graphics context.
     * @param chartType The type of chart to render.
     * @param axis The configured chart axis.
     * @param visibleData The list of visible data points.
     * @param startIndex The absolute start index of the visible data.
     */
    public void draw(Graphics2D g2d, ChartType chartType, IChartAxis axis, List<? extends AbstractChartData> visibleData, int startIndex) {
        AbstractChartTypeRenderer renderer = renderers.get(chartType);

        if (renderer != null) {
            renderer.draw(g2d, axis, visibleData, startIndex);
        } else {
            // Fallback for unimplemented chart types
            String message = chartType.getDisplayName() + " chart type not yet implemented.";
            g2d.setColor(Color.GRAY);
            g2d.drawString(message, 50, 100);
        }
    }
}