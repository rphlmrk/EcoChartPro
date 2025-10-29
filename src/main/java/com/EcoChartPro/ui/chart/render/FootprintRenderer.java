package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.FootprintBar;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FootprintRenderer implements AbstractChartTypeRenderer {
    private static final Logger logger = LoggerFactory.getLogger(FootprintRenderer.class);
    private static final int MIN_CLUSTER_HEIGHT = 1; // Minimum pixel height per price row
    private static final Font BASE_CLUSTER_FONT = new Font("Monospaced", Font.PLAIN, 12); // Increased base for readability
    private static final Font DELTA_FONT = new Font("Monospaced", Font.BOLD, 12); // Match base increase
    private static final double IMBALANCE_RATIO = 3.0; // Highlight if one side > ratio * other
    private static final int RECT_FALLBACK_THRESHOLD = 8; // Pixels: Fallback to rects only if < this (increased)

    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines, int viewStartIndex, ChartDataModel dataModel) {
        if (!axis.isConfigured() || visibleKlines.isEmpty()) return;

        SettingsManager settings = SettingsManager.getInstance();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); // Crisp text

        Map<java.time.Instant, FootprintBar> footprintData = dataModel.getFootprintData();

        Color bullColor = settings.getBullColor();
        Color bearColor = settings.getBearColor();
        Color pocColor = new Color(settings.getGridColor().getRed(), settings.getGridColor().getGreen(), settings.getGridColor().getBlue(), 80);
        int alpha = (int) (settings.getFootprintCandleOpacity() / 100.0 * 255);
        Color bullBodyColor = new Color(bullColor.getRed(), bullColor.getGreen(), bullColor.getBlue(), alpha);
        Color bearBodyColor = new Color(bearColor.getRed(), bearColor.getGreen(), bearColor.getBlue(), alpha);

        for (int i = 0; i < visibleKlines.size(); i++) {
            KLine kline = visibleKlines.get(i);
            int xCenter = axis.slotToX(i);
            int barWidth = (int) axis.getBarWidth();
            FootprintBar fpBar = footprintData.get(kline.timestamp());
            if (fpBar == null) {
                drawFallbackCandle(g2d, axis, kline, i);
                continue;
            }

            TreeMap<BigDecimal, FootprintBar.BidAskVolume> clusters = fpBar.getClusters();
            if (clusters.isEmpty()) continue;

            // Draw semi-transparent candle body background (applies opacity)
            boolean isUp = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isUp ? bullBodyColor : bearBodyColor);
            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int bodyTop = Math.min(yOpen, yClose);
            int bodyBottom = Math.max(yOpen, yClose);
            g2d.fillRect(xCenter - barWidth / 2, bodyTop, barWidth, bodyBottom - bodyTop);

            // Draw wick (non-opaque)
            g2d.setColor(isUp ? bullColor : bearColor);
            g2d.drawLine(xCenter, axis.priceToY(kline.high()), xCenter, axis.priceToY(kline.low()));

            // Calculate effective cluster height (pixels per price bin)
            BigDecimal priceStep = fpBar.getPriceStep();
            double pixelsPerCluster = axis.priceToPixel(priceStep.doubleValue());
            logger.trace("Bar at {}: pixelsPerCluster={}, barWidth={}", kline.timestamp(), pixelsPerCluster, barWidth); // Debug zoom

            // Dynamic font scaling: Fit to ~90% of cluster height, min 8pt
            int fontSize = Math.max(8, (int) (pixelsPerCluster * 0.9));
            Font clusterFont = BASE_CLUSTER_FONT.deriveFont((float) fontSize);
            g2d.setFont(clusterFont);
            FontMetrics fm = g2d.getFontMetrics();

            boolean useRectFallback = pixelsPerCluster < RECT_FALLBACK_THRESHOLD;
            if (useRectFallback) {
                drawRectFallbackClusters(g2d, axis, clusters, xCenter, barWidth, bullBodyColor, bearBodyColor);
                logger.debug("Fallback to rects for bar at {}: pixelsPerCluster < {}", kline.timestamp(), RECT_FALLBACK_THRESHOLD);
            } else {
                BigDecimal pocPrice = fpBar.getPocPrice();

                // Draw clusters (bottom to top: low to high)
                for (Map.Entry<BigDecimal, FootprintBar.BidAskVolume> entry : clusters.entrySet()) {
                    BigDecimal price = entry.getKey();
                    FootprintBar.BidAskVolume pair = entry.getValue();
                    int yTop = axis.priceToY(price.add(priceStep));
                    int yBottom = axis.priceToY(price);
                    int clusterHeight = Math.max(MIN_CLUSTER_HEIGHT, yBottom - yTop);
                    int y_text = yTop + (clusterHeight - fm.getHeight()) / 2 + fm.getAscent();

                    // Optional subtle background for POC
                    if (price.equals(pocPrice)) {
                        g2d.setColor(pocColor);
                        g2d.fillRect(xCenter - barWidth / 2, yTop, barWidth, clusterHeight);
                    }

                    // Format and pad strings for fixed width (prevents overlap)
                    String bidStr = padLeft(formatVolume(pair.bidVolume()), 5); // Left-pad to 5 chars
                    String askStr = padRight(formatVolume(pair.askVolume()), 5); // Right-pad to 5 chars

                    // Highlight imbalances
                    Color bidColor = (pair.bidVolume().doubleValue() / Math.max(1, pair.askVolume().doubleValue()) > IMBALANCE_RATIO) ? bearColor.brighter() : bearColor;
                    Color askColor = (pair.askVolume().doubleValue() / Math.max(1, pair.bidVolume().doubleValue()) > IMBALANCE_RATIO) ? bullColor.brighter() : bullColor;

                    // Draw bid (left, sell/bear)
                    g2d.setColor(bidColor);
                    g2d.drawString(bidStr, xCenter - fm.stringWidth("x")/2 - fm.stringWidth(bidStr) - 2, y_text);

                    // Delimiter (centered)
                    g2d.setColor(Color.GRAY);
                    g2d.drawString("x", xCenter - fm.stringWidth("x") / 2, y_text);

                    // Draw ask (right, buy/bull)
                    g2d.setColor(askColor);
                    g2d.drawString(askStr, xCenter + fm.stringWidth("x")/2 + 2, y_text);
                }
            }

            // Draw delta at bottom (always, even in fallback)
            BigDecimal delta = fpBar.getTotalDelta();
            String deltaStr = formatVolume(delta);
            int yBottom = axis.priceToY(kline.low()) + g2d.getFontMetrics(DELTA_FONT).getHeight() + 4;
            g2d.setColor(delta.compareTo(BigDecimal.ZERO) >= 0 ? bullColor : bearColor);
            g2d.setFont(DELTA_FONT);
            FontMetrics deltaFm = g2d.getFontMetrics();
            g2d.drawString(deltaStr, xCenter - deltaFm.stringWidth(deltaStr) / 2, yBottom);
        }
    }

    // Fallback: Draw colored rectangles per cluster (no text) for very dense views
    private void drawRectFallbackClusters(Graphics2D g2d, ChartAxis axis, TreeMap<BigDecimal, FootprintBar.BidAskVolume> clusters, int xCenter, int barWidth, Color bullColor, Color bearColor) {
        for (Map.Entry<BigDecimal, FootprintBar.BidAskVolume> entry : clusters.entrySet()) {
            BigDecimal price = entry.getKey();
            FootprintBar.BidAskVolume pair = entry.getValue();
            BigDecimal totalVolume = pair.getTotalVolume();
            int y = axis.priceToY(price);
            int halfWidth = barWidth / 2;

            if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                // Left: bid (bear)
                int bidWidth = (int) (halfWidth * pair.bidVolume().divide(totalVolume, 4, RoundingMode.HALF_UP).doubleValue());
                g2d.setColor(bearColor);
                g2d.fillRect(xCenter - bidWidth, y - MIN_CLUSTER_HEIGHT / 2, bidWidth, MIN_CLUSTER_HEIGHT);
                // Right: ask (bull)
                int askWidth = (int) (halfWidth * pair.askVolume().divide(totalVolume, 4, RoundingMode.HALF_UP).doubleValue());
                g2d.setColor(bullColor);
                g2d.fillRect(xCenter, y - MIN_CLUSTER_HEIGHT / 2, askWidth, MIN_CLUSTER_HEIGHT);
            } else {
                g2d.setColor(bearColor.darker());
                g2d.drawLine(xCenter - halfWidth, y, xCenter, y);
                g2d.setColor(bullColor.darker());
                g2d.drawLine(xCenter, y, xCenter + halfWidth, y);
            }
        }
    }

    private void drawFallbackCandle(Graphics2D g2d, ChartAxis axis, KLine kline, int slotIndex) {
        // Existing fallback code (unchanged)
        SettingsManager settings = SettingsManager.getInstance();
        int candleBodyWidth = Math.max(1, (int) (axis.getBarWidth() * 0.8));
        int xCenter = axis.slotToX(slotIndex);

        int yOpen = axis.priceToY(kline.open());
        int yClose = axis.priceToY(kline.close());
        int yHigh = axis.priceToY(kline.high());
        int yLow = axis.priceToY(kline.low());

        boolean isUp = kline.close().compareTo(kline.open()) >= 0;
        g2d.setColor(isUp ? settings.getBullColor() : settings.getBearColor());
        g2d.drawLine(xCenter, yHigh, xCenter, yLow);
        g2d.fillRect(xCenter - candleBodyWidth / 2, Math.min(yOpen, yClose), candleBodyWidth, Math.abs(yOpen - yClose));
    }

    private String formatVolume(BigDecimal vol) {
        if (vol.abs().compareTo(BigDecimal.valueOf(1000)) >= 0) {
            return vol.divide(BigDecimal.valueOf(1000), 1, java.math.RoundingMode.HALF_UP) + "K";
        }
        return vol.setScale(0, java.math.RoundingMode.DOWN).toString();
    }

    private String padLeft(String str, int length) {
        return String.format("%" + length + "s", str); // Left-pad with spaces
    }

    private String padRight(String str, int length) {
        return String.format("%-" + length + "s", str); // Right-pad with spaces
    }
}