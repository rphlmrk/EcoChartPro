package com.EcoChartPro.ui.chart.render.trading;

import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.OrderStatus;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import javax.swing.UIManager;

import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders trading-related objects like open positions and pending orders on the chart.
 * It also tracks the screen location of these objects to enable interaction.
 */
public class OrderRenderer {

    // --- Inner classes for interaction ---
    public enum InteractionType { PENDING_ORDER_PRICE, STOP_LOSS, TAKE_PROFIT, CLOSE_POSITION, CANCEL_ORDER }

    public record InteractiveZone(
            Rectangle bounds,
            UUID objectId,
            InteractionType type,
            BigDecimal originalPrice
    ) {}

    // --- Styling Constants ---
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 12);
    private static final Stroke SOLID_STROKE = new BasicStroke(1.5f);
    private static final Stroke DASHED_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{8}, 0);

    private final List<InteractiveZone> interactiveZones = new ArrayList<>();

    public void draw(Graphics2D g, IChartAxis axis, List<Position> positions, List<Order> pendingOrders, InteractiveZone dragPreview) {
        g.setFont(LABEL_FONT);
        interactiveZones.clear(); // Clear zones from the previous frame

        // Draw open positions
        if (positions != null) {
            for (Position pos : positions) {
                drawPosition(g, axis, pos);
            }
        }
        // Draw pending orders
        if (pendingOrders != null) {
            for (Order order : pendingOrders) {
                drawPendingOrder(g, axis, order);
            }
        }
        // Draw a preview of a line being dragged
        if (dragPreview != null) {
            drawDragPreview(g, axis, dragPreview);
        }
    }

    private void drawPosition(Graphics2D g, IChartAxis axis, Position pos) {
        Color entryColor = pos.direction() == TradeDirection.LONG ? UIManager.getColor("app.trading.long") : UIManager.getColor("app.trading.short");

        // Draw entry line
        int yEntry = axis.priceToY(pos.entryPrice());
        g.setColor(entryColor);
        g.setStroke(SOLID_STROKE);
        g.drawLine(0, yEntry, g.getClipBounds().width, yEntry);
        String entryText = String.format("Entry (%s)", pos.size());
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(entryText);
        int textY_baseline = yEntry - 5;
        g.drawString(entryText, 5, textY_baseline);
        
        // Draw a small 'X' button next to the entry text
        g.setColor(UIManager.getColor("Label.foreground"));
        g.setStroke(new BasicStroke(1.5f));
        int buttonX = 5 + textWidth + 5;
        int buttonY = textY_baseline - fm.getAscent() + (fm.getAscent() - 12) / 2;
        Rectangle closeButtonBounds = new Rectangle(buttonX, buttonY, 12, 12);
        g.drawRect(closeButtonBounds.x, closeButtonBounds.y, closeButtonBounds.width, closeButtonBounds.height);
        g.drawLine(closeButtonBounds.x + 3, closeButtonBounds.y + 3, closeButtonBounds.x + 9, closeButtonBounds.y + 9);
        g.drawLine(closeButtonBounds.x + 3, closeButtonBounds.y + 9, closeButtonBounds.x + 9, closeButtonBounds.y + 3);

        // Draw Stop Loss
        if (pos.stopLoss() != null) {
            int ySL = axis.priceToY(pos.stopLoss());
            g.setColor(UIManager.getColor("app.trading.short"));
            g.setStroke(DASHED_STROKE);
            g.drawLine(0, ySL, g.getClipBounds().width, ySL);
            g.drawString("SL", 5, ySL - 5);
            addInteractiveZone(pos.id(), ySL, pos.stopLoss(), InteractionType.STOP_LOSS, g.getClipBounds());
        }

        // Draw Take Profit
        if (pos.takeProfit() != null) {
            int yTP = axis.priceToY(pos.takeProfit());
            g.setColor(UIManager.getColor("app.trading.long"));
            g.setStroke(DASHED_STROKE);
            g.drawLine(0, yTP, g.getClipBounds().width, yTP);
            g.drawString("TP", 5, yTP - 5);
            addInteractiveZone(pos.id(), yTP, pos.takeProfit(), InteractionType.TAKE_PROFIT, g.getClipBounds());
        }
        
        addInteractiveZone(pos.id(), closeButtonBounds, pos.entryPrice(), InteractionType.CLOSE_POSITION);
    }

    private void drawPendingOrder(Graphics2D g, IChartAxis axis, Order order) {
        if (order.status() != OrderStatus.PENDING || order.limitPrice() == null) return;

        int yPrice = axis.priceToY(order.limitPrice());
        g.setColor(UIManager.getColor("app.trading.pending"));
        g.setStroke(DASHED_STROKE);
        g.drawLine(0, yPrice, g.getClipBounds().width, yPrice);
        
        String orderText = String.format("%s %s (%s)", order.type(), order.direction(), order.size());
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(orderText);
        int textY_baseline = yPrice - 5;
        g.drawString(orderText, 5, textY_baseline);

        // Draw the cancel 'X' button
        g.setColor(UIManager.getColor("Label.foreground"));
        g.setStroke(new BasicStroke(1.5f));
        int buttonX = 5 + textWidth + 5;
        int buttonY = textY_baseline - fm.getAscent() + (fm.getAscent() - 12) / 2;
        Rectangle cancelButtonBounds = new Rectangle(buttonX, buttonY, 12, 12);
        g.drawRect(cancelButtonBounds.x, cancelButtonBounds.y, cancelButtonBounds.width, cancelButtonBounds.height);
        g.drawLine(cancelButtonBounds.x + 3, cancelButtonBounds.y + 3, cancelButtonBounds.x + 9, cancelButtonBounds.y + 9);
        g.drawLine(cancelButtonBounds.x + 3, cancelButtonBounds.y + 9, cancelButtonBounds.x + 9, cancelButtonBounds.y + 3);

        addInteractiveZone(order.id(), yPrice, order.limitPrice(), InteractionType.PENDING_ORDER_PRICE, g.getClipBounds());
        addInteractiveZone(order.id(), cancelButtonBounds, null, InteractionType.CANCEL_ORDER);

        // Draw associated SL/TP for the pending order
        if (order.stopLoss() != null) {
            int ySL = axis.priceToY(order.stopLoss());
            g.setColor(UIManager.getColor("app.trading.short"));
            g.drawLine(0, ySL, g.getClipBounds().width, ySL);
            addInteractiveZone(order.id(), ySL, order.stopLoss(), InteractionType.STOP_LOSS, g.getClipBounds());
        }
        if (order.takeProfit() != null) {
            int yTP = axis.priceToY(order.takeProfit());
            g.setColor(UIManager.getColor("app.trading.long"));
            g.drawLine(0, yTP, g.getClipBounds().width, yTP);
            addInteractiveZone(order.id(), yTP, order.takeProfit(), InteractionType.TAKE_PROFIT, g.getClipBounds());
        }
    }

    private void drawDragPreview(Graphics2D g, IChartAxis axis, InteractiveZone preview) {
        Color previewColor = switch(preview.type) {
            case PENDING_ORDER_PRICE -> UIManager.getColor("app.trading.pending");
            case STOP_LOSS -> UIManager.getColor("app.trading.short");
            case TAKE_PROFIT -> UIManager.getColor("app.trading.long");
            default -> UIManager.getColor("Label.foreground"); // Default for others like CLOSE/CANCEL
        };
        g.setColor(previewColor);
        g.setStroke(DASHED_STROKE);
        int y = axis.priceToY(preview.originalPrice()); // In this context, originalPrice holds the current dragged price
        g.drawLine(0, y, g.getClipBounds().width, y);
        g.drawString(String.format("%.2f", preview.originalPrice()), g.getClipBounds().width - 60, y - 5);
    }

    private void addInteractiveZone(UUID id, Rectangle bounds, BigDecimal price, InteractionType type) {
        interactiveZones.add(new InteractiveZone(bounds, id, type, price));
    }


    private void addInteractiveZone(UUID id, int y, BigDecimal price, InteractionType type, Rectangle clipBounds) {
        int interactionHeight = 10;
        Rectangle bounds = new Rectangle(0, y - interactionHeight / 2, clipBounds.width, interactionHeight);
        interactiveZones.add(new InteractiveZone(bounds, id, type, price));
    }

    public InteractiveZone findZoneAt(Point p) {
        // Check precise button zones first
        for (InteractiveZone zone : interactiveZones) {
            if (zone.type() == InteractionType.CLOSE_POSITION || zone.type() == InteractionType.CANCEL_ORDER) {
                if (zone.bounds().contains(p)) {
                    return zone;
                }
            }
        }
        // Then check full-width line zones
        for (InteractiveZone zone : interactiveZones) {
             if (zone.type() != InteractionType.CLOSE_POSITION && zone.type() != InteractionType.CANCEL_ORDER) {
                if (zone.bounds().contains(p)) {
                    return zone;
                }
            }
        }
        return null;
    }
}