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
import java.util.UUID;

public class TextTool implements DrawingTool {

    private DrawingObjectPoint anchorPoint;
    private TextObject finalObject;

    @Override
    public void mousePressed(DrawingObjectPoint point, MouseEvent e) {
        if (anchorPoint == null) {
            this.anchorPoint = point;
            Frame owner = (Frame) SwingUtilities.getWindowAncestor((Component) e.getSource());

            TextSettingsDialog dialog = new TextSettingsDialog(owner, null);
            dialog.setVisible(true);

            TextObject result = dialog.getUpdatedTextObject();
            if (result != null) {
                // Ensure properties from dialog are used, but override to guarantee it is NOT screen anchored
                TextProperties dataAnchoredProps = new TextProperties(
                    result.properties().showBackground(),
                    result.properties().backgroundColor(),
                    result.properties().showBorder(),
                    result.properties().borderColor(),
                    result.properties().wrapText(),
                    false // This is a data-anchored text object
                );
                
                this.finalObject = new TextObject(
                    UUID.randomUUID(),
                    this.anchorPoint,
                    result.text(),
                    result.font(),
                    result.color(),
                    dataAnchoredProps,
                    result.visibility(),
                    false,
                    false
                );
            } else {
                reset();
            }
        }
    }

    @Override
    public void mouseMoved(DrawingObjectPoint point, MouseEvent e) {
        // No preview needed
    }

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
        anchorPoint = null;
        finalObject = null;
    }
}