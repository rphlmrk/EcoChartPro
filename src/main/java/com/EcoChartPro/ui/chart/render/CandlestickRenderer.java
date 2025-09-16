package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

public class CandlestickRenderer {

    /**
     * MODIFIED: Calculates the candle's position using the view's start index and screen slots.
     */
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex) {
        if (!axis.isConfigured() || klines == null) {
            return;
        }
        
        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int candleBodyWidth = Math.max(1, (int) (barWidth * 0.8)); 

        // The first k-line in the list corresponds to this global index
        int fromIndex = Math.max(0, viewStartIndex);

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            
            int globalKlineIndex = fromIndex + i;
            int slotIndex = globalKlineIndex - viewStartIndex;

            // Only draw if the candle falls within the visible slots on screen
            if (slotIndex >= 0 && slotIndex < axis.getBarsPerScreen()) {
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
}