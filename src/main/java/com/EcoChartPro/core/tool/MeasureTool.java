package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.MeasureToolObject;
import com.EcoChartPro.model.drawing.MeasureToolObject.ToolType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A drawing tool for creating measurement objects. Requires two clicks.
 */
public class MeasureTool implements DrawingTool {

    private final ToolType toolType;
    private DrawingObjectPoint startPoint;
    private DrawingObjectPoint endPoint;
    private DrawingObjectPoint previewEndPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;

    public MeasureTool(ToolType toolType) {
        this.toolType = toolType;
        DrawingToolTemplate activeTemplate = SettingsManager.getInstance().getActiveTemplateForTool("MeasureToolObject");
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
        return new MeasureToolObject(UUID.randomUUID(), startPoint, endPoint, toolType, defaultColor, defaultStroke, createDefaultVisibility(), false, false);
    }

    @Override
    public DrawingObject getPreviewObject() {
        if (startPoint != null && previewEndPoint != null) {
            return new MeasureToolObject(UUID.randomUUID(), startPoint, previewEndPoint, toolType, defaultColor, defaultStroke, createDefaultVisibility(), false, false);
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
        Map<Timeframe, Boolean> defaultVisibility = new LinkedHashMap<>();
        for (Timeframe tf : Timeframe.getStandardTimeframes()) {
            defaultVisibility.put(tf, true);
        }
        return defaultVisibility;
    }
}