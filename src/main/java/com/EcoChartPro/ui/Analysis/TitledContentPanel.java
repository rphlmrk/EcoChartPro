package com.EcoChartPro.ui.Analysis;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class TitledContentPanel extends JPanel {

    private final JLabel titleLabel;

    public TitledContentPanel(String title, JComponent content) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_title"));
        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        
        add(titleLabel, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    public void setTitle(String newTitle) {
        if (titleLabel != null) {
            titleLabel.setText(newTitle);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
        g2d.dispose();
        super.paintComponent(g);
    }
}