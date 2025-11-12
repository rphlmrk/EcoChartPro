package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a Rectangle drawing object.
 * It is defined by two corner data points and styling information.
 */
public record RectangleObject(
    UUID id,
    DrawingObjectPoint corner1,
    DrawingObjectPoint corner2,
    Color color,
    BasicStroke stroke,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    boolean showPriceLabel
) implements DrawingObject {

    private static final Color HANDLE_FILL_COLOR = Color.WHITE;
    private static final Color HANDLE_STROKE_COLOR = Color.BLACK;
    private static final Stroke SELECTED_STROKE = new BasicStroke(
        3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0
    );

    /**
     * Overloaded constructor for creating new, unlocked objects.
     */
    public RectangleObject(UUID id, DrawingObjectPoint corner1, DrawingObjectPoint corner2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, corner1, corner2, color, stroke, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public RectangleObject(UUID id, DrawingObjectPoint corner1, DrawingObjectPoint corner2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, corner1, corner2, color, stroke, visibility, isLocked, false);
    }

    @Override
    public UUID id() { return this.id; }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new RectangleObject(id, corner1, corner2, color, stroke, newVisibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withColor(Color newColor) {
        return new RectangleObject(id, corner1, corner2, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new RectangleObject(id, corner1, corner2, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf, boolean isSelected) {
        Point p1 = getScreenPoint(corner1, axis, klines, tf);
        Point p2 = getScreenPoint(corner2, axis, klines, tf);
        Rectangle r = getScreenBounds(p1, p2);

        Stroke originalStroke = g.getStroke();

        Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 30);
        g.setColor(fillColor);
        g.fillRect(r.x, r.y, r.width, r.height);

        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke());
        
        g.setColor(this.color());
        g.drawRect(r.x, r.y, r.width, r.height);

        if (isSelected && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
        
        g.setStroke(originalStroke);
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        Point p1 = getScreenPoint(corner1, axis, klines, tf);
        Point p2 = getScreenPoint(corner2, axis, klines, tf);
        Rectangle bounds = getScreenBounds(p1, p2);
        bounds.grow(SettingsService.getInstance().getDrawingHitThreshold() / 2, SettingsService.getInstance().getDrawingHitThreshold() / 2);
        return bounds.contains(screenPoint);
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        Instant minTime = corner1.timestamp().isBefore(corner2.timestamp()) ? corner1.timestamp() : corner2.timestamp();
        Instant maxTime = corner1.timestamp().isAfter(corner2.timestamp()) ? corner1.timestamp() : corner2.timestamp();
        BigDecimal minPrice = corner1.price().min(corner2.price());
        BigDecimal maxPrice = corner1.price().max(corner2.price());
        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;
        return timeOverlap && priceOverlap;
    }

    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        Point p1 = getScreenPoint(corner1, axis, klines, tf);
        Point p2 = getScreenPoint(corner2, axis, klines, tf);
        Rectangle r = getScreenBounds(p1, p2);
        handles.add(new DrawingHandle(new Point(r.x, r.y), DrawingHandle.HandleType.TOP_LEFT, id));
        handles.add(new DrawingHandle(new Point(r.x + r.width, r.y), DrawingHandle.HandleType.TOP_RIGHT, id));
        handles.add(new DrawingHandle(new Point(r.x, r.y + r.height), DrawingHandle.HandleType.BOTTOM_LEFT, id));
        handles.add(new DrawingHandle(new Point(r.x + r.width, r.y + r.height), DrawingHandle.HandleType.BOTTOM_RIGHT, id));
        return handles;
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return switch (handleType) {
            case TOP_LEFT -> new RectangleObject(id, newPoint, corner2, color, stroke, visibility, isLocked, showPriceLabel);
            case TOP_RIGHT -> new RectangleObject(id, new DrawingObjectPoint(corner2.timestamp(), newPoint.price()), new DrawingObjectPoint(newPoint.timestamp(), corner2.price()), color, stroke, visibility, isLocked, showPriceLabel);
            case BOTTOM_LEFT -> new RectangleObject(id, new DrawingObjectPoint(newPoint.timestamp(), corner2.price()), new DrawingObjectPoint(corner2.timestamp(), newPoint.price()), color, stroke, visibility, isLocked, showPriceLabel);
            case BOTTOM_RIGHT -> new RectangleObject(id, corner1, newPoint, color, stroke, visibility, isLocked, showPriceLabel);
            default -> this;
        };
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newCorner1 = new DrawingObjectPoint(corner1.timestamp().plusMillis(timeDelta), corner1.price().add(priceDelta));
        DrawingObjectPoint newCorner2 = new DrawingObjectPoint(corner2.timestamp().plusMillis(timeDelta), corner2.price().add(priceDelta));
        return new RectangleObject(id, newCorner1, newCorner2, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new RectangleObject(id, corner1, corner2, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new RectangleObject(id, corner1, corner2, color, stroke, visibility, isLocked, show);
    }

    private Point getScreenPoint(DrawingObjectPoint p, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        return new Point(axis.timeToX(p.timestamp(), klines, tf), axis.priceToY(p.price()));
    }
    
    private Rectangle getScreenBounds(Point p1, Point p2) {
        return new Rectangle(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.abs(p1.x - p2.x), Math.abs(p1.y - p2.y));
    }

    private void drawHandle(Graphics2D g, Point position) {
        int handleSize = SettingsService.getInstance().getDrawingHandleSize();
        int x = position.x - handleSize / 2;
        int y = position.y - handleSize / 2;
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(HANDLE_FILL_COLOR);
        g.fillRect(x, y, handleSize, handleSize);
        g.setColor(HANDLE_STROKE_COLOR);
        g.drawRect(x, y, handleSize, handleSize);
    }
}