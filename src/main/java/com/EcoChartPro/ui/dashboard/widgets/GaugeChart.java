package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;

public class GaugeChart extends JComponent {

    private double value;
    private double splitValue;
    private Color primaryColor;
    private Color secondaryColor;
    private GaugeType type;
    private String topLeftLabel, topRightLabel;

    public enum GaugeType { SEMI_CIRCLE, FULL_CIRCLE }

    public GaugeChart(GaugeType type) {
        this.type = type;
        setOpaque(false);
        updateUI(); // Set initial theme colors
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Set default semantic colors from the theme
        this.primaryColor = UIManager.getColor("app.color.accent");
        this.secondaryColor = UIManager.getColor("app.color.negative");
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (type == GaugeType.SEMI_CIRCLE) {
            return new Dimension(100, 55);
        } else {
            return new Dimension(80, 80);
        }
    }

    public void setData(double value) {
        this.value = value;
        this.splitValue = 0;
        repaint();
    }

    public void setTwoColorData(double value, double splitValue, Color primary, Color secondary) {
        this.value = value;
        this.splitValue = splitValue;
        this.primaryColor = primary;
        this.secondaryColor = secondary;
        repaint();
    }

    public void setExtraLabels(String topLeft, String topRight) {
        this.topLeftLabel = topLeft;
        this.topRightLabel = topRight;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int strokeWidth = 10;

        if (type == GaugeType.SEMI_CIRCLE) {
            drawSemiCircle(g2d, strokeWidth);
        } else {
            drawFullCircle(g2d, strokeWidth);
        }
        g2d.dispose();
    }

    private void drawSemiCircle(Graphics2D g2d, int strokeWidth) {
        int padding = strokeWidth / 2 + 2;
        int labelHeight = 15;
        int availableWidth = getWidth() - 2 * padding;
        int availableHeight = getHeight() - padding - labelHeight;
        if (availableWidth <= 0 || availableHeight <= 0) return;
        int diameter = Math.min(availableWidth, 2 * availableHeight);
        int x = (getWidth() - diameter) / 2;
        int y = padding;

        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 180, -180, Arc2D.OPEN));

        if (splitValue > 0 && value >= splitValue) {
            double primaryAngle = -splitValue * 180;
            double secondaryAngle = -(value - splitValue) * 180;
            g2d.setColor(secondaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 180 + primaryAngle, secondaryAngle, Arc2D.OPEN));
            g2d.setColor(primaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 180, primaryAngle, Arc2D.OPEN));
        } else {
            double fillAngle = -value * 180;
            g2d.setColor(primaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 180, fillAngle, Arc2D.OPEN));
        }

        g2d.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 10f));
        g2d.setColor(UIManager.getColor("Label.disabledForeground"));
        FontMetrics fm = g2d.getFontMetrics();
        int labelY = getHeight() - fm.getDescent() - 2;

        if (topLeftLabel != null) g2d.drawString(topLeftLabel, x, labelY);
        if (topRightLabel != null) {
            int stringWidth = fm.stringWidth(topRightLabel);
            g2d.drawString(topRightLabel, x + diameter - stringWidth, labelY);
        }
    }

    private void drawFullCircle(Graphics2D g2d, int strokeWidth) {
        int padding = strokeWidth / 2 + 2;
        int diameter = Math.min(getWidth(), getHeight()) - (padding * 2);
        int x = (getWidth() - diameter) / 2;
        int y = (getHeight() - diameter) / 2;

        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 90, -360, Arc2D.OPEN));

        if (splitValue > 0 && value >= splitValue) {
            double primaryAngle = -splitValue * 360;
            double secondaryAngle = -(value - splitValue) * 360;
            g2d.setColor(secondaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 90 + primaryAngle, secondaryAngle, Arc2D.OPEN));
            g2d.setColor(primaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 90, primaryAngle, Arc2D.OPEN));
        } else {
            double fillAngle = -value * 360;
            g2d.setColor(primaryColor);
            g2d.draw(new Arc2D.Double(x, y, diameter, diameter, 90, fillAngle, Arc2D.OPEN));
        }
    }
}