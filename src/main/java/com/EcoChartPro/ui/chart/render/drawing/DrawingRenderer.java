package com.EcoChartPro.ui.chart.render.drawing;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * A class responsible for painting DrawingObjects onto a ChartPanel.
 * It delegates the actual rendering logic to each drawing object.
 */
public class DrawingRenderer {

    /**
     * The main drawing method. It iterates through a pre-filtered list of visible
     * drawings and renders each one by delegating to the object's own render method.
     *
     * @param g               The graphics context to draw on.
     * @param visibleDrawings The list of drawings that are known to be on-screen.
     * @param axis            The ChartAxis used for coordinate conversion.
     * @param visibleData   The list of currently visible chart data, used for time-axis context.
     * @param timeframe       The current chart timeframe, used for extrapolation and visibility checks.
     */
    public void draw(Graphics2D g, List<DrawingObject> visibleDrawings, IChartAxis axis, List<? extends AbstractChartData> visibleData, Timeframe timeframe) {
        if (visibleDrawings == null || visibleDrawings.isEmpty() || !axis.isConfigured() || timeframe == null) {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (DrawingObject drawing : visibleDrawings) {
            // Get the drawing's visibility map. Default to 'true' if for some reason the map is incomplete.
            boolean isVisibleOnThisTimeframe = drawing.visibility().getOrDefault(timeframe, true);
            if (isVisibleOnThisTimeframe) {
                // Delegate rendering to the object itself
                drawing.render(g, axis, visibleData, timeframe);
            }
        }
    }
}