package com.EcoChartPro.ui.Analysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class WinRateBar extends JComponent {
    private double progress;

    public WinRateBar() {
        this.progress = 0.0;
        setPreferredSize(new Dimension(100, 1));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // This method is called when the Look and Feel changes.
        // We just need to repaint to pick up the new UIManager colors.
        repaint();
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = getHeight();
        g2d.setColor(UIManager.getColor("ProgressBar.trackColor"));
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
        int progressWidth = (int) (getWidth() * progress);
        if (progressWidth > 0) {
            g2d.setColor(UIManager.getColor("ProgressBar.foreground"));
            g2d.fill(new RoundRectangle2D.Float(0, 0, progressWidth, getHeight(), arc, arc));
        }
        g2d.dispose();
    }
}