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
import com.EcoChartPro.ui.chart.axis.ChartAxis;
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
import java.beans.PropertyChangeEvent;
import java.math.BigDecimal;
import java.util.UUID;

public class ChartController {

    private final ChartDataModel model;
    private final ChartInteractionManager interactionManager;
    private final ChartPanel view;
    private final MainWindow mainWindow;
    private final DrawingController drawingController;
    private OrderRenderer.InteractiveZone activeInteractionItem = null;

    // --- Unified Panning Fields ---
    private Point lastMousePoint = null; // For calculating delta in mouseDragged
    private Point dragStartPoint = null; // For vertical panning calculations
    private BigDecimal dragStartMinPrice = null;
    private BigDecimal dragStartMaxPrice = null;

    public ChartController(ChartDataModel model, ChartInteractionManager interactionManager, ChartPanel view, MainWindow mainWindow) {
        this.model = model;
        this.interactionManager = interactionManager;
        this.view = view;
        this.mainWindow = mainWindow;
        this.drawingController = view.getDrawingController();
        addListeners();
        model.addPropertyChangeListener("liveCandleAdded", this::handleLiveCandleUpdate);
        model.addPropertyChangeListener("liveTickReceived", this::handleLiveTickUpdate);
    }

    /**
     * [MODIFIED] Handles finalized live candle updates from the model.
     * This handler ensures that order execution logic is only run for the active chart in live mode.
     * This prevents duplicate processing if multiple charts of the same symbol are open.
     */
    private void handleLiveCandleUpdate(PropertyChangeEvent evt) {
        // [FIX] Replaced model.getCurrentMode() == ChartMode.LIVE with !model.isInReplayMode()
        if (!model.isInReplayMode() && view == mainWindow.getActiveChartPanel()) {
            if (evt.getNewValue() instanceof com.EcoChartPro.model.KLine) {
                PaperTradingService.getInstance().onBarUpdate((com.EcoChartPro.model.KLine) evt.getNewValue());
            }
        }
    }

    /**
     * [NEW] Handles live price tick updates from the model.
     * This handler is responsible for triggering real-time P&L updates for open positions.
     * It only acts if it's controlling the currently active chart panel.
     */
    private void handleLiveTickUpdate(PropertyChangeEvent evt) {
        // [FIX] Replaced model.getCurrentMode() == ChartMode.LIVE with !model.isInReplayMode()
        if (!model.isInReplayMode() && view == mainWindow.getActiveChartPanel()) {
            if (evt.getNewValue() instanceof com.EcoChartPro.model.KLine) {
                PaperTradingService.getInstance().updateLivePnl((com.EcoChartPro.model.KLine) evt.getNewValue());
            }
        }
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
                    if (price != null && view.getPriceSelectionCallback() != null) {
                        view.getPriceSelectionCallback().accept(price);
                    }
                    view.exitPriceSelectionMode();
                    e.consume();
                    return;
                }

                // --- TRADING INTERACTION ---
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

                DrawingManager dm = DrawingManager.getInstance();

                // --- DRAWING ACTION ---
                boolean isDrawingAction = (drawingController.getActiveTool() != null && !(drawingController.getActiveTool() instanceof InfoTool)) ||
                                           drawingController.findHandleAt(e.getPoint()) != null ||
                                           dm.findDrawingAt(e.getPoint(), view.getChartAxis(), model.getVisibleKLines(), model.getCurrentDisplayTimeframe()) != null;

                if (isDrawingAction) {
                    // Let the DrawingController's mousePressed handle it.
                    return;
                }
                
                // If we reach here, it's a pan operation. Deselect any drawing.
                if (dm.getSelectedDrawingId() != null) {
                    dm.setSelectedDrawingId(null);
                    mainWindow.getTitleBarManager().restoreIdleTitle();
                }

                // --- UNIFIED PANNING SETUP ---
                view.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                lastMousePoint = e.getPoint();

                // Setup for horizontal pan
                panAccumulator = 0.0;
                lockedBarWidthOnDrag = view.getChartAxis().getBarWidth();

                // Setup for vertical pan (only if in manual mode)
                if (!interactionManager.isAutoScalingY()) {
                    dragStartPoint = e.getPoint();
                    dragStartMinPrice = interactionManager.getManualMinPrice();
                    dragStartMaxPrice = interactionManager.getManualMaxPrice();
                } else {
                    dragStartPoint = null;
                    dragStartMinPrice = null;
                    dragStartMaxPrice = null;
                }

                // Only pause if we are actually in a replay session that can be paused.
                // [FIX] Replaced model.getCurrentMode() == ChartMode.REPLAY with model.isInReplayMode()
                if (model.isInReplayMode()) ReplaySessionManager.getInstance().pause();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                CrosshairManager.getInstance().clearPosition();
                 
                // --- 1. TRADING ITEM DRAG ---
                if (activeInteractionItem != null && isDraggable(activeInteractionItem.type())) {
                    BigDecimal newPrice = view.getChartAxis().yToPrice(e.getY());
                    if (newPrice != null) {
                        OrderRenderer.InteractiveZone preview = new OrderRenderer.InteractiveZone(activeInteractionItem.bounds(), activeInteractionItem.objectId(), activeInteractionItem.type(), newPrice);
                        view.setDragPreview(preview);
                    }
                    return; // Trade drag handled.
                }
                
                // --- 2. DRAWING DRAG ---
                boolean isDrawingDrag = (drawingController.getActiveTool() != null && !(drawingController.getActiveTool() instanceof InfoTool)) ||
                                         DrawingManager.getInstance().getSelectedDrawingId() != null;

                if (isDrawingDrag) {
                    return; // Drawing drag is handled by DrawingController.
                }

                // --- 3. UNIFIED CHART PANNING (DEFAULT) ---
                if (lastMousePoint == null) {
                    lastMousePoint = e.getPoint();
                    return;
                }

                // A. Vertical Panning (if applicable)
                if (!interactionManager.isAutoScalingY() && dragStartPoint != null && dragStartMinPrice != null && view.getChartAxis().isConfigured()) {
                    ChartAxis yAxis = view.getChartAxis();
                    BigDecimal priceAtStart = yAxis.yToPrice(dragStartPoint.y);
                    BigDecimal priceAtCurrent = yAxis.yToPrice(e.getY());
                    BigDecimal priceDelta = priceAtStart.subtract(priceAtCurrent);

                    BigDecimal newMin = dragStartMinPrice.add(priceDelta);
                    BigDecimal newMax = dragStartMaxPrice.add(priceDelta);

                    interactionManager.setManualPriceRange(newMin, newMax);
                }

                // B. Horizontal Panning
                int dx = e.getX() - lastMousePoint.x;
                if (lockedBarWidthOnDrag > 0) {
                    panAccumulator += -dx;
                    int barDelta = (int) (panAccumulator / lockedBarWidthOnDrag);
                    if (barDelta != 0) {
                        interactionManager.pan(barDelta);
                        panAccumulator -= barDelta * lockedBarWidthOnDrag;
                    }
                }
                
                // C. Update state for next drag event
                lastMousePoint = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // --- UNIFIED PANNING RESET ---
                lastMousePoint = null;
                dragStartPoint = null;
                dragStartMinPrice = null;
                dragStartMaxPrice = null;
                view.setCursor(Cursor.getDefaultCursor());

                // --- TRADING ITEM RELEASE ---
                if (activeInteractionItem != null && isDraggable(activeInteractionItem.type())) {
                    BigDecimal finalPrice = view.getChartAxis().yToPrice(e.getY());
                    if (finalPrice != null) finalizeOrderModification(activeInteractionItem.objectId(), activeInteractionItem.type(), finalPrice);
                    activeInteractionItem = null;
                    view.setDragPreview(null);
                    // The cursor will be reset above, no need to set it twice
                    view.repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (drawingController.getActiveTool() != null || view.isPriceSelectionMode() || view.getWidth() <= 0) return;
                
                double zoomFactor = e.getWheelRotation() < 0 ? 1.25 : 0.8;
                double cursorXRatio = (double) e.getX() / view.getWidth();
                
                interactionManager.zoom(zoomFactor, cursorXRatio);
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