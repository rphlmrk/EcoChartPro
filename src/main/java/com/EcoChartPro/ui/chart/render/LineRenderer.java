package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.util.List;

public class LineRenderer implements AbstractChartTypeRenderer {
    private final boolean withMarkers;

    public LineRenderer(boolean withMarkers) {
        this.withMarkers = withMarkers;
    }

    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null || visibleData.isEmpty()) return;
        if (!(visibleData.get(0) instanceof KLine)) return; // Only for KLine data

        @SuppressWarnings("unchecked")
        List<KLine> klines = (List<KLine>) visibleData;

        SettingsManager settings = SettingsManager.getInstance();
        g2d.setColor(settings.getBullColor());
        g2d.setStroke(new BasicStroke(2.0f));

        GeneralPath path = new GeneralPath();
        KLine firstKline = klines.get(0);
        path.moveTo(axis.slotToX(0), axis.priceToY(firstKline.close()));

        for (int i = 1; i < klines.size(); i++) {
            KLine kline = klines.get(i);
            path.lineTo(axis.slotToX(i), axis.priceToY(kline.close()));
        }
        g2d.draw(path);

        if (withMarkers) {
            int markerSize = 6;
            for (int i = 0; i < klines.size(); i++) {
                KLine kline = klines.get(i);
                int x = axis.slotToX(i) - markerSize / 2;
                int y = axis.priceToY(kline.close()) - markerSize / 2;
                g2d.fillOval(x, y, markerSize, markerSize);
            }
        }
    }
}