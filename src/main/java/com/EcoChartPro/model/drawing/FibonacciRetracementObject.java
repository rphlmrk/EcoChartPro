package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

public record FibonacciRetracementObject(
    UUID id,
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
    
    // New record to hold properties for each level.
    public record FibLevelProperties(boolean enabled, Color color) {}

    public FibonacciRetracementObject(UUID id, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, p1, p2, color, stroke, visibility, false, SettingsManager.getInstance().getFibRetracementDefaultLevels(), true);
    }
    
    public FibonacciRetracementObject(UUID id, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, p1, p2, color, stroke, visibility, isLocked, SettingsManager.getInstance().getFibRetracementDefaultLevels(), true);
    }

    public FibonacciRetracementObject(UUID id, DrawingObjectPoint p1, DrawingObjectPoint p2, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked, Map<Double, FibLevelProperties> fibLevels) {
        this(id, p1, p2, color, stroke, visibility, isLocked, fibLevels, true);
    }


    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (!axis.isConfigured() || p1 == null || p2 == null) return;

        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));

        BigDecimal priceRange = p2.price().subtract(p1.price());
        boolean isReversed = s2.x < s1.x;

        // Draw background
        Color bgColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 10);
        g.setColor(bgColor);
        g.fillRect(Math.min(s1.x, s2.x), Math.min(s1.y, s2.y), Math.abs(s1.x - s2.x), Math.abs(s1.y - s2.y));

        // Draw levels
        g.setFont(LABEL_FONT);
        for (Map.Entry<Double, FibLevelProperties> entry : fibLevels.entrySet()) {
            if (!entry.getValue().enabled()) continue;
            
            double level = entry.getKey();
            Color levelColor = entry.getValue().color();

            BigDecimal levelPrice = p1.price().add(priceRange.multiply(BigDecimal.valueOf(level)));
            int y = axis.priceToY(levelPrice);
            
            g.setColor(levelColor);
            g.setStroke(this.stroke);
            g.drawLine(s1.x, y, s2.x, y);

            // Draw text label
            String labelText = String.format("%.3f", level);
            int textX = isReversed ? s2.x - g.getFontMetrics().stringWidth(labelText) - 5 : s2.x + 5;
            g.drawString(labelText, textX, y - 2);
            g.drawString(levelPrice.setScale(2, RoundingMode.HALF_UP).toPlainString(), textX, y + g.getFontMetrics().getAscent() - 4);
        }

        // Draw connecting line
        g.setColor(this.color);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0));
        g.drawLine(s1.x, s1.y, s2.x, s2.y);

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
            "Fibonacci Retracement Settings", 
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
        Point s1 = new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price()));
        Rectangle bounds = new Rectangle(Math.min(s1.x, s2.x), Math.min(s1.y, s2.y), Math.abs(s1.x - s2.x), Math.abs(s1.y - s2.y));
        bounds.grow(SettingsManager.getInstance().getDrawingHitThreshold(), SettingsManager.getInstance().getDrawingHitThreshold());
        return bounds.contains(screenPoint);
    }

    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (isLocked || !axis.isConfigured()) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        handles.add(new DrawingHandle(new Point(axis.timeToX(p1.timestamp(), klines, tf), axis.priceToY(p1.price())), DrawingHandle.HandleType.START_POINT, id));
        handles.add(new DrawingHandle(new Point(axis.timeToX(p2.timestamp(), klines, tf), axis.priceToY(p2.price())), DrawingHandle.HandleType.END_POINT, id));
        return handles;
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return switch (handleType) {
            case START_POINT -> new FibonacciRetracementObject(id, newPoint, p2, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
            case END_POINT -> new FibonacciRetracementObject(id, p1, newPoint, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
            default -> this;
        };
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newP1 = new DrawingObjectPoint(p1.timestamp().plusMillis(timeDelta), p1.price().add(priceDelta));
        DrawingObjectPoint newP2 = new DrawingObjectPoint(p2.timestamp().plusMillis(timeDelta), p2.price().add(priceDelta));
        return new FibonacciRetracementObject(id, newP1, newP2, color, stroke, visibility, isLocked, fibLevels, showPriceLabel);
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        // Simple bounding box check
        Instant minTime = p1.timestamp().isBefore(p2.timestamp()) ? p1.timestamp() : p2.timestamp();
        Instant maxTime = p1.timestamp().isAfter(p2.timestamp()) ? p1.timestamp() : p2.timestamp();
        BigDecimal minPrice = p1.price().min(p2.price());
        BigDecimal maxPrice = p1.price().max(p2.price());
        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;
        return timeOverlap && priceOverlap;
    }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new FibonacciRetracementObject(id, p1, p2, color, stroke, newVisibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public DrawingObject withColor(Color newColor) {
        return new FibonacciRetracementObject(id, p1, p2, newColor, stroke, visibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new FibonacciRetracementObject(id, p1, p2, color, newStroke, visibility, isLocked, fibLevels, showPriceLabel);
    }
    @Override
    public boolean isLocked() { return isLocked; }
    @Override
    public DrawingObject withLocked(boolean locked) {
        return new FibonacciRetracementObject(id, p1, p2, color, stroke, visibility, locked, fibLevels, showPriceLabel);
    }
    
    // New method to create a new instance with updated levels.
    public DrawingObject withLevels(Map<Double, FibLevelProperties> newLevels) {
        return new FibonacciRetracementObject(id, p1, p2, color, stroke, visibility, isLocked, newLevels, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new FibonacciRetracementObject(id, p1, p2, color, stroke, visibility, isLocked, fibLevels, show);
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