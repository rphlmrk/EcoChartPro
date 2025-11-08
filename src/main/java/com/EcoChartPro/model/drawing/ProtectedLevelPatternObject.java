package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProtectedLevelPatternObject(
    UUID id,
    DrawingObjectPoint p0, // The start point that defines the PH/PL level.
    DrawingObjectPoint p1, // The extreme point of the first leg (the low/high).
    DrawingObjectPoint p2, // The violation point (L1).
    TradeDirection direction,
    Color color,
    BasicStroke stroke,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    boolean showPriceLabel
) implements DrawingObject {

    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Color HANDLE_FILL_COLOR = Color.WHITE;
    private static final Color HANDLE_STROKE_COLOR = Color.BLACK;
    private static final Stroke SELECTED_STROKE = new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
    private static final Stroke DASHED_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f);

    /**
     * Overloaded constructor for creating new, unlocked objects.
     */
    public ProtectedLevelPatternObject(UUID id, DrawingObjectPoint p0, DrawingObjectPoint p1, DrawingObjectPoint p2, TradeDirection direction, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, p0, p1, p2, direction, color, stroke, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public ProtectedLevelPatternObject(UUID id, DrawingObjectPoint p0, DrawingObjectPoint p1, DrawingObjectPoint p2, TradeDirection direction, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, p0, p1, p2, direction, color, stroke, visibility, isLocked, false);
    }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new ProtectedLevelPatternObject(id, p0, p1, p2, direction, color, stroke, newVisibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withColor(Color newColor) {
        return new ProtectedLevelPatternObject(id, p0, p1, p2, direction, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new ProtectedLevelPatternObject(id, p0, p1, p2, direction, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (p0 == null || p1 == null || p2 == null || !axis.isConfigured()) return;

        Point s0 = new Point(axis.timeToX(p0.timestamp(), klines, tf), axis.priceToY(p0.price()));
        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));

        g.setFont(LABEL_FONT);
        boolean isSelected = id.equals(DrawingManager.getInstance().getSelectedDrawingId());
        Stroke originalStroke = g.getStroke();

        // 1. Draw the solid lines representing the user-drawn setup
        g.setColor(Color.WHITE);
        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke);
        g.drawLine(s0.x, s0.y, s1.x, s1.y);
        g.drawLine(s1.x, s1.y, s2.x, s2.y);

        // 2. Calculate projected points
        BigDecimal priceRange_p0_p1 = p1.price().subtract(p0.price());
        BigDecimal fiftyPercentPrice = p0.price().add(priceRange_p0_p1.divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP));
        Duration violationTimeDelta = Duration.between(p1.timestamp(), p2.timestamp());
        Instant pullbackTimestamp = p2.timestamp().plus(violationTimeDelta.dividedBy(2));
        BigDecimal thrustPriceDelta = p2.price().subtract(p1.price());
        BigDecimal projectedTpPrice = fiftyPercentPrice.add(thrustPriceDelta);
        Instant projectedTpTimestamp = pullbackTimestamp.plus(violationTimeDelta);
        
        Point s_pullback = new Point(axis.timeToX(pullbackTimestamp, klines, tf), axis.priceToY(fiftyPercentPrice));
        Point s_tp = new Point(axis.timeToX(projectedTpTimestamp, klines, tf), axis.priceToY(projectedTpPrice));
        
        // 3. Draw the pattern-specific elements
        if (direction == TradeDirection.SHORT) { // Bearish Pattern (PL)
            g.setColor(new Color(30, 200, 30)); // Green
            g.setStroke(this.stroke);
            g.drawLine(s0.x, s0.y, s1.x, s0.y);
            g.drawString("PL", s0.x - 25, s0.y + 5);

            g.setColor(Color.WHITE);
            g.drawLine(s1.x, s2.y, s_pullback.x, s2.y);
            g.drawString("L1", s1.x - 25, s2.y + 5);
            g.drawString("TP", (s1.x + s_pullback.x) / 2, s2.y + 15);

            int fiftyPercentY = axis.priceToY(fiftyPercentPrice);
            g.setColor(new Color(0, 188, 212)); // Cyan
            g.drawLine(s1.x, fiftyPercentY, s_pullback.x, fiftyPercentY);
            g.drawString("50%", s_pullback.x + 5, fiftyPercentY + 5);
            
            g.setColor(new Color(255, 140, 40)); // Orange for bearish projection
            g.setStroke(DASHED_STROKE);
            g.drawLine(s2.x, s2.y, s_pullback.x, s_pullback.y);
            g.drawLine(s_pullback.x, s_pullback.y, s_tp.x, s_tp.y);
            g.drawString("TP", s_tp.x + 5, s_tp.y);

        } else { // Bullish Pattern (PH)
            g.setColor(new Color(255, 82, 82)); // Red
            g.setStroke(this.stroke);
            g.drawLine(s0.x, s0.y, s1.x, s0.y);
            g.drawString("PH", s0.x - 25, s0.y + 5);

            g.setColor(Color.WHITE);
            g.drawLine(s1.x, s2.y, s_pullback.x, s2.y);
            g.drawString("L1", s1.x - 25, s2.y + 5);
            g.drawString("TP", (s1.x + s_pullback.x) / 2, s2.y - 5);
            
            int fiftyPercentY = axis.priceToY(fiftyPercentPrice);
            g.setColor(new Color(0, 188, 212)); // Cyan
            g.drawLine(s1.x, fiftyPercentY, s_pullback.x, fiftyPercentY);
            g.drawString("50%", s_pullback.x + 5, fiftyPercentY + 5);
            
            g.setColor(new Color(255, 140, 40));
            g.setStroke(DASHED_STROKE);
            g.drawLine(s2.x, s2.y, s_pullback.x, s_pullback.y);
            g.drawLine(s_pullback.x, s_pullback.y, s_tp.x, s_tp.y);
            g.drawString("TP", s_tp.x + 5, s_tp.y);
        }

        if (isSelected && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
        g.setStroke(originalStroke);
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        Point s0 = new Point(axis.timeToX(p0.timestamp(), klines, tf), axis.priceToY(p0.price()));
        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));
        boolean hit1 = distanceToLineSegment(screenPoint.x, screenPoint.y, s0.x, s0.y, s1.x, s1.y) < SettingsService.getInstance().getDrawingHitThreshold();
        boolean hit2 = distanceToLineSegment(screenPoint.x, screenPoint.y, s1.x, s1.y, s2.x, s2.y) < SettingsService.getInstance().getDrawingHitThreshold();
        return hit1 || hit2;
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        Instant minTime = p0.timestamp().isBefore(p1.timestamp()) ? p0.timestamp() : p1.timestamp();
        minTime = minTime.isBefore(p2.timestamp()) ? minTime : p2.timestamp();

        Instant maxTime = p0.timestamp().isAfter(p1.timestamp()) ? p0.timestamp() : p1.timestamp();
        maxTime = maxTime.isAfter(p2.timestamp()) ? maxTime : p2.timestamp();

        BigDecimal minPrice = p0.price().min(p1.price()).min(p2.price());
        BigDecimal maxPrice = p0.price().max(p1.price()).max(p2.price());

        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;
        return timeOverlap && priceOverlap;
    }

    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        handles.add(new DrawingHandle(new Point(axis.timeToX(p0.timestamp(), klines, tf), axis.priceToY(p0.price())), DrawingHandle.HandleType.P0, id));
        handles.add(new DrawingHandle(new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price())), DrawingHandle.HandleType.P1, id));
        handles.add(new DrawingHandle(new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price())), DrawingHandle.HandleType.P2, id));
        return handles;
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        TradeDirection newDirection = direction;
        DrawingObjectPoint newP0 = p0, newP1 = p1, newP2 = p2;

        switch(handleType) {
            case P0: newP0 = newPoint; break;
            case P1: newP1 = newPoint; break;
            case P2: newP2 = newPoint; break;
            default: return this;
        }

        if (handleType == DrawingHandle.HandleType.P0 || handleType == DrawingHandle.HandleType.P1) {
            newDirection = newP1.price().compareTo(newP0.price()) < 0 ? TradeDirection.LONG : TradeDirection.SHORT;
        }
        
        return new ProtectedLevelPatternObject(id, newP0, newP1, newP2, newDirection, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newP0 = new DrawingObjectPoint(p0.timestamp().plusMillis(timeDelta), p0.price().add(priceDelta));
        DrawingObjectPoint newP1 = new DrawingObjectPoint(p1.timestamp().plusMillis(timeDelta), p1.price().add(priceDelta));
        DrawingObjectPoint newP2 = new DrawingObjectPoint(p2.timestamp().plusMillis(timeDelta), p2.price().add(priceDelta));
        return new ProtectedLevelPatternObject(id, newP0, newP1, newP2, direction, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new ProtectedLevelPatternObject(id, p0, p1, p2, direction, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new ProtectedLevelPatternObject(id, p0, p1, p2, direction, color, stroke, visibility, isLocked, show);
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