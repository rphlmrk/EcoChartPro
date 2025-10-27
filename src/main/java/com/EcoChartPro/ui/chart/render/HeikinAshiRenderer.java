package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.*;
import java.util.List;

public class HeikinAshiRenderer implements AbstractChartTypeRenderer {
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
            int xCenter = axis.slotToX(i);

            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int yHigh = axis.priceToY(kline.high());
            int yLow = axis.priceToY(kline.low());

            // The color is determined by the Heikin-Ashi open and close, which are already calculated
            boolean isBullish = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isBullish ? settings.getBullColor() : settings.getBearColor());

            // Wicks
            g2d.drawLine(xCenter, yHigh, xCenter, yLow);

            // Body
            if (isBullish) {
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