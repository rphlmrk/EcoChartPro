package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

public class MfeMaeScatterPlot extends JComponent {

    public record TradeEfficiencyPoint(BigDecimal mfe, BigDecimal mae, BigDecimal pnl) {}

    private List<TradeEfficiencyPoint> data = Collections.emptyList();
    private BigDecimal maxMae = BigDecimal.ZERO;
    private BigDecimal maxMfe = BigDecimal.ZERO;

    public MfeMaeScatterPlot() {
        setOpaque(false);
    }

    public void updateData(List<TradeEfficiencyPoint> data) {
        this.data = (data != null) ? data : Collections.emptyList();
        calculateBounds();
        repaint();
    }

    private void calculateBounds() {
        if (data.isEmpty()) {
            maxMae = BigDecimal.ZERO;
            maxMfe = BigDecimal.ZERO;
            return;
        }

        maxMae = data.stream().map(TradeEfficiencyPoint::mae).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        maxMfe = data.stream().map(TradeEfficiencyPoint::mfe).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        
        // Make the plot square by using the larger of the two max values for both axes
        BigDecimal universalMax = maxMae.max(maxMfe);
        if (universalMax.compareTo(BigDecimal.ZERO) == 0) {
            universalMax = BigDecimal.ONE; // Avoid division by zero
        }
        
        // Add 10% padding
        universalMax = universalMax.multiply(new BigDecimal("1.1"));
        
        maxMae = universalMax;
        maxMfe = universalMax;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (data.isEmpty()) {
            drawEmptyState(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int padding = 30;
        int chartWidth = getWidth() - 2 * padding;
        int chartHeight = getHeight() - 2 * padding;

        // Draw axes
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.drawLine(padding, padding, padding, padding + chartHeight); // Y-axis
        g2d.drawLine(padding, padding + chartHeight, padding + chartWidth, padding + chartHeight); // X-axis

        // Draw labels and grid lines
        drawAxisLabelsAndGrid(g2d, padding, chartWidth, chartHeight);

        // Draw 45-degree reference line
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f}, 0f));
        g2d.drawLine(padding, padding + chartHeight, padding + chartWidth, padding);
        g2d.setStroke(new BasicStroke(1f));

        // Plot data points
        for (TradeEfficiencyPoint point : data) {
            int x = padding + (int) (point.mae().divide(maxMae, 4, RoundingMode.HALF_UP).doubleValue() * chartWidth);
            int y = (padding + chartHeight) - (int) (point.mfe().divide(maxMfe, 4, RoundingMode.HALF_UP).doubleValue() * chartHeight);

            if (point.pnl().signum() > 0) {
                g2d.setColor(new Color(UIManager.getColor("app.color.positive").getRed(), UIManager.getColor("app.color.positive").getGreen(), UIManager.getColor("app.color.positive").getBlue(), 180));
            } else if (point.pnl().signum() < 0) {
                 g2d.setColor(new Color(UIManager.getColor("app.color.negative").getRed(), UIManager.getColor("app.color.negative").getGreen(), UIManager.getColor("app.color.negative").getBlue(), 180));
            } else {
                g2d.setColor(new Color(UIManager.getColor("Label.disabledForeground").getRed(), UIManager.getColor("Label.disabledForeground").getGreen(), UIManager.getColor("Label.disabledForeground").getBlue(), 180));
            }

            g2d.fillOval(x - 3, y - 3, 7, 7);
        }

        g2d.dispose();
    }

    private void drawAxisLabelsAndGrid(Graphics2D g2d, int padding, int chartWidth, int chartHeight) {
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(10f));
        FontMetrics fm = g2d.getFontMetrics();

        // X-axis (MAE)
        String xLabel = "MAE ($)";
        g2d.drawString(xLabel, padding + (chartWidth - fm.stringWidth(xLabel)) / 2, padding + chartHeight + fm.getAscent() + 5);
        for (int i = 0; i <= 4; i++) {
            double value = maxMae.doubleValue() * i / 4.0;
            String label = String.format("%.0f", value);
            int x = padding + (chartWidth * i / 4) - fm.stringWidth(label)/2;
            g2d.drawString(label, x, padding + chartHeight + fm.getAscent() - 5);
        }

        // Y-axis (MFE)
        String yLabel = "MFE ($)";
        g2d.rotate(-Math.PI / 2);
        g2d.drawString(yLabel, -(padding + (chartHeight + fm.stringWidth(yLabel)) / 2), fm.getAscent());
        g2d.rotate(Math.PI / 2);
        for (int i = 0; i <= 4; i++) {
            double value = maxMfe.doubleValue() * i / 4.0;
            String label = String.format("%.0f", value);
            int y = padding + chartHeight - (chartHeight * i / 4) + fm.getAscent()/2;
            g2d.drawString(label, padding - fm.stringWidth(label) - 5, y);
        }
    }
    
    private void drawEmptyState(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content"));
        String text = "Not enough data for MFE/MAE analysis.";
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(text, x, y);
        g2d.dispose();
    }
}