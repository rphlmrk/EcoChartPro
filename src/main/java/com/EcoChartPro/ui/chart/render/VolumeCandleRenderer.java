package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.util.List;

public class VolumeCandleRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex, ChartDataModel dataModel) {
        if (!axis.isConfigured() || klines == null || klines.isEmpty()) {
            return;
        }

        // 1. Find the min and max volume in the visible range
        BigDecimal minVolume = klines.get(0).volume();
        BigDecimal maxVolume = klines.get(0).volume();
        for (KLine kline : klines) {
            if (kline.volume().compareTo(minVolume) < 0) {
                minVolume = kline.volume();
            }
            if (kline.volume().compareTo(maxVolume) > 0) {
                maxVolume = kline.volume();
            }
        }

        BigDecimal volumeRange = maxVolume.subtract(minVolume);
        if (volumeRange.compareTo(BigDecimal.ZERO) <= 0) {
            volumeRange = BigDecimal.ONE; // Avoid division by zero
        }

        SettingsService settings = SettingsService.getInstance();
        double barWidth = axis.getBarWidth();
        int minCandleWidth = 2;
        int maxCandleWidth = Math.max(minCandleWidth, (int) (barWidth * 1.5));

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            int xCenter = axis.slotToX(i);

            // 2. Calculate dynamic candle width based on volume
            double volumeRatio = kline.volume().subtract(minVolume).doubleValue() / volumeRange.doubleValue();
            int candleBodyWidth = minCandleWidth + (int) (volumeRatio * (maxCandleWidth - minCandleWidth));
            candleBodyWidth = Math.max(1, candleBodyWidth);

            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int yHigh = axis.priceToY(kline.high());
            int yLow = axis.priceToY(kline.low());

            g2d.setColor(kline.close().compareTo(kline.open()) >= 0 ? settings.getBullColor() : settings.getBearColor());
            g2d.drawLine(xCenter, yHigh, xCenter, yLow);

            if (kline.close().compareTo(kline.open()) >= 0) {
                g2d.fillRect(xCenter - candleBodyWidth / 2, yClose, candleBodyWidth, yOpen - yClose);
            } else {
                int bodyX = xCenter - candleBodyWidth / 2;
                int bodyY = yOpen;
                int bodyHeight = yClose - yOpen;
                g2d.setColor(settings.getBearColor());
                g2d.fillRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
                g2d.setColor(settings.getChartBackground());
                g2d.drawRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
            }
        }
    }
}