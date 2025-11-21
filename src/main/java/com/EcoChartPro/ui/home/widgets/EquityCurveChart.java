package com.EcoChartPro.ui.home.widgets;

import com.EcoChartPro.core.journal.JournalAnalysisService.EquityPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * A custom JComponent that renders a filled area chart of the account's equity curve over time.
 */
public class EquityCurveChart extends JComponent {

    private List<EquityPoint> equityCurve = Collections.emptyList();
    
    public EquityCurveChart() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 150));
    }

    public void updateData(List<EquityPoint> data) {
        this.equityCurve = (data != null) ? data : Collections.emptyList();
        repaint();
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (equityCurve == null || equityCurve.size() < 2) {
            drawEmptyState(g);
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int padding = 5;
        int chartHeight = height - (2 * padding);
        int chartWidth = width - (2 * padding);

        BigDecimal minBalance = equityCurve.stream().map(EquityPoint::cumulativeBalance).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxBalance = equityCurve.stream().map(EquityPoint::cumulativeBalance).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal range = maxBalance.subtract(minBalance);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            range = maxBalance.abs().max(BigDecimal.ONE);
        }

        Path2D areaPath = new Path2D.Double();
        Path2D linePath = new Path2D.Double();
        
        areaPath.moveTo(padding, height - padding);

        for (int i = 0; i < equityCurve.size(); i++) {
            double x = padding + ((double) i / (equityCurve.size() - 1)) * chartWidth;
            double y = padding + (chartHeight - getRelativeHeight(equityCurve.get(i).cumulativeBalance(), minBalance, range, chartHeight));
            
            if (i == 0) {
                linePath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
            }
            areaPath.lineTo(x, y);
        }
        
        areaPath.lineTo(width - padding, height - padding);
        areaPath.closePath();

        Color accentColor = UIManager.getColor("Component.focusedBorderColor");
        if (accentColor == null) {
            accentColor = UIManager.getColor("app.color.neutral"); 
        }

        GradientPaint gradient = new GradientPaint(0, padding, new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80), 0, height, new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0));
        g2d.setPaint(gradient);
        g2d.fill(areaPath);

        g2d.setColor(accentColor);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.draw(linePath);

        g2d.dispose();
    }
    
    private double getRelativeHeight(BigDecimal value, BigDecimal min, BigDecimal range, int chartHeight) {
        if (range.compareTo(BigDecimal.ZERO) == 0) return 0;
        BigDecimal offset = value.subtract(min);
        return offset.divide(range, 4, RoundingMode.HALF_UP).doubleValue() * chartHeight;
    }
    
    private void drawEmptyState(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        g2d.setFont(UIManager.getFont("app.font.widget_content"));
        String text = "Not enough data to draw equity curve.";
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2 + fm.getAscent() / 2);
        g2d.dispose();
    }
}