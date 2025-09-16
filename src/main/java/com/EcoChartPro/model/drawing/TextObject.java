package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.settings.SettingsManager;
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

public record TextObject(
    UUID id,
    DrawingObjectPoint anchor,
    String text,
    Font font,
    Color color,
    TextProperties properties,
    Map<Timeframe, Boolean> visibility,
    boolean isLocked,
    boolean showPriceLabel
) implements DrawingObject {

    private static final Color HANDLE_FILL_COLOR = Color.WHITE;
    private static final Color HANDLE_STROKE_COLOR = Color.BLACK;
    private static final int MAX_WRAP_WIDTH = 200;

    /**
     * Overloaded constructor for backwards compatibility and for tools creating new objects.
     */
    public TextObject(UUID id, DrawingObjectPoint anchor, String text, Font font, Color color, TextProperties properties, Map<Timeframe, Boolean> visibility) {
        this(id, anchor, text, font, color, properties, visibility, false, false);
    }

    /**
     * Overloaded constructor for backwards compatibility (e.g., loading from older sessions).
     */
    public TextObject(UUID id, DrawingObjectPoint anchor, String text, Font font, Color color, TextProperties properties, Map<Timeframe, Boolean> visibility, boolean isLocked) {
        this(id, anchor, text, font, color, properties, visibility, isLocked, false);
    }


    @Override
    public void render(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        if (text == null || text.isBlank()) return;

        Point p;
        if (properties.screenAnchored()) {
            // For screen-anchored text, interpret the anchor as raw pixel coordinates.
            p = new Point((int) anchor.timestamp().toEpochMilli(), anchor.price().intValue());
        } else {
            if (!axis.isConfigured()) return;
            p = new Point(axis.timeToX(anchor.timestamp(), klines, tf), axis.priceToY(anchor.price()));
        }

        g.setFont(this.font);
        FontMetrics fm = g.getFontMetrics();

        List<String> linesToRender = getWrappedLines(fm, properties.wrapText());
        int textBlockWidth = 0;
        for (String line : linesToRender) {
            textBlockWidth = Math.max(textBlockWidth, fm.stringWidth(line));
        }
        int textBlockHeight = linesToRender.size() * fm.getHeight();
        
        int padding = 4;
        Rectangle bounds = new Rectangle(p.x, p.y - fm.getAscent() - padding, textBlockWidth + (padding * 2), textBlockHeight + (padding * 2));

        if (properties.showBackground()) {
            g.setColor(properties.backgroundColor());
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
        if (properties.showBorder()) {
            g.setColor(properties.borderColor());
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        g.setColor(this.color);
        int currentY = p.y;
        for (String line : linesToRender) {
            g.drawString(line, p.x + padding, currentY);
            currentY += fm.getHeight();
        }

        if (id.equals(DrawingManager.getInstance().getSelectedDrawingId()) && !isLocked) {
            getHandles(axis, klines, tf).forEach(h -> drawHandle(g, h.position()));
        }
    }
    
    private Rectangle getScreenBounds(Graphics2D g, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        Point p;
        if (properties.screenAnchored()) {
            p = new Point((int) anchor.timestamp().toEpochMilli(), anchor.price().intValue());
        } else {
            if (!axis.isConfigured()) return new Rectangle();
            p = new Point(axis.timeToX(anchor.timestamp(), klines, tf), axis.priceToY(anchor.price()));
        }

        FontMetrics fm = g.getFontMetrics(this.font);
        List<String> lines = getWrappedLines(fm, properties.wrapText());
        int w = 0;
        for (String l : lines) w = Math.max(w, fm.stringWidth(l));
        int h = lines.size() * fm.getHeight();
        return new Rectangle(p.x, p.y - fm.getAscent(), w + 8, h + 8);
    }

    @Override
    public boolean isHit(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe tf) {
        // Create a temporary graphics context to get FontMetrics
        Graphics2D g2d = (Graphics2D) new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).getGraphics();
        Rectangle bounds = getScreenBounds(g2d, axis, klines, tf);
        g2d.dispose();
        bounds.grow(SettingsManager.getInstance().getDrawingHitThreshold(), SettingsManager.getInstance().getDrawingHitThreshold());
        return bounds.contains(screenPoint);
    }
    
    @Override
    public List<DrawingHandle> getHandles(ChartAxis axis, List<KLine> klines, Timeframe tf) {
        // Disable handles (and thus moving) for screen-anchored or locked text.
        if (properties.screenAnchored() || isLocked) {
            return Collections.emptyList();
        }
        if (!axis.isConfigured()) return Collections.emptyList();
        Point p = new Point(axis.timeToX(anchor.timestamp(), klines, tf), axis.priceToY(anchor.price()));
        return List.of(new DrawingHandle(p, DrawingHandle.HandleType.BODY, id));
    }

    @Override
    public DrawingObject move(long timeDelta, BigDecimal priceDelta) {
        // Screen-anchored or locked text is not movable.
        if (properties.screenAnchored() || isLocked) return this;
        DrawingObjectPoint newAnchor = new DrawingObjectPoint(anchor.timestamp().plusMillis(timeDelta), anchor.price().add(priceDelta));
        return new TextObject(id, newAnchor, text, font, color, properties, visibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint) {
        // Screen-anchored or locked text is not movable.
        if (properties.screenAnchored() || isLocked) return this;
        return (handleType == DrawingHandle.HandleType.BODY) ? new TextObject(id, newPoint, text, font, color, properties, visibility, isLocked, showPriceLabel) : this;
    }

    @Override
    public boolean isVisible(TimeRange timeRange, PriceRange priceRange) {
        // A screen-anchored object is always visible.
        if (properties.screenAnchored()) return true;
        return timeRange.contains(anchor.timestamp()) && priceRange.contains(anchor.price());
    }

    @Override
    public BasicStroke stroke() {
        return new BasicStroke(1);
    }
    
    @Override
    public DrawingObject withStroke(BasicStroke newStroke) {
        return this;
    }
    
    @Override
    public DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility) {
        return new TextObject(id, anchor, text, font, color, properties, newVisibility, isLocked, showPriceLabel);
    }

    @Override
    public DrawingObject withColor(Color newColor) {
        return new TextObject(id, anchor, text, font, newColor, properties, visibility, isLocked, showPriceLabel);
    }

    @Override
    public boolean isLocked() {
        return isLocked;
    }

    @Override
    public DrawingObject withLocked(boolean locked) {
        return new TextObject(id, anchor, text, font, color, properties, visibility, locked, showPriceLabel);
    }

    @Override
    public DrawingObject withShowPriceLabel(boolean show) {
        return new TextObject(id, anchor, text, font, color, properties, visibility, isLocked, show);
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

    private List<String> getWrappedLines(FontMetrics fm, boolean wrapEnabled) {
        List<String> wrappedLines = new ArrayList<>();
        String[] manualLines = text.split("\n");

        for (String manualLine : manualLines) {
            if (!wrapEnabled || fm.stringWidth(manualLine) <= MAX_WRAP_WIDTH) {
                wrappedLines.add(manualLine);
                continue;
            }
            String[] words = manualLine.split(" ");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                if (fm.stringWidth(currentLine + word) > MAX_WRAP_WIDTH) {
                    wrappedLines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder(word + " ");
                } else {
                    currentLine.append(word).append(" ");
                }
            }
            if (!currentLine.isEmpty()) {
                wrappedLines.add(currentLine.toString().trim());
            }
        }
        return wrappedLines;
    }
}