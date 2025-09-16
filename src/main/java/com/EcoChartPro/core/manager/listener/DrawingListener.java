package com.EcoChartPro.core.manager.listener;

import com.EcoChartPro.model.drawing.DrawingObject;
import java.util.UUID;

/**
 * An interface for components that need to be notified of changes
 * to drawing objects managed by the DrawingManager.
 */
public interface DrawingListener {

    /**
     * Called when a new drawing has been added to the manager.
     *
     * @param drawingObject The new drawing object that was added.
     */
    void onDrawingAdded(DrawingObject drawingObject);

    /**
     * Called when an existing drawing has been modified (e.g., moved or restyled).
     *
     * @param drawingObject The drawing object that was updated.
     */
    void onDrawingUpdated(DrawingObject drawingObject);

    /**
     * Called when a drawing has been removed from the manager.
     *
     * @param drawingObjectId The unique ID of the drawing object that was removed.
     */
    void onDrawingRemoved(UUID drawingObjectId);
}