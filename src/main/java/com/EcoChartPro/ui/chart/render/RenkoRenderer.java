package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.RenkoBrick;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.*;
import java.util.List;

public class RenkoRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null) return;
        if (visibleData.isEmpty() || !(visibleData.get(0) instanceof RenkoBrick)) return;

        SettingsManager settings = SettingsManager.getInstance();
        double barWidth = axis.getBarWidth();
        int brickBodyWidth = Math.max(1, (int) (barWidth * 0.9));

        for (int i = 0; i < visibleData.size(); i++) {
            RenkoBrick brick = (RenkoBrick) visibleData.get(i);
            int xCenter = axis.slotToX(i);

            int yOpen = axis.priceToY(brick.open());
            int yClose = axis.priceToY(brick.close());

            int bodyX = xCenter - brickBodyWidth / 2;
            int bodyY = Math.min(yOpen, yClose);
            int bodyHeight = Math.abs(yOpen - yClose);

            if (brick.getType() == RenkoBrick.Type.UP) {
                g2d.setColor(settings.getBullColor());
                g2d.fillRect(bodyX, bodyY, brickBodyWidth, bodyHeight);
            } else {
                g2d.setColor(settings.getBearColor());
                g2d.fillRect(bodyX, bodyY, brickBodyWidth, bodyHeight);
                g2d.setColor(settings.getChartBackground());
                g2d.drawRect(bodyX, bodyY, brickBodyWidth, bodyHeight);
            }
        }
    }
}