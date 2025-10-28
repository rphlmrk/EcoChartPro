package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FootprintRenderer implements AbstractChartTypeRenderer {
    private static final int MIN_CLUSTER_HEIGHT = 1; // Minimum pixel height per price row
    private static final Font CLUSTER_FONT = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font DELTA_FONT = new Font("SansSerif", Font.BOLD, 10);

    private record VolumePair(BigDecimal buy, BigDecimal sell) {}

    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines, int viewStartIndex) {
        if (!axis.isConfigured() || visibleKlines.isEmpty()) return;

        SettingsManager settings = SettingsManager.getInstance();
        g2d.setFont(CLUSTER_FONT);
        FontMetrics fm = g2d.getFontMetrics();

        BigDecimal priceStep = calculatePriceStep(axis);
        if (priceStep.compareTo(BigDecimal.ZERO) <= 0) return;

        Color bullColor = settings.getBullColor();
        Color bearColor = settings.getBearColor();
        Color pocColor = new Color(settings.getGridColor().getRed(), settings.getGridColor().getGreen(), settings.getGridColor().getBlue(), 80);
        Color bullBodyColor = new Color(bullColor.getRed(), bullColor.getGreen(), bullColor.getBlue(), 20); // 20% opacity
        Color bearBodyColor = new Color(bearColor.getRed(), bearColor.getGreen(), bearColor.getBlue(), 20); // 20% opacity

        for (int i = 0; i < visibleKlines.size(); i++) {
            KLine kline = visibleKlines.get(i);
            int xCenter = axis.slotToX(i);
            int barWidth = (int) axis.getBarWidth();

            // --- Draw Candle Body Background ---
            boolean isUp = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isUp ? bullBodyColor : bearBodyColor);
            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            g2d.fillRect(xCenter - barWidth / 2, Math.min(yOpen, yClose), barWidth, Math.abs(yOpen - yClose));
            
            // --- Aggregate Data ---
            // This is a placeholder. For production, this heavy lifting should be cached in ChartDataModel.
            TreeMap<BigDecimal, VolumePair> clusters = aggregateClusters(kline, priceStep);

            BigDecimal totalBuy = BigDecimal.ZERO;
            BigDecimal totalSell = BigDecimal.ZERO;
            BigDecimal pocVolume = BigDecimal.ZERO;
            BigDecimal pocPrice = kline.low();

            // First pass: find POC and calculate totals
            for (var entry : clusters.entrySet()) {
                VolumePair pair = entry.getValue();
                totalBuy = totalBuy.add(pair.buy);
                totalSell = totalSell.add(pair.sell);
                BigDecimal clusterTotal = pair.buy.add(pair.sell);
                if (clusterTotal.compareTo(pocVolume) > 0) {
                    pocVolume = clusterTotal;
                    pocPrice = entry.getKey();
                }
            }
            
            // --- Draw Clusters ---
            for (var entry : clusters.descendingMap().entrySet()) {
                BigDecimal price = entry.getKey();
                VolumePair pair = entry.getValue();

                int y_top = axis.priceToY(price.add(priceStep));
                int y_bottom = axis.priceToY(price);
                int clusterHeight = Math.max(MIN_CLUSTER_HEIGHT, y_bottom - y_top);
                int y_text = y_top + (clusterHeight - fm.getHeight()) / 2 + fm.getAscent();
                
                // Highlight POC row with a filled box
                if (price.compareTo(pocPrice) == 0) {
                    g2d.setColor(pocColor);
                    g2d.fillRect(xCenter - barWidth / 2, y_top, barWidth, clusterHeight);
                }

                // Draw Sell Volume (left of center)
                String sellStr = formatVolume(pair.sell);
                int sellStrWidth = fm.stringWidth(sellStr);
                g2d.setColor(bearColor);
                g2d.drawString(sellStr, xCenter - sellStrWidth - 5, y_text);

                // Delimiter
                g2d.setColor(Color.GRAY);
                g2d.drawString("x", xCenter - (fm.stringWidth("x") / 2), y_text);

                // Draw Buy Volume (right of center)
                String buyStr = formatVolume(pair.buy);
                g2d.setColor(bullColor);
                g2d.drawString(buyStr, xCenter + 5, y_text);
            }

            // --- Draw Delta at Bottom ---
            BigDecimal delta = totalBuy.subtract(totalSell);
            String deltaStr = formatVolume(delta);
            int yBottom = axis.priceToY(kline.low()) + fm.getHeight() + 4;
            g2d.setColor(delta.compareTo(BigDecimal.ZERO) >= 0 ? bullColor : bearColor);
            g2d.setFont(DELTA_FONT);
            FontMetrics deltaFm = g2d.getFontMetrics();
            g2d.drawString(deltaStr, xCenter - deltaFm.stringWidth(deltaStr) / 2, yBottom);
            g2d.setFont(CLUSTER_FONT); // Reset font for the next bar
        }
    }

    // This is a crude approximation. A real implementation MUST use tick data for accuracy.
    private TreeMap<BigDecimal, VolumePair> aggregateClusters(KLine kline, BigDecimal priceStep) {
        TreeMap<BigDecimal, VolumePair> clusters = new TreeMap<>();
        boolean isUp = kline.close().compareTo(kline.open()) >= 0;
        BigDecimal range = kline.high().subtract(kline.low());
        if (range.compareTo(BigDecimal.ZERO) <= 0) range = priceStep;

        int numBins = range.divide(priceStep, 0, RoundingMode.CEILING).intValue();
        if (numBins == 0) numBins = 1;

        BigDecimal volPerBin = kline.volume().divide(BigDecimal.valueOf(numBins), 2, RoundingMode.HALF_UP);

        for (BigDecimal p = kline.low(); p.compareTo(kline.high()) < 0; p = p.add(priceStep)) {
            // Skew volume based on candle direction. This is a very rough guess.
            BigDecimal buyVol = isUp ? volPerBin.multiply(new BigDecimal("0.6")) : volPerBin.multiply(new BigDecimal("0.4"));
            BigDecimal sellVol = volPerBin.subtract(buyVol);
            BigDecimal binPrice = p.divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
            clusters.put(binPrice, new VolumePair(buyVol, sellVol));
        }
        return clusters;
    }

    private BigDecimal calculatePriceStep(ChartAxis axis) {
        // Dynamic bin size. Aim for a reasonable number of rows on screen.
        int targetRowsOnScreen = 60;
        BigDecimal priceRange = axis.getMaxPrice().subtract(axis.getMinPrice());
        if(priceRange.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ONE;
        return priceRange.divide(BigDecimal.valueOf(targetRowsOnScreen), 8, RoundingMode.HALF_UP);
    }

    private String formatVolume(BigDecimal vol) {
        // Simple integer formatting for volume, matches screenshot
        return vol.setScale(0, RoundingMode.DOWN).toString();
    }
}