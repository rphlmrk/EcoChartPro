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
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A versatile drawing object for measurements on the chart.
 * It can render as a Price Range, Date Range, or a full measurement tool.
 */
public record MeasureToolObject(
    UUID id,
    DrawingObjectPoint p1,
    DrawingObjectPoint p2,
    ToolType toolType,
    Color color,
    BasicStroke stroke,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    boolean showPriceLabel
) implements DrawingObject {

    public enum ToolType { PRICE_RANGE, DATE_RANGE, MEASURE }

    private static final Color HANDLE_FILL_COLOR = Color.WHITE;
    private static final Color HANDLE_STROKE_COLOR = Color.BLACK;
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Color LABEL_TEXT_COLOR = Color.WHITE;
    private static final Color LABEL_BACKGROUND_COLOR = new Color(30, 30, 30, 180);

    /**
     * Overloaded constructor for creating new, unlocked objects.
     */
    public MeasureToolObject(UUID id, DrawingObjectPoint p1, DrawingObjectPoint p2, ToolType toolType, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility) {
        this(id, p1, p2, toolType, color, stroke, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public MeasureToolObject(UUID id, DrawingObjectPoint p1, DrawingObjectPoint p2, ToolType toolType, Color color, BasicStroke stroke, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, p1, p2, toolType, color, stroke, visibility, isLocked, false);
    }

    @Override
    public void render(Graphics2D g, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (!axis.isConfigured() || p1 == null || p2 == null || tf == null) return;

        Point s1 = new Point(axis.timeToX(p1.timestamp(), data, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), data, tf), axis.priceToY(p2.price()));
        Rectangle bounds = getScreenBounds(s1, s2);

        // --- 1. Draw the main shape ---
        Stroke originalStroke = g.getStroke();
        g.setStroke(this.stroke);
        g.setColor(this.color);

        // Fill the background
        Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 30);
        g.setColor(fillColor);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Draw the border
        g.setColor(this.color);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // For the full measure tool, draw the diagonal line
        if (toolType == ToolType.MEASURE) {
            g.drawLine(s1.x, s1.y, s2.x, s2.y);
        }

        // --- 2. Calculate Metrics ---
        BigDecimal priceDelta = p2.price().subtract(p1.price());
        BigDecimal absPriceDelta = priceDelta.abs();
        BigDecimal pctChange = p1.price().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO :
            absPriceDelta.divide(p1.price(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        Duration duration = Duration.between(p1.timestamp(), p2.timestamp()).abs();
        // Use the record's accessor method duration()
        long barCount = tf.duration().isZero() ? 0 : duration.toMillis() / tf.duration().toMillis();
        String sign = priceDelta.signum() >= 0 ? "+" : "-";

        // --- 3. Prepare Text Labels ---
        List<String> labels = new ArrayList<>();
        switch (toolType) {
            case PRICE_RANGE:
                labels.add(String.format("%s%.2f (%.2f%%)", sign, absPriceDelta, pctChange));
                break;
            case DATE_RANGE:
                labels.add(String.format("%s", formatDuration(duration)));
                labels.add(String.format("%d bars", barCount));
                break;
            case MEASURE:
                labels.add(String.format("%s%.2f (%.2f%%)", sign, absPriceDelta, pctChange));
                labels.add(String.format("%s, %d bars", formatDuration(duration), barCount));
                break;
        }

        // --- 4. Render Text Labels ---
        g.setFont(LABEL_FONT);
        FontMetrics fm = g.getFontMetrics();
        int textHeight = fm.getHeight();
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, fm.stringWidth(label));
        }

        int boxX = bounds.x + bounds.width / 2 - maxWidth / 2 - 5;
        int boxY = bounds.y + bounds.height / 2 - (labels.size() * textHeight) / 2 - 5;
        int boxWidth = maxWidth + 10;
        int boxHeight = labels.size() * textHeight + 10;

        g.setColor(LABEL_BACKGROUND_COLOR);
        g.fillRect(boxX, boxY, boxWidth, boxHeight);

        g.setColor(LABEL_TEXT_COLOR);
        for (int i = 0; i < labels.size(); i++) {
            g.drawString(labels.get(i), boxX + 5, boxY + fm.getAscent() + (i * textHeight));
        }

        // --- 5. Draw Handles if Selected ---
        if (id.equals(DrawingManager.getInstance().getSelectedDrawingId()) && !isLocked) {
            getHandles(axis, data, tf).forEach(h -> drawHandle(g, h.position()));
        }

        g.setStroke(originalStroke);
    }
    
    private String formatDuration(Duration duration) {
        long days = duration.toDaysPart();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) return String.format("%dD %dH", days, hours);
        if (hours > 0) return String.format("%dH %dM", hours, minutes);
        return String.format("%dM", minutes);
    }

    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new MeasureToolObject(id, p1, p2, toolType, color, stroke, newVisibility, isLocked, showPriceLabel);
    }
    @Override
    public DrawingObject withColor(Color newColor) {
        return new MeasureToolObject(id, p1, p2, toolType, newColor, stroke, visibility, isLocked, showPriceLabel);
    }
    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return new MeasureToolObject(id, p1, p2, toolType, color, newStroke, visibility, isLocked, showPriceLabel);
    }
    @Override
    public boolean isHit(Point screenPoint, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (!axis.isConfigured()) return false;
        Point s1 = new Point(axis.timeToX(p1.timestamp(), data, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), data, tf), axis.priceToY(p2.price()));
        Rectangle bounds = getScreenBounds(s1, s2);
        bounds.grow(SettingsManager.getInstance().getDrawingHitThreshold() / 2, SettingsManager.getInstance().getDrawingHitThreshold() / 2);
        return bounds.contains(screenPoint);
    }
    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        Instant minTime = p1.timestamp().isBefore(p2.timestamp()) ? p1.timestamp() : p2.timestamp();
        Instant maxTime = p1.timestamp().isAfter(p2.timestamp()) ? p1.timestamp() : p2.timestamp();
        BigDecimal minPrice = p1.price().min(p2.price());
        BigDecimal maxPrice = p1.price().max(p2.price());
        boolean timeOverlap = !maxTime.isBefore(timeRange.start()) && !minTime.isAfter(timeRange.end());
        boolean priceOverlap = maxPrice.compareTo(priceRange.min()) >= 0 && minPrice.compareTo(priceRange.max()) <= 0;
        return timeOverlap && priceOverlap;
    }
    @Override
    public List<DrawingHandle> getHandles(IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf) {
        if (isLocked) return Collections.emptyList();
        List<DrawingHandle> handles = new ArrayList<>();
        Point s1 = new Point(axis.timeToX(p1.timestamp(), data, tf), axis.priceToY(p1.price()));
        Point s2 = new Point(axis.timeToX(p2.timestamp(), data, tf), axis.priceToY(p2.price()));
        handles.add(new DrawingHandle(s1, DrawingHandle.HandleType.START_POINT, id));
        handles.add(new DrawingHandle(s2, DrawingHandle.HandleType.END_POINT, id));
        return handles;
    }
    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        if (isLocked) return this;
        return switch (handleType) {
            case START_POINT -> new MeasureToolObject(id, newPoint, p2, toolType, color, stroke, visibility, isLocked, showPriceLabel);
            case END_POINT -> new MeasureToolObject(id, p1, newPoint, toolType, color, stroke, visibility, isLocked, showPriceLabel);
            default -> this;
        };
    }
    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        if (isLocked) return this;
        DrawingObjectPoint newP1 = new DrawingObjectPoint(p1.timestamp().plusMillis(timeDelta), p1.price().add(priceDelta));
        DrawingObjectPoint newP2 = new DrawingObjectPoint(p2.timestamp().plusMillis(timeDelta), p2.price().add(priceDelta));
        return new MeasureToolObject(id, newP1, newP2, toolType, color, stroke, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new MeasureToolObject(id, p1, p2, toolType, color, stroke, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new MeasureToolObject(id, p1, p2, toolType, color, stroke, visibility, isLocked, show);
    }

    private Rectangle getScreenBounds(Point s1, Point s2) {
        return new Rectangle(Math.min(s1.x, s2.x), Math.min(s1.y, s2.y), Math.abs(s1.x - s2.x), Math.abs(s1.y - s2.y));
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
}