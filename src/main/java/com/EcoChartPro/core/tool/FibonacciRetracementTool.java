package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class FibonacciRetracementTool implements DrawingTool {

    private DrawingObjectPoint startPoint;
    private DrawingObjectPoint endPoint;
    private DrawingObjectPoint previewEndPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;
    private final boolean defaultShowPriceLabel;

    public FibonacciRetracementTool() {
        SettingsManager sm = SettingsManager.getInstance();
        this.defaultColor = sm.getToolDefaultColor("FibonacciRetracementObject", new Color(0, 150, 136, 200));
        this.defaultStroke = sm.getToolDefaultStroke("FibonacciRetracementObject", new BasicStroke(1));
        this.defaultShowPriceLabel = sm.getToolDefaultShowPriceLabel("FibonacciRetracementObject", true);
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

    @Override
    public DrawingObject getDrawingObject() {
        if (!isFinished()) return null;
        return new FibonacciRetracementObject(UUID.randomUUID(), startPoint, endPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, SettingsManager.getInstance().getFibRetracementDefaultLevels(), defaultShowPriceLabel);
    }

    @Override
    public DrawingObject getPreviewObject() {
        if (startPoint != null && previewEndPoint != null) {
            return new FibonacciRetracementObject(UUID.randomUUID(), startPoint, previewEndPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, SettingsManager.getInstance().getFibRetracementDefaultLevels(), defaultShowPriceLabel);
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

    private Map<Timeframe, Boolean> createDefaultVisibility() {
        Map<Timeframe, Boolean> map = new EnumMap<>(Timeframe.class);
        for (Timeframe tf : Timeframe.values()) map.put(tf, true);
        return map;
    }
}