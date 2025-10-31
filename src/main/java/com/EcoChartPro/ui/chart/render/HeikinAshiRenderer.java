package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.*;
import java.util.List;

public class HeikinAshiRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex, ChartDataModel dataModel) {
        if (!axis.isConfigured() || klines == null) {
            return;
        }

        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int candleBodyWidth = Math.max(1, (int) (barWidth * 0.8));

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            int xCenter = axis.slotToX(i);

            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int yHigh = axis.priceToY(kline.high());
            int yLow = axis.priceToY(kline.low());

            // The color is determined by the Heikin-Ashi open and close, which are already calculated
            boolean isBullish = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isBullish ? settings.getBullColor() : settings.getBearColor());

            // [FIX] Render wicks precisely relative to the candle body for accurate HA representation.
            int bodyTopY = Math.min(yOpen, yClose);
            int bodyBottomY = Math.max(yOpen, yClose);
            int bodyHeight = bodyBottomY - bodyTopY;

            // Draw upper wick (from high to the top of the body)
            g2d.drawLine(xCenter, yHigh, xCenter, bodyTopY);

            // Draw lower wick (from low to the bottom of the body)
            g2d.drawLine(xCenter, yLow, xCenter, bodyBottomY);

            // Body
            int bodyX = xCenter - candleBodyWidth / 2;
            g2d.fillRect(bodyX, bodyTopY, candleBodyWidth, bodyHeight);

            // Add an outline to bearish candles for better visibility, matching the standard candle style.
            if (!isBullish) {
                g2d.setColor(settings.getChartBackground());
                g2d.drawRect(bodyX, bodyTopY, candleBodyWidth, bodyHeight);
            }
        }
    }
}