package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.RectangleObject;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A DrawingTool for creating a Rectangle. It requires two clicks for two corners.
 */
public class RectangleTool implements DrawingTool {

    private DrawingObjectPoint startPoint;
    private DrawingObjectPoint endPoint;
    private DrawingObjectPoint previewEndPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;
    private final boolean defaultShowPriceLabel;

    public RectangleTool() {
        SettingsManager sm = SettingsManager.getInstance();
        this.defaultColor = sm.getToolDefaultColor("RectangleObject", new Color(33, 150, 243));
        this.defaultStroke = sm.getToolDefaultStroke("RectangleObject", new BasicStroke(2));
        this.defaultShowPriceLabel = sm.getToolDefaultShowPriceLabel("RectangleObject", true);
    }

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (startPoint == null) {
            startPoint = point;
        } else if (endPoint == null) {
            endPoint = point;
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        if (startPoint != null && endPoint == null) {
            this.previewEndPoint = point;
        }
    }

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
        return new RectangleObject(UUID.randomUUID(), startPoint, endPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, defaultShowPriceLabel);
    }

    @Override
    public DrawingObject getPreviewObject() {
        if (startPoint != null && previewEndPoint != null) {
            return new RectangleObject(UUID.randomUUID(), startPoint, previewEndPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, defaultShowPriceLabel);
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