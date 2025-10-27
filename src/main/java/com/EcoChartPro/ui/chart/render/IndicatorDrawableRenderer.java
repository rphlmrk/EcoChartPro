package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.api.indicator.drawing.*;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * A universal rendering engine for DrawableObjects that supports multiple rendering modes.
 */
public class IndicatorDrawableRenderer {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorDrawableRenderer.class);
    // A constant used to pack/unpack the anchor time and pixel offset into a single long.
    // This allows for offsets from -5000 to +4999, which is more than sufficient.
    private static final long NANO_ENCODING_MULTIPLIER = 10000L;
    private static final long NANO_ENCODING_BIAS = 5000L;

    public void draw(Graphics2D g, List<DrawableObject> objects, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        if (g == null || objects == null || !axis.isConfigured() || timeframe == null) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Instant viewStartTime = visibleKLines.isEmpty() ? Instant.MIN : visibleKLines.get(0).timestamp();
        Instant viewEndTime = visibleKLines.isEmpty() ? Instant.MAX : visibleKLines.get(visibleKLines.size() - 1).timestamp();

        for (DrawableObject obj : objects) {
            if (obj instanceof DrawableBox box) {
                drawBox(g, box, axis, visibleKLines, timeframe);
            } else if (obj instanceof DrawableLine line) {
                drawLine(g, line, axis, visibleKLines, timeframe);
            } else if (obj instanceof DrawablePolyline polyline) {
                drawOptimizedPolyline(g, polyline, axis, visibleKLines, viewStartTime, viewEndTime, timeframe);
            } else if (obj instanceof DrawableText text) {
                drawText(g, text, axis, visibleKLines, timeframe);
            } else if (obj instanceof DrawablePolygon polygon) {
                drawPolygon(g, polygon, axis, visibleKLines, timeframe);
            }
        }
    }

    private int resolveX(Graphics2D g, DataPoint point, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        if (point == null || point.time() == null) return -1;
        long epochSecond = point.time().getEpochSecond();
        
        if (epochSecond == DrawingSentinels.RIGHT_EDGE_PIXEL_SENTINEL.getEpochSecond()) {
            return g.getClipBounds().width - point.time().getNano();
        }
        
        if (epochSecond == DrawingSentinels.TIME_ANCHORED_PIXEL_SENTINEL.getEpochSecond()) {
            long encodedValue = point.time().getNano();
            long anchorTimeSec = encodedValue / NANO_ENCODING_MULTIPLIER;
            int pixelOffset = (int) (encodedValue % NANO_ENCODING_MULTIPLIER - NANO_ENCODING_BIAS);
            Instant anchorTime = Instant.ofEpochSecond(anchorTimeSec);
            int anchorX = axis.timeToX(anchorTime, visibleKLines, timeframe);
            return (anchorX == -1) ? -1 : anchorX + pixelOffset;
        }

        return axis.timeToX(point.time(), visibleKLines, timeframe);
    }
    
    /**
     * This method now correctly handles "degenerate" polygons (where all vertices
     * have the same timestamp). It gives them a visible width on the screen.
     */
    private void drawPolygon(Graphics2D g, DrawablePolygon polygon, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        List<DataPoint> vertices = polygon.vertices();
        if (vertices == null || vertices.size() < 2) return;
        
        int[] xPoints = new int[vertices.size()];
        int[] yPoints = new int[vertices.size()];
        boolean isDegenerate = true;
        Instant firstTimestamp = vertices.get(0).time();
        int firstX = -1;

        for (int i = 0; i < vertices.size(); i++) {
            DataPoint vertex = vertices.get(i);
            xPoints[i] = resolveX(g, vertex, axis, visibleKLines, timeframe);
            if (xPoints[i] == -1) return; // Don't draw if any point is off-screen
            
            if (i == 0) firstX = xPoints[i];
            
            if (isDegenerate && !vertex.time().equals(firstTimestamp)) {
                isDegenerate = false;
            }
            yPoints[i] = axis.priceToY(vertex.price());
        }

        if (isDegenerate) {
            double barWidth = axis.getBarWidth();
            int shapePixelWidth = Math.max(8, (int)(barWidth * 0.6));
            int halfWidth = shapePixelWidth / 2;
            
            if (vertices.size() == 3) { // Assume triangle signal shape
                xPoints[0] = firstX - halfWidth;
                xPoints[1] = firstX;
                xPoints[2] = firstX + halfWidth;
            } else { // Fallback for other degenerate shapes
                 for (int i = 0; i < xPoints.length; i++) {
                    xPoints[i] = (i % 2 == 0) ? (firstX - halfWidth) : (firstX + halfWidth);
                }
            }
        }

        if (polygon.fillColor() != null) { g.setColor(polygon.fillColor()); g.fillPolygon(xPoints, yPoints, vertices.size()); }
        if (polygon.strokeColor() != null && polygon.strokeWidth() > 0) { g.setColor(polygon.strokeColor()); g.setStroke(new BasicStroke(polygon.strokeWidth())); g.drawPolygon(xPoints, yPoints, vertices.size()); }
    }

    /**
     * This method now correctly handles "degenerate" boxes (where both corners
     * have the same timestamp). It gives them the full width of a candle bar,
     * which is perfect for drawing backgrounds.
     */
    private void drawBox(Graphics2D g, DrawableBox box, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        int x1 = resolveX(g, box.corner1(), axis, visibleKLines, timeframe);
        int y1 = axis.priceToY(box.corner1().price());
        int x2 = resolveX(g, box.corner2(), axis, visibleKLines, timeframe);
        int y2 = axis.priceToY(box.corner2().price());

        if (x1 == -1 || x2 == -1) return; // Don't draw if time is off-screen

        int rectX = Math.min(x1, x2);
        int rectY = Math.min(y1, y2);
        int width = Math.abs(x1 - x2);
        int height = Math.abs(y1 - y2);
        
        if (width <= 1) { // Detect zero-duration box
            double barWidth = axis.getBarWidth();
            width = (int) Math.max(1.0, barWidth);
            rectX = x1 - (width / 2); // Recenter the box on the candle's X
        }

        if (box.fillColor() != null) { g.setColor(box.fillColor()); g.fillRect(rectX, rectY, width, height); }
        if (box.strokeColor() != null && box.strokeWidth() > 0) { g.setColor(box.strokeColor()); g.setStroke(new BasicStroke(box.strokeWidth())); g.drawRect(rectX, rectY, width, height); }
    }

    private void drawOptimizedPolyline(Graphics2D g, DrawablePolyline polyline, ChartAxis axis, List<KLine> visibleKLines, Instant viewStartTime, Instant viewEndTime, Timeframe timeframe) {
        List<DataPoint> allPoints = polyline.getPoints();
        if (allPoints.size() < 2) return;
        int startIndex = Collections.binarySearch(allPoints, new DataPoint(viewStartTime, null), (p1, p2) -> p1.time().compareTo(p2.time()));
        if (startIndex < 0) startIndex = -startIndex - 1;
        startIndex = Math.max(0, startIndex - 1);
        int endIndex = Collections.binarySearch(allPoints, new DataPoint(viewEndTime, null), (p1, p2) -> p1.time().compareTo(p2.time()));
        if (endIndex < 0) endIndex = -endIndex - 1;
        endIndex = Math.min(allPoints.size(), endIndex + 1);
        if (startIndex >= endIndex) return;
        int numVisiblePoints = endIndex - startIndex;
        int[] xPoints = new int[numVisiblePoints];
        int[] yPoints = new int[numVisiblePoints];
        for (int i = 0; i < numVisiblePoints; i++) {
            DataPoint point = allPoints.get(startIndex + i);
            xPoints[i] = resolveX(g, point, axis, visibleKLines, timeframe);
            yPoints[i] = axis.priceToY(point.price());
        }
        if (polyline.getColor() != null && polyline.getStrokeWidth() > 0) { g.setColor(polyline.getColor()); g.setStroke(new BasicStroke(polyline.getStrokeWidth())); g.drawPolyline(xPoints, yPoints, numVisiblePoints); }
    }

    private void drawLine(Graphics2D g, DrawableLine line, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        int x1 = resolveX(g, line.start(), axis, visibleKLines, timeframe);
        int y1 = axis.priceToY(line.start().price());
        int x2 = resolveX(g, line.end(), axis, visibleKLines, timeframe);
        int y2 = axis.priceToY(line.end().price());
        if (line.color() != null && line.strokeWidth() > 0) { g.setColor(line.color()); g.setStroke(new BasicStroke(line.strokeWidth())); g.drawLine(x1, y1, x2, y2); }
    }

    private void drawText(Graphics2D g, DrawableText text, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe) {
        int anchorX = resolveX(g, text.position(), axis, visibleKLines, timeframe);
        int anchorY = axis.priceToY(text.position().price());
        if (text.color() != null && text.font() != null && text.text() != null && !text.text().isEmpty()) {
            g.setColor(text.color());
            g.setFont(text.font());
            FontMetrics fm = g.getFontMetrics();
            Rectangle2D textBounds = fm.getStringBounds(text.text(), g);
            float textWidth = (float) textBounds.getWidth();
            float ascent = fm.getAscent();
            float drawX = anchorX;
            float drawY = anchorY;
            switch (text.anchor()) {
                case TOP_LEFT:      drawX = anchorX;                 drawY = anchorY + ascent;        break;
                case TOP_CENTER:    drawX = anchorX - textWidth / 2; drawY = anchorY + ascent;        break;
                case TOP_RIGHT:     drawX = anchorX - textWidth;     drawY = anchorY + ascent;        break;
                case CENTER_LEFT:   drawX = anchorX;                 drawY = anchorY + ascent / 2;    break;
                case CENTER:        drawX = anchorX - textWidth / 2; drawY = anchorY + ascent / 2;    break;
                case CENTER_RIGHT:  drawX = anchorX - textWidth;     drawY = anchorY + ascent / 2;    break;
                case BOTTOM_LEFT:   drawX = anchorX;                 drawY = anchorY;                 break;
                case BOTTOM_CENTER: drawX = anchorX - textWidth / 2; drawY = anchorY;                 break;
                case BOTTOM_RIGHT:  drawX = anchorX - textWidth;     drawY = anchorY;                 break;
            }
            g.drawString(text.text(), drawX, drawY);
        }
    }
}