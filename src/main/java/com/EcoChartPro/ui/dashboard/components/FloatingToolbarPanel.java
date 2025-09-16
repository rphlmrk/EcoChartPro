package com.EcoChartPro.ui.dashboard.components;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A panel with a semi-transparent, rounded rectangle background,
 * suitable for creating floating toolbars or navigation bars. This component
 * is theme-aware and will adapt its background color to the current Look and Feel.
 */
public class FloatingToolbarPanel extends JPanel {

    private final int arcWidth;
    private final int arcHeight;

    public FloatingToolbarPanel(int arcWidth, int arcHeight) {
        this.arcWidth = arcWidth;
        this.arcHeight = arcHeight;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bgColor = UIManager.getColor("Panel.background");
        // Create a semi-transparent version of the theme's panel background color.
        Color transparentBg = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 230);
        g2d.setColor(transparentBg);
        
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arcWidth, arcHeight));
        
        g2d.dispose();
    }
}