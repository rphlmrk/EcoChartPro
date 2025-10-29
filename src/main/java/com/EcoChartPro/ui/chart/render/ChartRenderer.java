package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChartRenderer {

    private final Map<ChartType, AbstractChartTypeRenderer> renderers = new HashMap<>();
    // Minimum bar width in pixels to render footprint text. Below this, it switches to candles.
    private static final int FOOTPRINT_THRESHOLD_PX = 30;

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
        renderers.put(ChartType.FOOTPRINT, new FootprintRenderer());
    }

    /**
     * Draws the main chart series by delegating to the appropriate renderer based on the selected ChartType.
     * Includes logic to automatically switch to candles if Footprint is not legible.
     *
     * @param g2d The graphics context.
     * @param chartType The type of chart to render.
     * @param axis The configured chart axis.
     * @param visibleKlines The list of visible data points.
     * @param startIndex The absolute start index of the visible data.
     * @param dataModel The chart's data model, for accessing additional data like footprints.
     */
    public void draw(Graphics2D g2d, ChartType chartType, ChartAxis axis, List<KLine> visibleKlines, int startIndex, ChartDataModel dataModel) {
        ChartType effectiveType = chartType;

        // Auto-switch from Footprint to Candles when zoomed out for performance and readability
        if (chartType == ChartType.FOOTPRINT) {
            if (axis.getBarWidth() < FOOTPRINT_THRESHOLD_PX) {
                effectiveType = ChartType.CANDLES;
            }
        }
        
        AbstractChartTypeRenderer renderer = renderers.get(effectiveType);

        if (renderer != null) {
            renderer.draw(g2d, axis, visibleKlines, startIndex, dataModel);
        } else {
            // Fallback for unimplemented chart types
            String message = chartType.getDisplayName() + " chart type not yet implemented.";
            g2d.setColor(Color.GRAY);
            g2d.drawString(message, 50, 100);
        }
    }
}