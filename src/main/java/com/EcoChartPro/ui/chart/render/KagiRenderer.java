package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.chart.KagiLine;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import java.awt.*;
import java.util.List;
import javax.swing.UIManager; // <--- FIX: Added missing import

public class KagiRenderer implements AbstractChartTypeRenderer {
    private static final Stroke YANG_STROKE = new BasicStroke(3.0f);
    private static final Stroke YIN_STROKE = new BasicStroke(1.0f);

    @Override
    public void draw(Graphics2D g2d, IChartAxis axis, List<? extends AbstractChartData> visibleData, int viewStartIndex) {
        if (!axis.isConfigured() || visibleData == null || visibleData.isEmpty()) return;
        if (!(visibleData.get(0) instanceof KagiLine)) return;

        Color bullColor = UIManager.getColor("app.color.positive");
        Color bearColor = UIManager.getColor("app.color.negative");

        for (int i = 0; i < visibleData.size(); i++) {
            KagiLine line = (KagiLine) visibleData.get(i);
            
            // Note: For index-based charts, the slot index is the absolute index relative to the start of the *entire* dataset.
            // The visibleData list is a slice, so 'i' is the index within the slice. The viewStartIndex tells us where that slice begins.
            int absoluteIndex = i + viewStartIndex;

            int x = axis.slotToX(absoluteIndex);
            int y1 = axis.priceToY(line.open());
            int y2 = axis.priceToY(line.close());
            
            g2d.setColor(line.close().compareTo(line.open()) >= 0 ? bullColor : bearColor);
            g2d.setStroke(line.type() == KagiLine.Type.YANG ? YANG_STROKE : YIN_STROKE);

            if (line.startTime().equals(line.endTime())) { // Shoulder or Waist (horizontal line)
                int prevX = axis.slotToX(absoluteIndex - 1);
                g2d.drawLine(prevX, y1, x, y1);
            } else { // Vertical line
                g2d.drawLine(x, y1, x, y2);
            }
        }
    }
}