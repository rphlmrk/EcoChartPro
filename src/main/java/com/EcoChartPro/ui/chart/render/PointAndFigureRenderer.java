package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.PointAndFigureColumn;
import com.EcoChartPro.ui.chart.axis.IChartAxis;

import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

public class PointAndFigureRenderer implements AbstractChartTypeRenderer {
    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null || visibleData.isEmpty()) {
            return;
        }
        if (!(visibleData.get(0) instanceof PointAndFigureColumn)) {
            return;
        }

        SettingsManager settings = SettingsManager.getInstance();
        // The generator uses the settings, so we just need them for rendering.
        // For now, let's assume they are available. These will be added in the integration step.
        BigDecimal boxSize = settings.getPfBoxSize();
        if (boxSize.compareTo(BigDecimal.ZERO) <= 0) {
            boxSize = BigDecimal.ONE; // Fallback to avoid infinite loops
        }

        double barWidth = axis.getBarWidth();
        int boxPixelWidth = Math.max(2, (int) (barWidth * 0.7));
        int boxInset = (int) ((barWidth - boxPixelWidth) / 2);

        for (int i = 0; i < visibleData.size(); i++) {
            PointAndFigureColumn column = (PointAndFigureColumn) visibleData.get(i);

            int absoluteIndex = i + viewStartIndex;
            int x = axis.slotToX(absoluteIndex);
            
            Color color = column.type() == PointAndFigureColumn.Type.COLUMN_OF_X 
                          ? settings.getBullColor() 
                          : settings.getBearColor();
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(1.5f));

            // Iterate through each box level in the column
            for (BigDecimal price = column.low(); price.compareTo(column.high()) < 0; price = price.add(boxSize)) {
                int yBottom = axis.priceToY(price);
                int yTop = axis.priceToY(price.add(boxSize));
                int boxPixelHeight = yBottom - yTop;

                int boxX = x - boxPixelWidth / 2;
                
                if (column.type() == PointAndFigureColumn.Type.COLUMN_OF_X) {
                    // Draw an 'X'
                    g2d.drawLine(boxX, yTop, boxX + boxPixelWidth, yBottom);
                    g2d.drawLine(boxX, yBottom, boxX + boxPixelWidth, yTop);
                } else {
                    // Draw an 'O'
                    g2d.drawOval(boxX, yTop, boxPixelWidth, boxPixelHeight);
                }
            }
        }
    }
}