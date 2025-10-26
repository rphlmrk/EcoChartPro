package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

public class CandleRenderer implements AbstractChartTypeRenderer {

    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex) {
        if (!axis.isConfigured() || klines == null) {
            return;
        }
        
        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int candleBodyWidth = Math.max(1, (int) (barWidth * 0.8)); 

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            
            // The slot index on the screen is simply 'i' because the visibleKlines list is already a slice.
            int slotIndex = i;

            int xCenter = axis.slotToX(slotIndex);
            
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
                // Use chart background for the outline for theme compatibility
                g2d.setColor(settings.getChartBackground());
                g2d.drawRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
            }
        }
    }
}