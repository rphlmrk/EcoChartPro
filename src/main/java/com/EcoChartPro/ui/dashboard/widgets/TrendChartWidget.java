package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;

public class TrendChartWidget extends JPanel {

    public record DataPoint(String label, double value) {}

    private String title = "Chart";
    private List<DataPoint> dataPoints = Collections.emptyList();

    public TrendChartWidget() {
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    public void setData(String title, List<DataPoint> data) {
        this.title = title;
        this.dataPoints = (data != null) ? data : Collections.emptyList();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (dataPoints.isEmpty()) {
            drawEmptyState(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int padding = 10;
        int labelHeight = 30;
        int titleHeight = 25;
        int chartX = padding + 35;
        int chartY = padding + titleHeight;
        int chartWidth = getWidth() - (padding * 2) - 35;
        int chartHeight = getHeight() - (padding * 2) - labelHeight - titleHeight;

        // --- Draw Title ---
        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        g2d.setColor(UIManager.getColor("Label.foreground"));
        g2d.drawString(title, padding, padding + 15);

        // --- Calculate Data Range ---
        double minVal = dataPoints.stream().mapToDouble(DataPoint::value).min().orElse(0.0);
        double maxVal = dataPoints.stream().mapToDouble(DataPoint::value).max().orElse(0.0);

        // Adjust range to include zero baseline
        if (minVal > 0) minVal = 0;
        if (maxVal < 0) maxVal = 0;
        if (minVal == maxVal) { // Handle case where all values are the same (e.g., all zero)
            minVal -= 1;
            maxVal += 1;
        }
        double range = maxVal - minVal;

        // --- Draw Y-Axis and Labels ---
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.drawLine(chartX, chartY, chartX, chartY + chartHeight); // Y-Axis Line
        drawYAxisLabels(g2d, minVal, maxVal, chartX, chartY, chartHeight);

        // --- Draw Zero Baseline ---
        int zeroY = chartY + (int) (chartHeight * (maxVal / range));
        g2d.setColor(UIManager.getColor("Component.focusedBorderColor"));
        g2d.drawLine(chartX, zeroY, chartX + chartWidth, zeroY); // X-Axis (Zero Line)

        // --- Draw Bars ---
        float barWidth = (float) chartWidth / dataPoints.size() * 0.7f;
        float barSpacing = (float) chartWidth / dataPoints.size();
        
        for (int i = 0; i < dataPoints.size(); i++) {
            DataPoint dp = dataPoints.get(i);
            int barHeight = (int) (chartHeight * (dp.value() / range));
            int barX = chartX + (int)(i * barSpacing + (barSpacing - barWidth) / 2);
            int barY = zeroY - barHeight;

            if (dp.value() < 0) {
                barY = zeroY;
                barHeight = -barHeight;
                g2d.setColor(UIManager.getColor("app.color.negative"));
            } else {
                g2d.setColor(UIManager.getColor("app.color.positive"));
            }

            g2d.fillRect(barX, barY, (int)barWidth, barHeight);
            
            // Draw Label
            g2d.setColor(UIManager.getColor("Label.disabledForeground"));
            g2d.setFont(UIManager.getFont("defaultFont").deriveFont(9f));
            FontRenderContext frc = g2d.getFontRenderContext();
            Rectangle2D labelBounds = g2d.getFont().getStringBounds(dp.label(), frc);
            int labelX = barX + ((int)barWidth / 2) - ((int)labelBounds.getWidth() / 2);
            g2d.drawString(dp.label(), labelX, chartY + chartHeight + 15);
        }

        g2d.dispose();
    }
    
    private void drawYAxisLabels(Graphics2D g2d, double min, double max, int x, int y, int height) {
        g2d.setFont(UIManager.getFont("defaultFont").deriveFont(9f));
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        DecimalFormat df = new DecimalFormat("0.##");

        // Simple 3-point axis: Max, Zero, Min
        g2d.drawString(df.format(max), x - 30, y + 10);
        g2d.drawString("0", x - 30, y + (int) (height * (max / (max - min))));
        g2d.drawString(df.format(min), x - 30, y + height);
    }

    private void drawEmptyState(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content"));
        String text = "Not enough data to display chart.";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, x, y);
        g2d.dispose();
    }
}