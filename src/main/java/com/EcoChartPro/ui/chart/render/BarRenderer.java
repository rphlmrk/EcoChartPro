package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Graphics2D;
import java.util.List;

public class BarRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex) {
        if (!axis.isConfigured() || klines == null) return;

        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int tickWidth = Math.max(1, (int) (barWidth / 4));

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            int xCenter = axis.slotToX(i);

            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int yHigh = axis.priceToY(kline.high());
            int yLow = axis.priceToY(kline.low());

            g2d.setColor(kline.close().compareTo(kline.open()) >= 0 ? settings.getBullColor() : settings.getBearColor());

            // Vertical high-low line
            g2d.drawLine(xCenter, yHigh, xCenter, yLow);
            // Open tick (left)
            g2d.drawLine(xCenter - tickWidth, yOpen, xCenter, yOpen);
            // Close tick (right)
            g2d.drawLine(xCenter, yClose, xCenter + tickWidth, yClose);
        }
    }
}