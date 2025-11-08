package com.EcoChartPro.core.tool;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.ProtectedLevelPatternObject;
import com.EcoChartPro.model.drawing.Trendline;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A drawing tool for creating the 3-point Protected High/Low pattern.
 */
public class ProtectedLevelPatternTool implements DrawingTool {

    private DrawingObjectPoint p0, p1, p2;
    private DrawingObjectPoint previewPoint;

    private final Color defaultColor;
    private final BasicStroke defaultStroke;

    public ProtectedLevelPatternTool() {
        DrawingToolTemplate activeTemplate = SettingsService.getInstance().getActiveTemplateForTool("ProtectedLevelPatternObject");
        this.defaultColor = activeTemplate.color();
        this.defaultStroke = activeTemplate.stroke();
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
        // We only need a preview point if the drawing is in progress
        if (!isFinished()) {
            previewPoint = point;
        }
    }

    @Override
    public boolean isFinished() {
        return p0 != null && p1 != null && p2 != null;
    }

    @Override
    public DrawingObject getDrawingObject() {
        if (!isFinished()) return null;

        // Correctly determine the direction.
        // A down-move (p1 < p0) is a setup for a LONG (Bullish PH pattern).
        // An up-move (p1 > p0) is a setup for a SHORT (Bearish PL pattern).
        TradeDirection direction = p1.price().compareTo(p0.price()) < 0 ?
                                   TradeDirection.LONG :
                                   TradeDirection.SHORT;

        return new ProtectedLevelPatternObject(
            UUID.randomUUID(), p0, p1, p2, direction,
            defaultColor, defaultStroke, createDefaultVisibility(), false, false
        );
    }

    @Override
    public DrawingObject getPreviewObject() {
        // This provides a live preview of the lines as the user is clicking.
        if (p0 != null && p1 == null && previewPoint != null) {
            // Preview the first leg (p0 -> mouse)
            return new Trendline(UUID.randomUUID(), p0, previewPoint, defaultColor, defaultStroke, createDefaultVisibility());
        } else if (p0 != null && p1 != null && p2 == null && previewPoint != null) {
            // Preview the second leg (p1 -> mouse) after the first leg is set.
            return new Trendline(UUID.randomUUID(), p1, previewPoint, defaultColor, defaultStroke, createDefaultVisibility());
        }
        return null;
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
        for (Timeframe tf : Timeframe.getStandardTimeframes()) {
            map.put(tf, true);
        }
        return map;
    }
}