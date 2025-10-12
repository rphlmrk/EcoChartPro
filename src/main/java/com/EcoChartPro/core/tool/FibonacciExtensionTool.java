package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.settings.SettingsManager.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.*;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FibonacciExtensionTool implements DrawingTool {

    private DrawingObjectPoint p0, p1, p2;
    private DrawingObjectPoint previewPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;
    private final boolean defaultShowPriceLabel;

    public FibonacciExtensionTool() {
        DrawingToolTemplate activeTemplate = SettingsManager.getInstance().getActiveTemplateForTool("FibonacciExtensionObject");
        this.defaultColor = activeTemplate.color();
        this.defaultStroke = activeTemplate.stroke();
        this.defaultShowPriceLabel = activeTemplate.showPriceLabel();
    }

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (p0 == null) {
            p0 = point;
        } else if (p1 == null) {
            p1 = point;
        } else if (p2 == null) {
            p2 = point;
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        if (!isFinished()) {
            previewPoint = point;
        }
    }

    @Override
    public DrawingObject getDrawingObject() {
        if (!isFinished()) return null;
        return new FibonacciExtensionObject(UUID.randomUUID(), p0, p1, p2, defaultColor, defaultStroke, createDefaultVisibility(), false, SettingsManager.getInstance().getFibExtensionDefaultLevels(), defaultShowPriceLabel);
    }

    @Override
    public DrawingObject getPreviewObject() {
        if (p0 != null && p1 == null && previewPoint != null) {
            return new Trendline(UUID.randomUUID(), p0, previewPoint, defaultColor, defaultStroke, createDefaultVisibility());
        } else if (p0 != null && p1 != null && p2 == null && previewPoint != null) {
            return new Trendline(UUID.randomUUID(), p1, previewPoint, defaultColor, defaultStroke, createDefaultVisibility());
        }
        return null;
    }

    @Override
    public boolean isFinished() {
        return p0 != null && p1 != null && p2 != null;
    }

    @Override
    public void reset() {
        p0 = null;
        p1 = null;
        p2 = null;
        previewPoint = null;
    }

    private Map<Timeframe, Boolean> createDefaultVisibility() {
        Map<Timeframe, Boolean> map = new LinkedHashMap<>();
        for (Timeframe tf : Timeframe.getStandardTimeframes()) map.put(tf, true);
        return map;
    }
}