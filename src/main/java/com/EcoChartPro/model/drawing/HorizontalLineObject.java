package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HorizontalLineObject(
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
    public HorizontalLineObject(UUID id, DrawingObjectPoint anchor, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, anchor, color, stroke, visibility, false, true);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public HorizontalLineObject(UUID id, DrawingObjectPoint anchor, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, anchor, color, stroke, visibility, isLocked, true);
    }


    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new HorizontalLineObject(id, anchor, color, stroke, newVisibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withColor(Color newColor) {
        return new HorizontalLineObject(id, anchor, newColor, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new HorizontalLineObject(id, anchor, color, newStroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public void render(Graphics2D g, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (!axis.isConfigured()) return;
        
        int y = axis.priceToY(anchor.price());
        int chartWidth = g.getClipBounds().width;

        boolean isSelected = id.equals(DrawingManager.getInstance().getSelectedDrawingId());
        Stroke originalStroke = g.getStroke();
        
        g.setColor(this.color);
        g.setStroke(isSelected ? SELECTED_STROKE : this.stroke);
        g.drawLine(0, y, chartWidth, y);
        g.setStroke(originalStroke);

        if (isSelected && !isLocked) {
            getHandles(axis, data, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }

    @Override
    public boolean isHit(Point screenPoint, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        int y = axis.priceToY(anchor.price());
        return Math.abs(screenPoint.y - y) < SettingsManager.getInstance().getDrawingHitThreshold();
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        return priceRange.contains(anchor.price());
    }

    @Override
    public List<DrawingHandle> getHandles(IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (isLocked || !axis.isConfigured() || data.isEmpty()) return Collections.emptyList();
        int x = axis.timeToX(data.get(data.size() / 2).startTime(), data, tf);
        int y = axis.priceToY(anchor.price());
        return Collections.singletonList(new DrawingHandle(new Point(x, y), DrawingHandle.HandleType.BODY, id));
    }
    
    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return new HorizontalLineObject(id, newPoint, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newAnchor = new DrawingObjectPoint(anchor.timestamp(), anchor.price().add(priceDelta));
        return new HorizontalLineObject(id, newAnchor, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new HorizontalLineObject(id, anchor, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new HorizontalLineObject(id, anchor, color, stroke, visibility, isLocked, show);
    }

    private void drawHandle(Graphics2D g, Point position) {
        int handleSize = SettingsManager.getInstance().getDrawingHandleSize();
        int x = position.x - handleSize / 2;
        int y = position.y - handleSize / 2;
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(Color.WHITE);
        g.fillRect(x, y, handleSize, handleSize);
        g.setColor(Color.BLACK);
        g.drawRect(x, y, handleSize, handleSize);
    }
}