package com.EcoChartPro.api.indicator.drawing;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A public API class that describes a multi-segment line (a polyline).
 * This class is designed for high performance. It is stateful and allows for
 * efficient appending of new data points without reallocating its entire history.
 * This is part of the stable API for custom indicator plugins.
 */
public final class DrawablePolyline implements DrawableObject {

    private final List<DataPoint> points;
    private final Color color;
    private final float strokeWidth;

    /**
     * Constructs an empty polyline.
     */
    public DrawablePolyline(Color color, float strokeWidth) {
        this.points = new ArrayList<>();
        this.color = color;
        this.strokeWidth = strokeWidth;
    }
    
    /**
     * MODIFICATION: Changed visibility from private to public.
     * 
     * Constructs a new polyline with an existing list of points.
     * This is useful for bulk calculations or creating a line from a pre-computed set of data.
     */
    public DrawablePolyline(List<DataPoint> points, Color color, float strokeWidth) {
        this.points = new ArrayList<>(points); // Create a defensive copy
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    /**
     * Gets a read-only view of the points in this polyline.
     */
    public List<DataPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public Color getColor() {
        return color;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }
    
    /**
     * Creates a new DrawablePolyline instance with the given point appended.
     * This method is used by the IndicatorRunner to efficiently update the state.
     * @param point The DataPoint to add.
     * @return A new DrawablePolyline instance.
     */
    public DrawablePolyline withAppendedPoint(DataPoint point) {
        List<DataPoint> newPoints = new ArrayList<>(this.points);
        newPoints.add(point);
        return new DrawablePolyline(newPoints, this.color, this.strokeWidth);
    }
}