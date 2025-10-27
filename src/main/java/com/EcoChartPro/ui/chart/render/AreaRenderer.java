package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.util.List;

public class AreaRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null || visibleData.isEmpty()) return;
        if (!(visibleData.get(0) instanceof KLine)) return; // Only for KLine data

        @SuppressWarnings("unchecked")
        List<KLine> klines = (List<KLine>) visibleData;

        SettingsManager settings = SettingsManager.getInstance();
        Color lineColor = settings.getBullColor();
        Color areaColor = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 50); // 20% opacity

        GeneralPath path = new GeneralPath();
        KLine firstKline = klines.get(0);
        int firstX = axis.slotToX(0);
        path.moveTo(firstX, axis.priceToY(firstKline.close()));

        for (int i = 1; i < klines.size(); i++) {
            path.lineTo(axis.slotToX(i), axis.priceToY(klines.get(i).close()));
        }

        int lastX = axis.slotToX(klines.size() - 1);
        int chartHeight = g2d.getClipBounds().height;
        path.lineTo(lastX, chartHeight);
        path.lineTo(firstX, chartHeight);
        path.closePath();

        g2d.setColor(areaColor);
        g2d.fill(path);

        // Draw the top line
        g2d.setColor(lineColor);
        g2d.setStroke(new BasicStroke(2.0f));
        GeneralPath linePath = new GeneralPath();
        linePath.moveTo(firstX, axis.priceToY(firstKline.close()));
        for (int i = 1; i < klines.size(); i++) {
            linePath.lineTo(axis.slotToX(i), axis.priceToY(klines.get(i).close()));
        }
        g2d.draw(linePath);
    }
}