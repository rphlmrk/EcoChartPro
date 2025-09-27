package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.journal.JournalAnalysisService.PnlDistributionBin;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * A custom component that displays a histogram chart for P&L distribution.
 */
public class HistogramChart extends JComponent {

    private List<PnlDistributionBin> data = Collections.emptyList();
    private final Color positiveColor = UIManager.getColor("app.color.positive");
    private final Color negativeColor = UIManager.getColor("app.color.negative");
    private final Color neutralColor = UIManager.getColor("app.color.neutral");
    private final Color textColor = UIManager.getColor("Label.foreground");
    private final Font labelFont = UIManager.getFont("Label.font").deriveFont(10f);
    private final Font valueFont = UIManager.getFont("Label.font").deriveFont(Font.BOLD, 11f);

    public void updateData(List<PnlDistributionBin> data) {
        this.data = (data == null) ? Collections.emptyList() : data;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (data.isEmpty()) {
            g2.setColor(textColor);
            String msg = "No P&L data to display.";
            FontMetrics fm = g2.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            g2.drawString(msg, (getWidth() - msgWidth) / 2, getHeight() / 2);
            return;
        }

        int padding = 20;
        int labelAreaHeight = 40;
        int chartWidth = getWidth() - 2 * padding;
        int chartHeight = getHeight() - 2 * padding - labelAreaHeight;
        
        int maxCount = data.stream().mapToInt(PnlDistributionBin::count).max().orElse(1);
        int barGap = 4;
        int barWidth = (chartWidth - (data.size() - 1) * barGap) / data.size();

        for (int i = 0; i < data.size(); i++) {
            PnlDistributionBin bin = data.get(i);
            
            int barHeight = (int) ((double) bin.count() / maxCount * chartHeight);
            int x = padding + i * (barWidth + barGap);
            int y = padding + chartHeight - barHeight;

            // Determine bar color
            if (bin.upperBound().signum() <= 0 && bin.lowerBound().signum() < 0) {
                g2.setColor(negativeColor);
            } else if (bin.lowerBound().signum() >= 0 && bin.upperBound().signum() > 0) {
                g2.setColor(positiveColor);
            } else {
                g2.setColor(neutralColor);
            }
            g2.fillRect(x, y, barWidth, barHeight);

            // Draw count above the bar
            g2.setColor(textColor);
            g2.setFont(valueFont);
            String countStr = String.valueOf(bin.count());
            FontMetrics fmValue = g2.getFontMetrics();
            int countWidth = fmValue.stringWidth(countStr);
            g2.drawString(countStr, x + (barWidth - countWidth) / 2, y - 5);
            
            // Draw label below the bar
            g2.setFont(labelFont);
            String label = bin.label().replace(" to ", "\n");
            FontMetrics fmLabel = g2.getFontMetrics();
            String[] lines = label.split("\n");
            int labelY = padding + chartHeight + fmLabel.getAscent() + 5;
            for(int lineNum=0; lineNum < lines.length; lineNum++) {
                String line = lines[lineNum];
                int lineWidth = fmLabel.stringWidth(line);
                g2.drawString(line, x + (barWidth - lineWidth) / 2, labelY + (lineNum * fmLabel.getHeight()));
            }
        }
    }
}