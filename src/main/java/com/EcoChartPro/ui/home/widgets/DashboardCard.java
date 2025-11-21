package com.EcoChartPro.ui.home.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class DashboardCard extends JPanel {

    private final String title;
    private final JComponent content;
    private final JComponent headerAction;

    public DashboardCard(String title, JComponent content) {
        this(title, content, null);
    }

    public DashboardCard(String title, JComponent content, JComponent headerAction) {
        this.title = title;
        this.content = content;
        this.headerAction = headerAction;

        setOpaque(false);
        setLayout(new BorderLayout());
        // Outer padding for the grid gap, Inner padding for content
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(createHeader(), BorderLayout.NORTH);

        // Ensure content doesn't paint its own background over ours
        if (content instanceof JComponent) {
            ((JComponent) content).setOpaque(false);
            ((JComponent) content).setBorder(null);
        }
        add(content, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_title")); // Use theme font
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(titleLabel, BorderLayout.WEST);

        if (headerAction != null) {
            header.add(headerAction, BorderLayout.EAST);
        }
        return header;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 20; // Bento-style rounded corners

        // Background
        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));

        // Subtle Border
        g2d.setColor(UIManager.getColor("Component.borderColor"));
        g2d.setStroke(new BasicStroke(1f));
        g2d.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc));

        g2d.dispose();
        super.paintComponent(g);
    }

    // Helper to wrap a component easily
    public static DashboardCard wrap(String title, JComponent content) {
        return new DashboardCard(title, content);
    }
}