package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.Graphics2D;
import java.util.List;

public class BarRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null) return;
        if (visibleData.isEmpty() || !(visibleData.get(0) instanceof KLine)) return; // Only for KLine data

        @SuppressWarnings("unchecked")
        List<KLine> klines = (List<KLine>) visibleData;

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