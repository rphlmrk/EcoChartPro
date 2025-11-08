package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject.FibLevelProperties;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import com.EcoChartPro.ui.dialogs.FibonacciSettingsDialog;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public record FibonacciExtensionObject(
    UUID id,
    DrawingObjectPoint p0,
    DrawingObjectPoint p1,
    DrawingObjectPoint p2,
    Color color,
    BasicStroke stroke,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    Map<Double, FibLevelProperties> fibLevels,
    boolean showPriceLabel
) implements DrawingObject {

    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 11);
    
    public FibonacciExtensionObject(UUID id, DrawingObjectPoint p0, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, p0, p1, p2, color, stroke, visibility, false, SettingsService.getInstance().getFibExtensionDefaultLevels(), true);
    }

    public FibonacciExtensionObject(UUID id, DrawingObjectPoint p0, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, p0, p1, p2, color, stroke, visibility, isLocked, SettingsService.getInstance().getFibExtensionDefaultLevels(), true);
    }
    
    public FibonacciExtensionObject(UUID id, DrawingObjectPoint p0, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked, Map<Double, FibLevelProperties> fibLevels) {
        this(id, p0, p1, p2, color, stroke, visibility, isLocked, fibLevels, true);
    }
    
    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured() || p0 == null || p1 == null || p2 == null) return;

        Point s0 = new Point(axis.timeToX(p0.timestamp(), klines, tf), axis.priceToY(p0.price()));
        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));

        // Draw base trend lines
        g.setColor(this.color);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0));
        g.drawLine(s0.x, s0.y, s1.x, s1.y);
        g.drawLine(s1.x, s1.y, s2.x, s2.y);
        
        // Calculate and draw projection levels
        BigDecimal priceRange = p1.price().subtract(p0.price());
        int chartWidth = g.getClipBounds().width;

        g.setFont(LABEL_FONT);
        for (Map.Entry<Double, FibLevelProperties> entry : fibLevels.entrySet()) {
            if (!entry.getValue().enabled()) continue;

            double level = entry.getKey();
            Color levelColor = entry.getValue().color();

            BigDecimal levelPrice = p2.price().add(priceRange.multiply(BigDecimal.valueOf(level)));
            int y = axis.priceToY(levelPrice);

            g.setColor(levelColor);
            g.setStroke(this.stroke);
            g.drawLine(s2.x, y, chartWidth, y);

            String labelText = String.format("%.3f", level);
            g.drawString(labelText, s2.x + 5, y - 2);
            g.drawString(levelPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(), s2.x + 5, y + g.getFontMetrics().getAscent() - 4);
        }

        if (id.equals(DrawingManager.getInstance().getSelectedDrawingId()) && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }
    
    @Override
    public void showSettingsDialog(Frame owner, DrawingManager dm) {
        Consumer<FibonacciSettingsDialog.SaveResult> onSave = result -> {
            DrawingObject updatedDrawing = this.withLevels(result.levels())
                                                        .withVisibility(result.visibility())
                                                        .withShowPriceLabel(result.showPriceLabel());
            dm.updateDrawing(updatedDrawing);
        };
        new FibonacciSettingsDialog(
            owner, 
            "Fibonacci Extension Settings", 
            this.getClass().getSimpleName(), 
            this.fibLevels(), 
            this.visibility(), 
            this.showPriceLabel(), 
            onSave
        ).setVisible(true);
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        Point s0 = new Point(axis.timeToX(p0.timestamp(), klines, tf), axis.priceToY(p0.price()));
        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        boolean hit1 = distanceToLineSegment(screenPoint.x, screenPoint.y, s0.x, s0.y, s1.x, s1.y) < SettingsService.getInstance().getDrawingHitThreshold();
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));
        boolean hit2 = distanceToLineSegment(screenPoint.x, screenPoint.y, s1.x, s1.y, s2.x, s2.y) < SettingsService.getInstance().getDrawingHitThreshold();
        return hit1 || hit2;
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
        return switch (handleType) {
            case P0 -> new FibonacciExtensionObject(id, newPoint, p1, p2, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
            case P1 -> new FibonacciExtensionObject(id, p0, newPoint, p2, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
            case P2 -> new FibonacciExtensionObject(id, p0, p1, newPoint, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
            default -> this;
        };
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newP0 = new DrawingObjectPoint(p0.timestamp().plusMillis(timeDelta), p0.price().add(priceDelta));
        DrawingObjectPoint newP1 = new DrawingObjectPoint(p1.timestamp().plusMillis(timeDelta), p1.price().add(priceDelta));
        DrawingObjectPoint newP2 = new DrawingObjectPoint(p2.timestamp().plusMillis(timeDelta), p2.price().add(priceDelta));
        return new FibonacciExtensionObject(id, newP0, newP1, newP2, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
    }
    
    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        // Check if the bounding box of the three defining points intersects the view.
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
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new FibonacciExtensionObject(id, p0, p1, p2, color, stroke, newVisibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public DrawingObject withColor(Color newColor) {
        return new FibonacciExtensionObject(id, p0, p1, p2, newColor, stroke, visibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new FibonacciExtensionObject(id, p0, p1, p2, color, newStroke, visibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public boolean isLocked() { return isLocked; }
    @Override
    public DrawingObject withLocked(boolean locked) {
        return new FibonacciExtensionObject(id, p0, p1, p2, color, stroke, visibility, locked, fibLevels, showPriceLabel);
    }
    
    public DrawingObject withLevels(Map<Double, FibLevelProperties> newLevels) {
        return new FibonacciExtensionObject(id, p0, p1, p2, color, stroke, visibility, isLocked, newLevels, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new FibonacciExtensionObject(id, p0, p1, p2, color, stroke, visibility, isLocked, fibLevels, show);
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