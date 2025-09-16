package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.Trendline;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A DrawingTool for creating a Trendline. It requires two clicks.
 */
public class TrendlineTool implements DrawingTool {

    private DrawingObjectPoint startPoint;
    private DrawingObjectPoint endPoint;
    private DrawingObjectPoint previewEndPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;
    private final boolean defaultShowPriceLabel;

    public TrendlineTool() {
        SettingsManager sm = SettingsManager.getInstance();
        this.defaultColor = sm.getToolDefaultColor("Trendline", new Color(255, 140, 40));
        this.defaultStroke = sm.getToolDefaultStroke("Trendline", new BasicStroke(2));
        this.defaultShowPriceLabel = sm.getToolDefaultShowPriceLabel("Trendline", true);
    }

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (startPoint == null) {
            startPoint = point;
        } else if (endPoint == null) {
            // Apply snapping to the final point as well if Shift is held
            if (e.isShiftDown()) {
                endPoint = getSnappedPoint(startPoint, point);
            } else {
                endPoint = point;
            }
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        if (startPoint != null && endPoint == null) {
            // If Shift is down, calculate the snapped point for the preview
            if (e.isShiftDown()) {
                this.previewEndPoint = getSnappedPoint(startPoint, point);
            } else {
                this.previewEndPoint = point;
            }
        }
    }

    /**
     * Snapping logic for straight lines.
     * Given a start and end point, returns a new end point that is snapped
     * to be perfectly horizontal or vertical relative to the start point.
     * Note: This heuristic compares data value changes. A pixel-based
     * comparison would be more accurate but requires more context.
     */
    private DrawingObjectPoint getSnappedPoint(DrawingObjectPoint start, DrawingObjectPoint end) {
        if (start == null || end == null) return end;

        // A simple heuristic for snapping based on data coordinates.
        // It compares the magnitude of the change in time vs. the change in price.
        long timeDiff = Math.abs(end.timestamp().toEpochMilli() - start.timestamp().toEpochMilli());
        BigDecimal priceDiff = end.price().subtract(start.price()).abs();

        // Heuristic: Tune these weights based on typical chart scales.
        // This attempts to normalize the scales so the comparison is meaningful.
        double timeWeight = timeDiff / 1000.0; // Assume 1 second is a unit
        double priceWeight = priceDiff.doubleValue() / 0.1; // Assume 0.1 price units is a unit

        if (timeWeight > priceWeight) {
            // More horizontal than vertical, so snap horizontally (keep the same price).
            return new DrawingObjectPoint(end.timestamp(), start.price());
        } else {
            // More vertical than horizontal, so snap vertically (keep the same time).
            return new DrawingObjectPoint(start.timestamp(), end.price());
        }
    }

    /**
     * Creates a default visibility map where the drawing is visible on all timeframes.
     */
    private Map<Timeframe, Boolean> createDefaultVisibility() {
        Map<Timeframe, Boolean> defaultVisibility = new EnumMap<>(Timeframe.class);
        for (Timeframe tf : Timeframe.values()) {
            defaultVisibility.put(tf, true);
        }
        return defaultVisibility;
    }

    @Override
    public DrawingObject getDrawingObject() {
        if (!isFinished()) {
            return null;
        }
        return new Trendline(UUID.randomUUID(), startPoint, endPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, defaultShowPriceLabel);
    }



    @Override
    public DrawingObject getPreviewObject() {
        if (startPoint != null && previewEndPoint != null) {
            return new Trendline(UUID.randomUUID(), startPoint, previewEndPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, defaultShowPriceLabel);
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return startPoint != null && endPoint != null;
    }

    @Override
    public void reset() {
        this.startPoint = null;
        this.endPoint = null;
        this.previewEndPoint = null;
    }
}