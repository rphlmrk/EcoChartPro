package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.RangeBar;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.*;
import java.util.List;

public class RangeBarRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
         if (!axis.isConfigured() || visibleData == null || visibleData.isEmpty()) return;
         if (!(visibleData.get(0) instanceof RangeBar)) return; // Only for RangeBar data

        @SuppressWarnings("unchecked")
        List<RangeBar> rangeBars = (List<RangeBar>) visibleData;
        
        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int candleBodyWidth = Math.max(1, (int) (barWidth * 0.8)); 

        for (int i = 0; i < rangeBars.size(); i++) {
            RangeBar bar = rangeBars.get(i);
            
            // The slot index on the screen is simply 'i' because the visible list is already a slice.
            int xCenter = axis.slotToX(i);
            
            int yOpen = axis.priceToY(bar.open());
            int yClose = axis.priceToY(bar.close());
            int yHigh = axis.priceToY(bar.high());
            int yLow = axis.priceToY(bar.low());

            g2d.setColor(bar.close().compareTo(bar.open()) >= 0 ? settings.getBullColor() : settings.getBearColor());
            // Draw the high-low wick
            g2d.drawLine(xCenter, yHigh, xCenter, yLow);

            // Draw the open-close body
            if (bar.close().compareTo(bar.open()) >= 0) { // Bullish bar
                g2d.fillRect(xCenter - candleBodyWidth / 2, yClose, candleBodyWidth, yOpen - yClose);
            } else { // Bearish bar
                int bodyX = xCenter - candleBodyWidth / 2;
                int bodyY = yOpen;
                int bodyHeight = yClose - yOpen;
                g2d.setColor(settings.getBearColor());
                g2d.fillRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
                // Use chart background for the outline for theme compatibility, same as candles
                g2d.setColor(settings.getChartBackground());
                g2d.drawRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
            }
        }
    }
}