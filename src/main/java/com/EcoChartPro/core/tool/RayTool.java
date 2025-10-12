package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.RayObject;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RayTool implements DrawingTool {
    
    private DrawingObjectPoint startPoint;
    private DrawingObjectPoint endPoint;
    private DrawingObjectPoint previewEndPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;

    public RayTool() {
        DrawingToolTemplate activeTemplate = SettingsManager.getInstance().getActiveTemplateForTool("RayObject");
        this.defaultColor = activeTemplate.color();
        this.defaultStroke = activeTemplate.stroke();
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
        return new RayObject(UUID.randomUUID(), startPoint, endPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, false);
    }

    @Override
    public DrawingObject getPreviewObject() {
        if (startPoint != null && previewEndPoint != null) {
            return new RayObject(UUID.randomUUID(), startPoint, previewEndPoint, defaultColor, defaultStroke, createDefaultVisibility(), false, false);
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