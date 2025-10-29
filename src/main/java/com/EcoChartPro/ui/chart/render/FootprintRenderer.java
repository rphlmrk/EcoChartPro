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
    private static final int MIN_CLUSTER_HEIGHT = 1;
    // [FIX] Increased base font sizes for better readability when zoomed in
    private static final Font BASE_CLUSTER_FONT = new Font("Monospaced", Font.PLAIN, 13);
    private static final Font DELTA_FONT = new Font("Monospaced", Font.BOLD, 13);
    private static final double IMBALANCE_RATIO = 3.0;
    private static final int RECT_FALLBACK_THRESHOLD = 8;
    private static final int TARGET_CLUSTERS = 60;
    private static final BigDecimal MIN_RENDER_STEP = new BigDecimal("0.00000001");

    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines, int viewStartIndex, ChartDataModel dataModel) {
        if (!axis.isConfigured() || visibleKlines.isEmpty()) return;

        SettingsManager settings = SettingsManager.getInstance();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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

            BigDecimal originalPriceStep = fpBar.getPriceStep();
            double pixelsPerOriginalStep = axis.priceToPixel(originalPriceStep.doubleValue());
            BigDecimal effectivePriceStep;

            if (pixelsPerOriginalStep < RECT_FALLBACK_THRESHOLD) {
                BigDecimal visiblePriceRange = axis.getMaxPrice().subtract(axis.getMinPrice());
                effectivePriceStep = calculateEffectivePriceStep(visiblePriceRange);
            } else {
                effectivePriceStep = originalPriceStep;
            }
            TreeMap<BigDecimal, FootprintBar.BidAskVolume> renderClusters = mergeClusters(clusters, effectivePriceStep);

            int renderWidth = barWidth;
            final int zoomThreshold = 30;
            final double maxScaleFactor = 2.5;
            final double scaleRange = 200.0;

            if (barWidth > zoomThreshold) {
                double zoomProgress = Math.min(1.0, (barWidth - zoomThreshold) / scaleRange);
                double scaleFactor = 1.0 + zoomProgress * (maxScaleFactor - 1.0);
                renderWidth = (int) (barWidth * scaleFactor);
            }
            
            boolean isUp = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isUp ? bullBodyColor : bearBodyColor);
            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int bodyTop = Math.min(yOpen, yClose);
            int bodyBottom = Math.max(yOpen, yClose);
            g2d.fillRect(xCenter - renderWidth / 2, bodyTop, renderWidth, bodyBottom - bodyTop);

            g2d.setColor(isUp ? bullColor : bearColor);
            g2d.drawLine(xCenter, axis.priceToY(kline.high()), xCenter, axis.priceToY(kline.low()));

            double pixelsPerCluster = axis.priceToPixel(effectivePriceStep.doubleValue());
            
            // [FIX] Increased minimum font size for better readability
            int fontSize = Math.max(9, (int) (pixelsPerCluster * 0.9));
            Font clusterFont = BASE_CLUSTER_FONT.deriveFont((float) fontSize);
            g2d.setFont(clusterFont);
            FontMetrics fm = g2d.getFontMetrics();

            boolean useRectFallback = pixelsPerCluster < RECT_FALLBACK_THRESHOLD;
            if (useRectFallback) {
                drawRectFallbackClusters(g2d, axis, renderClusters, xCenter, renderWidth, bullBodyColor, bearBodyColor, effectivePriceStep);
            } else {
                BigDecimal pocPrice = fpBar.getPocPrice();

                for (Map.Entry<BigDecimal, FootprintBar.BidAskVolume> entry : renderClusters.entrySet()) {
                    BigDecimal price = entry.getKey();
                    FootprintBar.BidAskVolume pair = entry.getValue();
                    int yTop = axis.priceToY(price.add(effectivePriceStep));
                    int yBottom = axis.priceToY(price);
                    int clusterHeight = Math.max(MIN_CLUSTER_HEIGHT, yBottom - yTop);
                    int y_text = yTop + (clusterHeight - fm.getHeight()) / 2 + fm.getAscent();

                    if (pocPrice.compareTo(price) >= 0 && pocPrice.compareTo(price.add(effectivePriceStep)) < 0) {
                        g2d.setColor(pocColor);
                        g2d.fillRect(xCenter - renderWidth / 2, yTop, renderWidth, clusterHeight);
                    }

                    String bidStr = padLeft(formatVolume(pair.bidVolume()), 5);
                    String askStr = padRight(formatVolume(pair.askVolume()), 5);

                    Color bidColor = (pair.bidVolume().doubleValue() / Math.max(1, pair.askVolume().doubleValue()) > IMBALANCE_RATIO) ? bearColor.brighter() : bearColor;
                    Color askColor = (pair.askVolume().doubleValue() / Math.max(1, pair.bidVolume().doubleValue()) > IMBALANCE_RATIO) ? bullColor.brighter() : bullColor;

                    g2d.setColor(bidColor);
                    g2d.drawString(bidStr, xCenter - fm.stringWidth("x")/2 - fm.stringWidth(bidStr) - 2, y_text);
                    g2d.setColor(Color.GRAY);
                    g2d.drawString("x", xCenter - fm.stringWidth("x") / 2, y_text);
                    g2d.setColor(askColor);
                    g2d.drawString(askStr, xCenter + fm.stringWidth("x")/2 + 2, y_text);
                }
            }

            BigDecimal delta = fpBar.getTotalDelta();
            String deltaStr = formatVolume(delta);
            int yBottom = axis.priceToY(kline.low()) + g2d.getFontMetrics(DELTA_FONT).getHeight() + 4;
            g2d.setColor(delta.compareTo(BigDecimal.ZERO) >= 0 ? bullColor : bearColor);
            g2d.setFont(DELTA_FONT);
            FontMetrics deltaFm = g2d.getFontMetrics();
            g2d.drawString(deltaStr, xCenter - deltaFm.stringWidth(deltaStr) / 2, yBottom);
        }
    }

    private void drawRectFallbackClusters(Graphics2D g2d, ChartAxis axis, TreeMap<BigDecimal, FootprintBar.BidAskVolume> clusters, int xCenter, int renderWidth, Color bullColor, Color bearColor, BigDecimal effectivePriceStep) {
        for (Map.Entry<BigDecimal, FootprintBar.BidAskVolume> entry : clusters.entrySet()) {
            BigDecimal price = entry.getKey();
            FootprintBar.BidAskVolume pair = entry.getValue();
            BigDecimal totalVolume = pair.getTotalVolume();
            
            int yTop = axis.priceToY(price.add(effectivePriceStep));
            int yBottom = axis.priceToY(price);
            int height = Math.max(MIN_CLUSTER_HEIGHT, yBottom - yTop);
            int halfWidth = renderWidth / 2;

            if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                int bidWidth = (int) (halfWidth * pair.bidVolume().divide(totalVolume, 4, RoundingMode.HALF_UP).doubleValue());
                g2d.setColor(bearColor);
                g2d.fillRect(xCenter - bidWidth, yTop, bidWidth, height);

                int askWidth = (int) (halfWidth * pair.askVolume().divide(totalVolume, 4, RoundingMode.HALF_UP).doubleValue());
                g2d.setColor(bullColor);
                g2d.fillRect(xCenter, yTop, askWidth, height);
            } else {
                int y = yTop + height / 2;
                g2d.setColor(bearColor.darker());
                g2d.drawLine(xCenter - halfWidth, y, xCenter, y);
                g2d.setColor(bullColor.darker());
                g2d.drawLine(xCenter, y, xCenter + halfWidth, y);
            }
        }
    }

    private void drawFallbackCandle(Graphics2D g2d, ChartAxis axis, KLine kline, int slotIndex) {
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
        return String.format("%" + length + "s", str);
    }

    private String padRight(String str, int length) {
        return String.format("%-" + length + "s", str);
    }

    private BigDecimal calculateEffectivePriceStep(BigDecimal visiblePriceRange) {
        if (visiblePriceRange == null || visiblePriceRange.compareTo(BigDecimal.ZERO) <= 0) {
            return MIN_RENDER_STEP;
        }
        BigDecimal targetStep = visiblePriceRange.divide(BigDecimal.valueOf(TARGET_CLUSTERS), 8, RoundingMode.HALF_UP);
        return targetStep.max(MIN_RENDER_STEP);
    }

    private TreeMap<BigDecimal, FootprintBar.BidAskVolume> mergeClusters(TreeMap<BigDecimal, FootprintBar.BidAskVolume> originalClusters, BigDecimal mergeStep) {
        if (originalClusters.isEmpty()) {
            return new TreeMap<>();
        }

        TreeMap<BigDecimal, FootprintBar.BidAskVolume> merged = new TreeMap<>();
        BigDecimal currentBinStart = null;
        BigDecimal accumulatedBid = BigDecimal.ZERO;
        BigDecimal accumulatedAsk = BigDecimal.ZERO;

        for (Map.Entry<BigDecimal, FootprintBar.BidAskVolume> entry : originalClusters.entrySet()) {
            BigDecimal price = entry.getKey();
            FootprintBar.BidAskVolume vol = entry.getValue();

            BigDecimal binPrice = price.divide(mergeStep, 0, RoundingMode.FLOOR).multiply(mergeStep);

            if (currentBinStart == null || !binPrice.equals(currentBinStart)) {
                if (currentBinStart != null) {
                    merged.put(currentBinStart, new FootprintBar.BidAskVolume(accumulatedBid, accumulatedAsk));
                }
                currentBinStart = binPrice;
                accumulatedBid = vol.bidVolume();
                accumulatedAsk = vol.askVolume();
            } else {
                accumulatedBid = accumulatedBid.add(vol.bidVolume());
                accumulatedAsk = accumulatedAsk.add(vol.askVolume());
            }
        }

        if (currentBinStart != null) {
            merged.put(currentBinStart, new FootprintBar.BidAskVolume(accumulatedBid, accumulatedAsk));
        }

        return merged;
    }
}