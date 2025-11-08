package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Graphics2D;
import java.util.List;

public class HollowCandleRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, ChartAxis axis, List<KLine> klines, int viewStartIndex, ChartDataModel dataModel) {
        if (!axis.isConfigured() || klines == null) return;

        SettingsService settings = SettingsService.getInstance();
        double barWidth = axis.getBarWidth();
        int candleBodyWidth = Math.max(1, (int) (barWidth * 0.8));

        for (int i = 0; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            int xCenter = axis.slotToX(i);

            int yOpen = axis.priceToY(kline.open());
            int yClose = axis.priceToY(kline.close());
            int yHigh = axis.priceToY(kline.high());
            int yLow = axis.priceToY(kline.low());

            boolean isBullish = kline.close().compareTo(kline.open()) >= 0;
            g2d.setColor(isBullish ? settings.getBullColor() : settings.getBearColor());

            // Wicks are always drawn
            g2d.drawLine(xCenter, yHigh, xCenter, Math.max(yOpen, yClose));
            g2d.drawLine(xCenter, yLow, xCenter, Math.min(yOpen, yClose));

            int bodyX = xCenter - candleBodyWidth / 2;
            int bodyY = Math.min(yOpen, yClose);
            int bodyHeight = Math.abs(yOpen - yClose);

            if (isBullish) {
                // Draw hollow body for bullish candles
                g2d.drawRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
            } else {
                // Draw filled body for bearish candles
                g2d.fillRect(bodyX, bodyY, candleBodyWidth, bodyHeight);
            }
        }
    }
}