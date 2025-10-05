package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.tool.InfoTool;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.chart.render.trading.OrderRenderer;
import com.EcoChartPro.ui.chart.render.trading.OrderRenderer.InteractionType;
import com.EcoChartPro.ui.dialogs.TextSettingsDialog;

import javax.swing.*;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.math.BigDecimal;
import java.util.UUID;

public class ChartController {

    private final ChartDataModel model;
    private final ChartPanel view;
    private final MainWindow mainWindow;
    private final DrawingController drawingController;
    private OrderRenderer.InteractiveZone activeInteractionItem = null;

    // Panning-related fields
    private Point lastMousePoint = null;

    public ChartController(ChartDataModel model, ChartPanel view, MainWindow mainWindow) {
        this.model = model;
        this.view = view;
        this.mainWindow = mainWindow;
        this.drawingController = view.getDrawingController();
        addListeners();
    }

    private void addListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            private double panAccumulator = 0.0;
            private double lockedBarWidthOnDrag = 0.0;
            
            @Override
            public void mouseExited(MouseEvent e) {
                CrosshairManager.getInstance().clearPosition();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                CrosshairManager.getInstance().clearPosition();

                if (view.isPriceSelectionMode()) {
                    BigDecimal price = view.getChartAxis().yToPrice(e.getY());
                    if (price != null && view.getPriceSelectionCallback() != null) view.getPriceSelectionCallback().accept(price);
                    view.exitPriceSelectionMode();
                    e.consume();
                    return;
                }

                // --- FIX: PRIORITIZE TRADING INTERACTION ---
                // First, check for interaction with trading objects.
                if (view.getDataModel().isInReplayMode()) {
                    activeInteractionItem = view.getOrderRenderer().findZoneAt(e.getPoint());
                    if (activeInteractionItem != null) {
                        if (activeInteractionItem.type() == InteractionType.CLOSE_POSITION) {
                            handleClosePositionRequest(activeInteractionItem.objectId());
                            activeInteractionItem = null;
                        } else if (activeInteractionItem.type() == InteractionType.CANCEL_ORDER) {
                            handleCancelOrderRequest(activeInteractionItem.objectId());
                            activeInteractionItem = null;
                        } else { // Draggable item
                            view.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                            ReplaySessionManager.getInstance().pause();
                        }
                        return; // IMPORTANT: A trading item was handled, so stop processing.
                    }
                }
                // --- END FIX ---

                DrawingManager dm = DrawingManager.getInstance();

                // Check if we are performing a drawing action that should prevent panning.
                // The InfoTool is explicitly excluded here to allow panning.
                boolean isDrawingAction = (drawingController.getActiveTool() != null && !(drawingController.getActiveTool() instanceof InfoTool)) ||
                                           drawingController.findHandleAt(e.getPoint()) != null ||
                                           dm.findDrawingAt(e.getPoint(), view.getChartAxis(), model.getVisibleKLines(), model.getCurrentDisplayTimeframe()) != null;

                if (isDrawingAction) {
                    // Let the DrawingController's mousePressed handle it. We return so we don't start a pan.
                    return;
                }
                
                // If we reach here, no drawing action and no trading action is being performed.
                // It's a click on an empty area. De-select any drawings.
                if (dm.getSelectedDrawingId() != null) {
                    dm.setSelectedDrawingId(null);
                    mainWindow.getTitleBarManager().restoreIdleTitle();
                }

                // If nothing else, start a pan.
                lastMousePoint = e.getPoint();
                panAccumulator = 0.0;
                lockedBarWidthOnDrag = view.getChartAxis().getBarWidth();
                if (model.isInReplayMode()) ReplaySessionManager.getInstance().pause();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                 CrosshairManager.getInstance().clearPosition();
                 
                 if (activeInteractionItem != null && isDraggable(activeInteractionItem.type())) {
                    BigDecimal newPrice = view.getChartAxis().yToPrice(e.getY());
                    if (newPrice != null) {
                        OrderRenderer.InteractiveZone preview = new OrderRenderer.InteractiveZone(activeInteractionItem.bounds(), activeInteractionItem.objectId(), activeInteractionItem.type(), newPrice);
                        view.setDragPreview(preview);
                    }
                    return;
                }

                // Check if a drawing drag action is happening. InfoTool is excluded to allow panning.
                boolean isDrawingDrag = (drawingController.getActiveTool() != null && !(drawingController.getActiveTool() instanceof InfoTool)) ||
                                         DrawingManager.getInstance().getSelectedDrawingId() != null;

                if (isDrawingDrag) {
                    return;
                }

                // If we are not dragging a trade or drawing object, pan the chart.
                if (lastMousePoint == null) {
                    lastMousePoint = e.getPoint();
                    return;
                }

                int dx = e.getX() - lastMousePoint.x;
                lastMousePoint = e.getPoint();
                if (lockedBarWidthOnDrag <= 0) return;
                panAccumulator += -dx;
                int barDelta = (int) (panAccumulator / lockedBarWidthOnDrag);
                if (barDelta != 0) {
                    model.pan(barDelta);
                    panAccumulator -= barDelta * lockedBarWidthOnDrag;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Drawing-related release logic is now entirely in DrawingController.
                if (activeInteractionItem != null && isDraggable(activeInteractionItem.type())) {
                    BigDecimal finalPrice = view.getChartAxis().yToPrice(e.getY());
                    if (finalPrice != null) finalizeOrderModification(activeInteractionItem.objectId(), activeInteractionItem.type(), finalPrice);
                    activeInteractionItem = null;
                    view.setDragPreview(null);
                    view.setCursor(Cursor.getDefaultCursor());
                    view.repaint();
                }
                
                // Reset pan state
                lastMousePoint = null;
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (drawingController.getActiveTool() != null || view.isPriceSelectionMode()) return;
                if (e.getWheelRotation() < 0) model.zoom(1.25); else model.zoom(0.8);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    DrawingManager drawingManager = DrawingManager.getInstance();
                    DrawingObject foundDrawing = drawingManager.findDrawingAt(e.getPoint(), view.getChartAxis(), model.getVisibleKLines(), model.getCurrentDisplayTimeframe());

                    if (foundDrawing instanceof TextObject textObject) {
                        e.consume();
                        Frame owner = (Frame) SwingUtilities.getWindowAncestor(view);
                        
                        TextSettingsDialog dialog = new TextSettingsDialog(owner, textObject);
                        dialog.setVisible(true);

                        TextObject result = dialog.getUpdatedTextObject();
                        if (result != null) {
                            TextObject updated = new TextObject(
                                textObject.id(),
                                textObject.anchor(),
                                result.text(),
                                result.font(),
                                result.color(),
                                result.properties(),
                                result.visibility(),
                                textObject.isLocked()
                            );
                            drawingManager.updateDrawing(updated);
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // MODIFICATION: Use the public snapping method from the ChartPanel (view).
                DrawingObjectPoint dataPoint = view.getSnappingPoint(e);
                CrosshairManager.getInstance().updatePosition(dataPoint, view);
                
                // DrawingController's mouseMoved listener handles its own cursor changes for tools and handles.
                // We only handle cursor changes for trading objects here, and only if no drawing action is active.
                if (drawingController.getActiveTool() != null || DrawingManager.getInstance().getSelectedDrawingId() != null) {
                    return;
                }

                if (view.getDataModel().isInReplayMode()) {
                    OrderRenderer.InteractiveZone zone = view.getOrderRenderer().findZoneAt(e.getPoint());
                    if (zone != null) {
                        if (zone.type() == InteractionType.CLOSE_POSITION || zone.type() == InteractionType.CANCEL_ORDER) {
                            view.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        } else {
                            view.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                        }
                    } else {
                        view.setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        };

        view.addMouseListener(mouseAdapter);
        view.addMouseMotionListener(mouseAdapter);
        view.addMouseWheelListener(mouseAdapter);
    }
    
    private boolean isDraggable(InteractionType type) {
        return type == InteractionType.PENDING_ORDER_PRICE || type == InteractionType.STOP_LOSS || type == InteractionType.TAKE_PROFIT;
    }

    /**
     * MODIFICATION: New method to handle cancelling a pending order from the chart.
     */
    private void handleCancelOrderRequest(UUID orderId) {
        Order order = PaperTradingService.getInstance().getPendingOrders().stream()
                .filter(o -> o.id().equals(orderId)).findFirst().orElse(null);
        if (order == null) return;
        
        int choice = JOptionPane.showConfirmDialog(view,
                String.format("Are you sure you want to cancel this %s %s order on %s?",
                        order.type(), order.direction(), order.symbol().name()),
                "Confirm Cancel Order",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
                
        if (choice == JOptionPane.YES_OPTION) {
            PaperTradingService.getInstance().cancelOrder(orderId);
        }
    }


    private void handleClosePositionRequest(UUID positionId) {
        if (model.getCurrentReplayKLine() == null) {
            JOptionPane.showMessageDialog(view, "Cannot close position, replay data not available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Position position = PaperTradingService.getInstance().getOpenPositions().stream().filter(p -> p.id().equals(positionId)).findFirst().orElse(null);
        if (position == null) return;
        int choice = JOptionPane.showConfirmDialog(view, String.format("Are you sure you want to close this %s position on %s at market?", position.direction(), position.symbol().name()), "Confirm Close Position", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) PaperTradingService.getInstance().closePosition(positionId, model.getCurrentReplayKLine());
    }

    private void finalizeOrderModification(UUID objectId, InteractionType dragType, BigDecimal finalPrice) {
        PaperTradingService service = PaperTradingService.getInstance();
        Order pendingOrder = service.getPendingOrders().stream().filter(o -> o.id().equals(objectId)).findFirst().orElse(null);
        Position openPosition = service.getOpenPositions().stream().filter(p -> p.id().equals(objectId)).findFirst().orElse(null);

        if (dragType == InteractionType.PENDING_ORDER_PRICE && pendingOrder != null) {
            BigDecimal priceDelta = finalPrice.subtract(pendingOrder.limitPrice());
            BigDecimal newSL = (pendingOrder.stopLoss() == null) ? null : pendingOrder.stopLoss().add(priceDelta);
            BigDecimal newTP = (pendingOrder.takeProfit() == null) ? null : pendingOrder.takeProfit().add(priceDelta);
            service.modifyOrder(objectId, finalPrice, newSL, newTP, pendingOrder.trailingStopDistance());
        } else if (dragType == InteractionType.STOP_LOSS) {
            if (pendingOrder != null) service.modifyOrder(objectId, pendingOrder.limitPrice(), finalPrice, pendingOrder.takeProfit(), pendingOrder.trailingStopDistance());
            else if (openPosition != null) service.modifyOrder(objectId, null, finalPrice, openPosition.takeProfit(), null);
        } else if (dragType == InteractionType.TAKE_PROFIT) {
            if (pendingOrder != null) service.modifyOrder(objectId, pendingOrder.limitPrice(), pendingOrder.stopLoss(), finalPrice, pendingOrder.trailingStopDistance());
            else if (openPosition != null) service.modifyOrder(objectId, null, openPosition.stopLoss(), finalPrice, openPosition.trailingStopDistance());
        }
    }
}