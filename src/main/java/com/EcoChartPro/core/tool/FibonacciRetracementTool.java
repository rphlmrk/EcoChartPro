package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
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
        DrawingToolTemplate activeTemplate = SettingsManager.getInstance().getActiveTemplateForTool("FibonacciRetracementObject");
        this.defaultColor = activeTemplate.color();
        this.defaultStroke = activeTemplate.stroke();
        this.defaultShowPriceLabel = activeTemplate.showPriceLabel();
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
        Map<Timeframe, Boolean> map = new LinkedHashMap<>();
        for (Timeframe tf : Timeframe.getStandardTimeframes()) map.put(tf, true);
        return map;
    }
}