package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class VisibleRangeVolumeProfileRenderer {

    private record VolumeBar(BigDecimal upVolume, BigDecimal downVolume) {
        BigDecimal totalVolume() {
            return upVolume.add(downVolume);
        }
    }

    private static final double PROFILE_WIDTH_RATIO = 0.3; // 30% of the chart width

    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> visibleKlines) {
        if (!axis.isConfigured() || visibleKlines == null || visibleKlines.isEmpty()) {
            return;
        }

        SettingsManager settings = SettingsManager.getInstance();
        int rowHeight = settings.getVrvpRowHeight();

        // 1. Determine the price bin size based on row height
        BigDecimal pricePerPixel = axis.getMaxPrice().subtract(axis.getMinPrice()).divide(BigDecimal.valueOf(g2d.getClipBounds().getHeight()), 8, RoundingMode.HALF_UP);
        if (pricePerPixel.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal priceStep = pricePerPixel.multiply(BigDecimal.valueOf(rowHeight));
        if (priceStep.compareTo(BigDecimal.ZERO) <= 0) return;

        // 2. Build the volume histogram with up/down separation
        Map<BigDecimal, VolumeBar> volumeHistogram = new TreeMap<>();
        for (KLine kline : visibleKlines) {
            boolean isUpBar = kline.close().compareTo(kline.open()) >= 0;
            long priceLevelsInBar = kline.high().subtract(kline.low()).divide(priceStep, 0, RoundingMode.UP).longValue();
            if (priceLevelsInBar == 0) priceLevelsInBar = 1;

            BigDecimal volumePerLevel = kline.volume().divide(BigDecimal.valueOf(priceLevelsInBar), 8, RoundingMode.HALF_UP);

            for (BigDecimal price = kline.low(); price.compareTo(kline.high()) <= 0; price = price.add(priceStep)) {
                BigDecimal binPrice = price.divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
                volumeHistogram.compute(binPrice, (p, bar) -> {
                    if (bar == null) {
                        return isUpBar ? new VolumeBar(volumePerLevel, BigDecimal.ZERO) : new VolumeBar(BigDecimal.ZERO, volumePerLevel);
                    } else {
                        return isUpBar ? new VolumeBar(bar.upVolume.add(volumePerLevel), bar.downVolume) : new VolumeBar(bar.upVolume, bar.downVolume.add(volumePerLevel));
                    }
                });
            }
        }

        // 3. Find max volume and POC
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal pocPrice = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, VolumeBar> entry : volumeHistogram.entrySet()) {
            if (entry.getValue().totalVolume().compareTo(maxVolume) > 0) {
                maxVolume = entry.getValue().totalVolume();
                pocPrice = entry.getKey();
            }
        }

        if (maxVolume.compareTo(BigDecimal.ZERO) <= 0) return;

        // 4. Render the histogram bars (growing from right to left)
        int chartWidth = g2d.getClipBounds().width;
        int maxBarWidth = (int) (chartWidth * PROFILE_WIDTH_RATIO);

        for (Map.Entry<BigDecimal, VolumeBar> entry : volumeHistogram.entrySet()) {
            BigDecimal price = entry.getKey();
            VolumeBar bar = entry.getValue();
            BigDecimal totalVolume = bar.totalVolume();

            int y = axis.priceToY(price);
            
            int totalBarWidth = (int) (maxBarWidth * (totalVolume.doubleValue() / maxVolume.doubleValue()));
            int upVolumeWidth = (int) (totalBarWidth * (bar.upVolume.doubleValue() / totalVolume.doubleValue()));
            int downVolumeWidth = totalBarWidth - upVolumeWidth;

            // Draw Up Volume part (from the right edge, inward)
            g2d.setColor(settings.getVrvpUpVolumeColor());
            g2d.fillRect(chartWidth - upVolumeWidth, y, upVolumeWidth, rowHeight);
            
            // Draw Down Volume part (adjacent to the up volume part, growing further inward)
            g2d.setColor(settings.getVrvpDownVolumeColor());
            g2d.fillRect(chartWidth - upVolumeWidth - downVolumeWidth, y, downVolumeWidth, rowHeight);

            // Highlight POC row
            if (price.equals(pocPrice)) {
                g2d.setStroke(settings.getVrvpPocLineStroke());
                g2d.setColor(settings.getVrvpPocColor());
                g2d.drawRect(chartWidth - totalBarWidth, y, totalBarWidth, rowHeight);
            }
        }
    }
}