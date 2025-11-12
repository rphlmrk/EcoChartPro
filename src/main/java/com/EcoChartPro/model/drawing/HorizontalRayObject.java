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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HorizontalRayObject(
    UUID id,
    DrawingObjectPoint anchor,
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
    public HorizontalRayObject(UUID id, DrawingObjectPoint anchor, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, anchor, color, stroke, visibility, false, true);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public HorizontalRayObject(UUID id, DrawingObjectPoint anchor, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, anchor, color, stroke, visibility, isLocked, true);
    }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new HorizontalRayObject(id, anchor, color, stroke, newVisibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withColor(Color newColor) {
        return new HorizontalRayObject(id, anchor, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new HorizontalRayObject(id, anchor, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf, boolean isSelected) {
        if (!axis.isConfigured()) return;
        
        Point p1 = new Point(axis.timeToX(anchor.timestamp(), klines, tf), axis.priceToY(anchor.price()));
        int chartWidth = g.getClipBounds().width;

        Stroke originalStroke = g.getStroke();
        
        g.setColor(this.color);
        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke);
        g.drawLine(p1.x, p1.y, chartWidth, p1.y);
        g.setStroke(originalStroke);

        if (isSelected && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        Point p1 = new Point(axis.timeToX(anchor.timestamp(), klines, tf), axis.priceToY(anchor.price()));
        // Check if point is on the horizontal line and to the right of the anchor
        boolean yMatch = Math.abs(screenPoint.y - p1.y) < SettingsService.getInstance().getDrawingHitThreshold();
        boolean xMatch = screenPoint.x >= p1.x;
        return yMatch && xMatch;
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        // Visible if the anchor's price is in range, and the time range overlaps the ray
        return priceRange.contains(anchor.price()) && !anchor.timestamp().isAfter(timeRange.end());
    }

    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        int x = axis.timeToX(anchor.timestamp(), klines, tf);
        int y = axis.priceToY(anchor.price());
        return Collections.singletonList(new DrawingHandle(new Point(x, y), DrawingHandle.HandleType.START_POINT, id));
    }
    
    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return new HorizontalRayObject(id, newPoint, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newAnchor = new DrawingObjectPoint(anchor.timestamp().plusMillis(timeDelta), anchor.price().add(priceDelta));
        return new HorizontalRayObject(id, newAnchor, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new HorizontalRayObject(id, anchor, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new HorizontalRayObject(id, anchor, color, stroke, visibility, isLocked, show);
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
}