package com.EcoChartPro.core.tool;

import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * A "passive" drawing tool that simply tracks the current mouse position
 * to display detailed information about the KLine under the cursor.
 */
public class InfoTool implements DrawingTool {

    private DrawingObjectPoint currentPoint;
    private Point screenPoint; // NEW: To store the screen coordinates

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        // This tool does not create an object, so this is a no-op.
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        this.currentPoint = point;
        this.screenPoint = e.getPoint(); // Store the reliable screen coordinate from the event
    }

    @Override
    public DrawingObject getDrawingObject() {
        return null;
    }

    @Override
    public DrawingObject getPreviewObject() {
        return null;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public void reset() {
        this.currentPoint = null;
        this.screenPoint = null;
    }

    /**
     * Gets the last known data point (time/price) under the cursor.
     * @return The current DrawingObjectPoint.
     */
    public DrawingObjectPoint getCurrentPoint() {
        return currentPoint;
    }

    /**
     * Gets the last known screen point (x/y pixels) of the cursor.
     * @return The current screen Point.
     */
    public Point getScreenPoint() {
        return screenPoint;
    }
}