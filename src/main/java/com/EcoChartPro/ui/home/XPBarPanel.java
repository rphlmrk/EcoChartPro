package com.EcoChartPro.ui.home;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class XPBarPanel extends JPanel {
    private int level = 1;
    private long currentXp = 0;
    private long requiredXp = 100;
    private final JLabel levelLabel;
    private static final Color XP_BAR_COLOR = UIManager.getColor("app.color.accent");
    private static final Color XP_BAR_BG_COLOR = UIManager.getColor("Component.borderColor");

    public XPBarPanel() {
        setOpaque(false);
        // Use manual layout because we are custom painting and setting bounds.
        setLayout(null);
        // Set a non-zero preferred width so the component is visible in its parent layout.
        setPreferredSize(new Dimension(250, 30));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));

        levelLabel = new JLabel("Level 1");
        levelLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        levelLabel.setForeground(UIManager.getColor("Label.foreground"));
        levelLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        add(levelLabel);
    }

    public void updateProgress(int level, long currentXp, long requiredXp) {
        this.level = level;
        this.currentXp = currentXp;
        this.requiredXp = Math.max(1, requiredXp); // Avoid division by zero
        this.levelLabel.setText("Level " + level);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int barWidth = 150;
        int barHeight = 8;
        // Position bar on the far right of the panel
        int barX = getWidth() - barWidth - getInsets().right;
        int barY = (getHeight() - barHeight) / 2;

        // Draw background
        g2d.setColor(XP_BAR_BG_COLOR);
        g2d.fill(new RoundRectangle2D.Float(barX, barY, barWidth, barHeight, barHeight, barHeight));

        // Draw progress
        double progress = (double) currentXp / requiredXp;
        int progressWidth = (int) (barWidth * progress);
        g2d.setColor(XP_BAR_COLOR);
        g2d.fill(new RoundRectangle2D.Float(barX, barY, progressWidth, barHeight, barHeight, barHeight));

        // Position level label to the left of the bar
        Dimension labelSize = levelLabel.getPreferredSize();
        levelLabel.setBounds(barX - labelSize.width - 10, (getHeight() - labelSize.height) / 2,
                labelSize.width, labelSize.height);

        g2d.dispose();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (levelLabel != null) {
            levelLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
            levelLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
    }
}