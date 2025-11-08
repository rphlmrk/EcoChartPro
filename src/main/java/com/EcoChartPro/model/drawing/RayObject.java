package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RayObject(
    UUID id,
    DrawingObjectPoint start,
    DrawingObjectPoint end,
    Color color,
    BasicStroke stroke,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    boolean showPriceLabel
) implements DrawingObject {

    private static final Stroke SELECTED_STROKE = new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

    /**
     * Overloaded constructor for creating new, unlocked objects.
     */
    public RayObject(UUID id, DrawingObjectPoint start, DrawingObjectPoint end, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, start, end, color, stroke, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public RayObject(UUID id, DrawingObjectPoint start, DrawingObjectPoint end, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, start, end, color, stroke, visibility, isLocked, false);
    }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new RayObject(id, start, end, color, stroke, newVisibility, isLocked, showPriceLabel);
    }
    
    @Override
    public DrawingObject withColor(Color newColor) {
        return new RayObject(id, start, end, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new RayObject(id, start, end, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured()) return;
        
        Point p1 = new Point(axis.timeToX(start.timestamp(), klines, tf), axis.priceToY(start.price()));
        Point p2 = new Point(axis.timeToX(end.timestamp(), klines, tf), axis.priceToY(end.price()));
        
        double dx = p2.x - p1.x;
        double dy = p2.y - p1.y;

        int xEnd, yEnd;
        int chartWidth = g.getClipBounds().width;

        if (dx == 0) { // Vertical ray
            xEnd = p1.x;
            yEnd = dy > 0 ? g.getClipBounds().height : 0;
        } else {
            double slope = dy / dx;
            if (dx > 0) { // Ray goes right
                xEnd = chartWidth;
            } else { // Ray goes left
                xEnd = 0;
            }
            yEnd = (int) (p1.y + slope * (xEnd - p1.x));
        }

        boolean isSelected = id.equals(DrawingManager.getInstance().getSelectedDrawingId());
        Stroke originalStroke = g.getStroke();
        g.setColor(this.color);
        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke);
        g.drawLine(p1.x, p1.y, xEnd, yEnd);
        g.setStroke(originalStroke);

        if (isSelected && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        Point p1 = new Point(axis.timeToX(this.start.timestamp(), klines, tf), axis.priceToY(this.start.price()));
        Point p2 = new Point(axis.timeToX(this.end.timestamp(), klines, tf), axis.priceToY(this.end.price()));
        
        if ((p2.x > p1.x && screenPoint.x < p1.x) || (p2.x < p1.x && screenPoint.x > p1.x)) {
            return false;
        }
        
        double distance = distanceToLine(screenPoint, p1, p2);
        return distance < SettingsService.getInstance().getDrawingHitThreshold();
    }
    
    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        // Check if the bounding box of the ray's defining segment intersects the view.
        // This correctly handles cases where the start point is off-screen but the ray enters the view.
        Instant minTime = start.timestamp().isBefore(end.timestamp()) ? start.timestamp() : end.timestamp();
        Instant maxTime = start.timestamp().isAfter(end.timestamp()) ? start.timestamp() : end.timestamp();
        BigDecimal minPrice = start.price().min(end.price());
        BigDecimal maxPrice = start.price().max(end.price());

        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;

        return timeOverlap && priceOverlap;
    }

    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        int x1 = axis.timeToX(start.timestamp(), klines, tf);
        int y1 = axis.priceToY(start.price());
        int x2 = axis.timeToX(end.timestamp(), klines, tf);
        int y2 = axis.priceToY(end.price());
        handles.add(new DrawingHandle(new Point(x1, y1), DrawingHandle.HandleType.START_POINT, id));
        handles.add(new DrawingHandle(new Point(x2, y2), DrawingHandle.HandleType.END_POINT, id));
        return handles;
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return switch (handleType) {
            case START_POINT -> new RayObject(id, newPoint, end, color, stroke, visibility, isLocked, showPriceLabel);
            case END_POINT -> new RayObject(id, start, newPoint, color, stroke, visibility, isLocked, showPriceLabel);
            default -> this;
        };
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newStart = new DrawingObjectPoint(start.timestamp().plusMillis(timeDelta), start.price().add(priceDelta));
        DrawingObjectPoint newEnd = new DrawingObjectPoint(end.timestamp().plusMillis(timeDelta), end.price().add(priceDelta));
        return new RayObject(id, newStart, newEnd, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new RayObject(id, start, end, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new RayObject(id, start, end, color, stroke, visibility, isLocked, show);
    }

    private void drawHandle(Graphics2D g, Point position) {
        int handleSize = SettingsService.getInstance().getDrawingHandleSize();
        int x = position.x - handleSize / 2;
        int y = position.y - handleSize / 2;
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(Color.WHITE);
        g.fillRect(x, y, handleSize, handleSize);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, handleSize, handleSize);
    }

    private static double distanceToLine(Point p, Point v, Point w) {
        double l2 = v.distanceSq(w);
        if (l2 == 0.0) return p.distance(v);
        return Math.abs((w.y - v.y) * p.x - (w.x - v.x) * p.y + w.x * v.y - w.y * v.x) / Math.sqrt(l2);
    }
}