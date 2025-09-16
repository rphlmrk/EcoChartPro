package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.commands.UpdateDrawingCommand;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.tool.DrawingTool;
import com.EcoChartPro.model.drawing.DrawingHandle;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

public class DrawingController extends MouseAdapter {

    private final ChartPanel chartPanel;
    private final DrawingManager drawingManager;
    private DrawingTool activeTool;
    private DrawingHandle activeHandle;
    private DrawingObject stateBeforeDrag;
    private boolean isDraggingObject = false;
    private DrawingObjectPoint dragStartPoint = null;
    private final Consumer<Boolean> onToolStateChange;

    public DrawingController(ChartPanel chartPanel, Consumer<Boolean> onToolStateChange) {
        this.chartPanel = chartPanel;
        this.drawingManager = DrawingManager.getInstance();
        this.onToolStateChange = onToolStateChange;
        chartPanel.addMouseListener(this);
        chartPanel.addMouseMotionListener(this);
    }

    public void setActiveTool(DrawingTool tool) {
        if (this.activeTool != tool) {
            this.activeTool = tool;
            drawingManager.setSelectedDrawingId(null);
            if (onToolStateChange != null) onToolStateChange.accept(tool != null);
        }
        chartPanel.setCursor(tool != null ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
    }

    public DrawingTool getActiveTool() {
        return activeTool;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (chartPanel.isPriceSelectionMode()) return;
        if (!chartPanel.getChartAxis().isConfigured()) return;
        
        // Use the centralized snapping method from the ChartPanel.
        DrawingObjectPoint dataPoint = chartPanel.getSnappingPoint(e);

        if (dataPoint == null) return;

        if (SwingUtilities.isRightMouseButton(e)) {
            if (activeTool != null) {
                activeTool.reset();
                setActiveTool(null);
            }
            return;
        }

        if (activeTool != null) {
            activeTool.mousePressed(dataPoint, e);
            if (activeTool.isFinished()) {
                drawingManager.addDrawing(activeTool.getDrawingObject());
                activeTool.reset();
                setActiveTool(null);
            } else {
                chartPanel.repaint(); // Repaint for preview
            }
        } else {
            // Check for handle interaction first
            DrawingHandle handle = findHandleAt(e.getPoint());
            if (handle != null) {
                activeHandle = handle;
                stateBeforeDrag = drawingManager.getDrawingById(handle.parentDrawingId());
                drawingManager.setSelectedDrawingId(handle.parentDrawingId());
            } else {
                // If not a handle, check for object body interaction
                DrawingObject clickedObject = drawingManager.findDrawingAt(e.getPoint(), chartPanel.getChartAxis(), chartPanel.getDataModel().getVisibleKLines(), chartPanel.getDataModel().getCurrentDisplayTimeframe());
                if (clickedObject != null && !clickedObject.isLocked()) {
                    isDraggingObject = true;
                    dragStartPoint = dataPoint;
                    stateBeforeDrag = clickedObject;
                    drawingManager.setSelectedDrawingId(clickedObject.id());
                    chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else if (clickedObject == null) {
                    // Clicked on empty space, deselect
                    drawingManager.setSelectedDrawingId(null);
                }
            }
        }
        chartPanel.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // Use the centralized snapping method from the ChartPanel.
        DrawingObjectPoint newPoint = chartPanel.getSnappingPoint(e);
        if (newPoint == null) return;
        
        // Handle dragging a resize/move handle
        if (activeHandle != null && stateBeforeDrag != null && !stateBeforeDrag.isLocked()) {
            DrawingObject currentDrawing = drawingManager.getDrawingById(activeHandle.parentDrawingId());
            if (currentDrawing != null) {
                DrawingObject updatedDrawing = currentDrawing.withPoint(activeHandle.type(), newPoint);
                drawingManager.updateDrawingPreview(updatedDrawing);
            }

        } else if (isDraggingObject && stateBeforeDrag != null && dragStartPoint != null) {
            long timeDelta = newPoint.timestamp().toEpochMilli() - dragStartPoint.timestamp().toEpochMilli();
            BigDecimal priceDelta = newPoint.price().subtract(dragStartPoint.price());
            DrawingObject updatedDrawing = stateBeforeDrag.move(timeDelta, priceDelta);
            drawingManager.updateDrawingPreview(updatedDrawing);
        // Handle preview while creating a new drawing
        } else if (activeTool != null) {
             activeTool.mouseMoved(newPoint, e);
             chartPanel.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Finalize a handle drag operation
        if (activeHandle != null && stateBeforeDrag != null) {
            DrawingObject stateAfterDrag = drawingManager.getDrawingById(activeHandle.parentDrawingId());
            if (stateAfterDrag != null && !stateBeforeDrag.equals(stateAfterDrag)) {
                UpdateDrawingCommand command = new UpdateDrawingCommand(stateBeforeDrag, stateAfterDrag);
                UndoManager.getInstance().addCommandToHistory(command);
            }

        } else if (isDraggingObject && stateBeforeDrag != null) {
            DrawingObject stateAfterDrag = drawingManager.getDrawingById(stateBeforeDrag.id());
             if (stateAfterDrag != null && !stateBeforeDrag.equals(stateAfterDrag)) {
                UpdateDrawingCommand command = new UpdateDrawingCommand(stateBeforeDrag, stateAfterDrag);
                UndoManager.getInstance().addCommandToHistory(command);
            }
        }

        // Reset all drag/handle states
        activeHandle = null;
        stateBeforeDrag = null;
        isDraggingObject = false;
        dragStartPoint = null;
        if (activeTool == null) {
            chartPanel.setCursor(Cursor.getDefaultCursor());
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Use the centralized snapping method from the ChartPanel.
        DrawingObjectPoint dataPoint = chartPanel.getSnappingPoint(e);

        if (activeTool != null) {
            if (dataPoint != null) {
                activeTool.mouseMoved(dataPoint, e);
                chartPanel.repaint();
            }
        } else {
            // Change cursor if hovering over a handle or a draggable object
            DrawingHandle handle = findHandleAt(e.getPoint());
            if (handle != null) {
                chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else {
                DrawingObject foundObject = drawingManager.findDrawingAt(e.getPoint(), chartPanel.getChartAxis(), chartPanel.getDataModel().getVisibleKLines(), chartPanel.getDataModel().getCurrentDisplayTimeframe());
                if (foundObject != null && !foundObject.isLocked()) {
                     chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                } else {
                     chartPanel.setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }

    /**
     * MODIFICATION: Made this method public to be accessible from ChartController.
     * @param screenPoint The point on the screen to check.
     * @return The handle at that point, or null if none is found.
     */
    public DrawingHandle findHandleAt(Point screenPoint) {
        UUID selectedId = drawingManager.getSelectedDrawingId();
        if (selectedId == null) return null;

        DrawingObject selectedDrawing = drawingManager.getDrawingById(selectedId);
        if (selectedDrawing == null || selectedDrawing.isLocked()) return null;

        java.util.List<DrawingHandle> handles = selectedDrawing.getHandles(chartPanel.getChartAxis(), chartPanel.getDataModel().getVisibleKLines(), chartPanel.getDataModel().getCurrentDisplayTimeframe());
        for (DrawingHandle handle : handles) {
            if (handle.position().distance(screenPoint) < SettingsManager.getInstance().getDrawingHitThreshold()) {
                return handle;
            }
        }
        return null;
    }
}