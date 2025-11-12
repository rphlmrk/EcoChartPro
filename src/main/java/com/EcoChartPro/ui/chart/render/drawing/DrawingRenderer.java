package com.EcoChartPro.ui.chart.render.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.UUID;

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
     * @param visibleKLines   The list of currently visible K-lines, used for time-axis context.
     * @param timeframe       The current chart timeframe, used for extrapolation and visibility checks.
     * @param drawingManager  The drawing manager to check for the selected object.
     */
    public void draw(Graphics2D g, List<DrawingObject> visibleDrawings, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe, DrawingManager drawingManager) { // [MODIFIED]
        if (visibleDrawings == null || visibleDrawings.isEmpty() || !axis.isConfigured() || timeframe == null) {
            return;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        UUID selectedId = drawingManager.getSelectedDrawingId(); // [NEW]

        for (DrawingObject drawing : visibleDrawings) {
            boolean isVisibleOnThisTimeframe = drawing.visibility().getOrDefault(timeframe, true);
            if (isVisibleOnThisTimeframe) {
                boolean isSelected = drawing.id().equals(selectedId); // [NEW]
                drawing.render(g, axis, visibleKLines, timeframe, isSelected); // [MODIFIED]
            }
        }
    }
}