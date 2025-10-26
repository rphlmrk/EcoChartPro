package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class VisibleRangeVolumeProfileRenderer {

    private static final Color PROFILE_COLOR = new Color(158, 158, 158, 60);
    private static final Color POC_COLOR = new Color(255, 255, 255, 90);
    private static final double PROFILE_WIDTH_RATIO = 0.3; // 30% of the chart width

    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines) {
        if (!axis.isConfigured() || visibleKlines == null || visibleKlines.isEmpty()) {
            return;
        }

        // 1. Determine the price bin size (one bin per pixel row for precision)
        BigDecimal pricePerPixel = axis.getMaxPrice().subtract(axis.getMinPrice()).divide(BigDecimal.valueOf(g2d.getClipBounds().getHeight()), 8, RoundingMode.HALF_UP);
        if (pricePerPixel.compareTo(BigDecimal.ZERO) <= 0) return;

        // 2. Build the volume histogram
        Map<BigDecimal, BigDecimal> volumeHistogram = new TreeMap<>();
        for (KLine kline : visibleKlines) {
            // Distribute volume across price levels within the candle's range
            BigDecimal priceStep = pricePerPixel;
            for (BigDecimal price = kline.low(); price.compareTo(kline.high()) <= 0; price = price.add(priceStep)) {
                BigDecimal binPrice = price.divide(priceStep, 0, RoundingMode.HALF_UP).multiply(priceStep);
                // Simple approximation: add a fraction of the bar's volume to each price bin it covers
                volumeHistogram.merge(binPrice, kline.volume(), BigDecimal::add);
            }
        }

        // 3. Find the max volume (for scaling) and the Point of Control (POC)
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal pocPrice = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> entry : volumeHistogram.entrySet()) {
            if (entry.getValue().compareTo(maxVolume) > 0) {
                maxVolume = entry.getValue();
                pocPrice = entry.getKey();
            }
        }

        if (maxVolume.compareTo(BigDecimal.ZERO) <= 0) return;

        // 4. Render the histogram bars
        int maxBarWidth = (int) (g2d.getClipBounds().getWidth() * PROFILE_WIDTH_RATIO);
        for (Map.Entry<BigDecimal, BigDecimal> entry : volumeHistogram.entrySet()) {
            BigDecimal price = entry.getKey();
            BigDecimal volume = entry.getValue();
            int barWidth = (int) (maxBarWidth * (volume.doubleValue() / maxVolume.doubleValue()));
            int y = axis.priceToY(price);

            g2d.setColor(price.equals(pocPrice) ? POC_COLOR : PROFILE_COLOR);
            g2d.fillRect(g2d.getClipBounds().width - barWidth, y, barWidth, 1);
        }
    }
}