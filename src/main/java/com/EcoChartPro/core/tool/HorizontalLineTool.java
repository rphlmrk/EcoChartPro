package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.HorizontalLineObject;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HorizontalLineTool implements DrawingTool {

    private DrawingObjectPoint anchor;
    private final Color defaultColor;
    private final BasicStroke defaultStroke;
    private final boolean defaultShowPriceLabel;

    public HorizontalLineTool() {
        DrawingToolTemplate activeTemplate = SettingsManager.getInstance().getActiveTemplateForTool("HorizontalLineObject");
        this.defaultColor = activeTemplate.color();
        this.defaultStroke = activeTemplate.stroke();
        this.defaultShowPriceLabel = activeTemplate.showPriceLabel();
    }

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (anchor == null) {
            anchor = point;
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        // One-click tool, no preview on move needed after click
    }

    @Override
    public DrawingObject getDrawingObject() {
        if (!isFinished()) return null;
        return new HorizontalLineObject(UUID.randomUUID(), anchor, defaultColor, defaultStroke, createDefaultVisibility(), false, defaultShowPriceLabel);
    }

    @Override
    public DrawingObject getPreviewObject() {
        return null; // No preview, it's instant
    }

    @Override
    public boolean isFinished() {
        return anchor != null;
    }

    @Override
    public void reset() {
        anchor = null;
    }

    private Map<Timeframe, Boolean> createDefaultVisibility() {
        Map<Timeframe, Boolean> map = new LinkedHashMap<>();
        for (Timeframe tf : Timeframe.getStandardTimeframes()) map.put(tf, true);
        return map;
    }
}