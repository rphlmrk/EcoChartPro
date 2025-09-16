package com.EcoChartPro.core.tool;

import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.model.drawing.TextProperties;
import com.EcoChartPro.ui.dialogs.TextSettingsDialog;

import javax.swing.*;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AnchoredTextTool implements DrawingTool {

    private DrawingObjectPoint screenAnchorPoint;
    private TextObject finalObject;

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (screenAnchorPoint == null) {
            // This tool creates a SCREEN-anchored text object.
            // We ignore the data point and use the raw mouse event coordinates from the ChartPanel.
            // We abuse DrawingObjectPoint to store screen coordinates: X in timestamp, Y in price.
            Instant xCoord = Instant.ofEpochMilli(e.getX());
            BigDecimal yCoord = new BigDecimal(e.getY());
            this.screenAnchorPoint = new DrawingObjectPoint(xCoord, yCoord);

            Frame owner = (Frame) SwingUtilities.getWindowAncestor((Component) e.getSource());
            TextSettingsDialog dialog = new TextSettingsDialog(owner, null);
            dialog.setVisible(true);

            TextObject result = dialog.getUpdatedTextObject();
            if (result != null) {
                // Create a new TextProperties object that forces screen anchoring.
                TextProperties screenAnchoredProps = new TextProperties(
                    result.properties().showBackground(),
                    result.properties().backgroundColor(),
                    result.properties().showBorder(),
                    result.properties().borderColor(),
                    result.properties().wrapText(),
                    true // This is the key change: force screen anchoring
                );

                this.finalObject = new TextObject(
                    UUID.randomUUID(),
                    this.screenAnchorPoint,
                    result.text(),
                    result.font(),
                    result.color(),
                    screenAnchoredProps,
                    result.visibility(),
                    false,
                    false
                );
            } else {
                reset(); // User cancelled
            }
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {}

    @Override
    public DrawingObject getDrawingObject() {
        return finalObject;
    }

    @Override
    public DrawingObject getPreviewObject() {
        return null;
    }

    @Override
    public boolean isFinished() {
        return finalObject != null;
    }

    @Override
    public void reset() {
        screenAnchorPoint = null;
        finalObject = null;
    }
}