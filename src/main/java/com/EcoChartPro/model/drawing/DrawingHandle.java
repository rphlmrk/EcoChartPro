package com.EcoChartPro.model.drawing;

import java.awt.Point;
import java.util.UUID;

/**
 * Represents an interactive handle on a drawing object.
 *
 * @param position The screen coordinate (in pixels) of the handle's center.
 * @param type The type of handle (e.g., start or end point).
 * @param parentDrawingId The ID of the drawing this handle belongs to.
 */
public record DrawingHandle(
    Point position,
    HandleType type,
    UUID parentDrawingId
) {
    /**
     * Defines the different types of handles a drawing can have.
     */
    public enum HandleType {
        // For Trendlines
        START_POINT,
        END_POINT,

        // For Rectangles
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,

        // For moving the whole object
        BODY,
        
        // For Protected Level Pattern
        P0,
        P1,
        P2
    }
}