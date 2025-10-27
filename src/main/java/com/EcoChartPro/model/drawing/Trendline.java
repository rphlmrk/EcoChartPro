package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a Trendline drawing object.
 * It is defined by two data points (start and end) and styling information.
 */
public record Trendline(
    UUID id,
    DrawingObjectPoint start,
    DrawingObjectPoint end,
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
    public Trendline(UUID id, DrawingObjectPoint start, DrawingObjectPoint end, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, start, end, color, stroke, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public Trendline(UUID id, DrawingObjectPoint start, DrawingObjectPoint end, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, start, end, color, stroke, visibility, isLocked, false);
    }


    @Override
    public UUID id() { return this.id; }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new Trendline(id, start, end, color, stroke, newVisibility, isLocked, showPriceLabel);
    }
    
    @Override
    public DrawingObject withColor(Color newColor) {
        return new Trendline(id, start, end, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new Trendline(id, start, end, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        int x1 = axis.timeToX(this.start().timestamp(), data, tf);
        int y1 = axis.priceToY(this.start().price());
        int x2 = axis.timeToX(this.end().timestamp(), data, tf);
        int y2 = axis.priceToY(this.end().price());

        UUID selectedId = DrawingManager.getInstance().getSelectedDrawingId();
        boolean isSelected = this.id().equals(selectedId);
        g.setColor(this.color());
        Stroke originalStroke = g.getStroke();
        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke());
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(originalStroke);

        if (isSelected && !isLocked) {
            getHandles(axis, data, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }

    @Override
    public boolean isHit(Point screenPoint, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        Point p1 = new Point(axis.timeToX(this.start.timestamp(), data, tf), axis.priceToY(this.start.price()));
        Point p2 = new Point(axis.timeToX(this.end.timestamp(), data, tf), axis.priceToY(this.end.price()));
        return distanceToLineSegment(screenPoint.x, screenPoint.y, p1.x, p1.y, p2.x, p2.y) < SettingsManager.getInstance().getDrawingHitThreshold();
    }
    
    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        Instant minTime = start.timestamp().isBefore(end.timestamp()) ? start.timestamp() : end.timestamp();
        Instant maxTime = start.timestamp().isAfter(end.timestamp()) ? start.timestamp() : end.timestamp();
        BigDecimal minPrice = start.price().min(end.price());
        BigDecimal maxPrice = start.price().max(end.price());
        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;
        return timeOverlap && priceOverlap;
    }

    @Override
    public List<DrawingHandle> getHandles(IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        int x1 = axis.timeToX(start.timestamp(), data, tf);
        int y1 = axis.priceToY(start.price());
        int x2 = axis.timeToX(end.timestamp(), data, tf);
        int y2 = axis.priceToY(end.price());
        handles.add(new DrawingHandle(new Point(x1, y1), DrawingHandle.HandleType.START_POINT, id));
        handles.add(new DrawingHandle(new Point(x2, y2), DrawingHandle.HandleType.END_POINT, id));
        return handles;
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return switch (handleType) {
            case START_POINT -> new Trendline(id, newPoint, end, color, stroke, visibility, isLocked, showPriceLabel);
            case END_POINT -> new Trendline(id, start, newPoint, color, stroke, visibility, isLocked, showPriceLabel);
            default -> this;
        };
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newStart = new DrawingObjectPoint(start.timestamp().plusMillis(timeDelta), start.price().add(priceDelta));
        DrawingObjectPoint newEnd = new DrawingObjectPoint(end.timestamp().plusMillis(timeDelta), end.price().add(priceDelta));
        return new Trendline(id, newStart, newEnd, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new Trendline(id, start, end, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new Trendline(id, start, end, color, stroke, visibility, isLocked, show);
    }

    private void drawHandle(Graphics2D g, Point position) {
        int handleSize = SettingsManager.getInstance().getDrawingHandleSize();
        int x = position.x - handleSize / 2;
        int y = position.y - handleSize / 2;
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(HANDLE_FILL_COLOR);
        g.fillRect(x, y, handleSize, handleSize);
        g.setColor(HANDLE_STROKE_COLOR);
        g.drawRect(x, y, handleSize, handleSize);
    }

    private static double distanceToLineSegment(int x, int y, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) return Math.hypot(x - x1, y - y1);
        double t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(x - closestX, y - closestY);
    }
}