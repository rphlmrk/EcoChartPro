package com.EcoChartPro.core.tool;

import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import java.awt.event.MouseEvent;

/**
 * Defines a contract for a drawing tool that manages the state
 * of creating a single drawing object via user interaction.
 */
public interface DrawingTool {

    /**
     * Handles a mouse press event at a specific data coordinate.
     * @param point The data point (time/price) of the click.
     * @param e The raw mouse event, useful for checking modifier keys.
     */
    void mousePressed(DrawingObjectPoint point, MouseEvent e);

    /**
     * Handles a mouse move event, typically to show a live preview.
     * @param point The current data point (time/price) of the mouse.
     * @param e The raw mouse event, useful for checking modifier keys.
     */
    void mouseMoved(DrawingObjectPoint point, MouseEvent e);

    /**
     * Gets the final, completed drawing object once the tool is finished.
     * @return The completed DrawingObject, or null if not finished.
     */
    DrawingObject getDrawingObject();

    /**
     * Gets the "ghost" or "preview" object to be rendered while drawing.
     * @return The preview DrawingObject, or null if no preview is active.
     */
    DrawingObject getPreviewObject();

    /**
     * Checks if the tool has gathered enough points to create a final object.
     * @return true if the drawing process is complete, false otherwise.
     */
    boolean isFinished();

    /**
     * Resets the tool's internal state to allow drawing a new object.
     */
    void reset();
}