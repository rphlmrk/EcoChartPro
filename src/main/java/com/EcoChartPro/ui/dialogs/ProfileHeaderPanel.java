package com.EcoChartPro.ui.dialogs;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;

public class ProfileHeaderPanel extends JPanel {
    private final JLabel levelLabel;
    private final JLabel titleLabel;
    private final JLabel xpLabel;
    private long currentXp = 0;
    private long requiredXp = 100;
    private static final Color XP_BAR_COLOR = javax.swing.UIManager.getColor("app.color.accent");
    private static final Color XP_BAR_BG_COLOR = javax.swing.UIManager.getColor("Component.borderColor");

    public ProfileHeaderPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));
        setLayout(new GridBagLayout());

        levelLabel = new JLabel("Level 1");
        levelLabel.setFont(javax.swing.UIManager.getFont("app.font.heading"));

        titleLabel = new JLabel("Novice Analyst");
        titleLabel.setFont(javax.swing.UIManager.getFont("app.font.subheading"));
        titleLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        
        xpLabel = new JLabel("0 / 100 XP");
        xpLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_content"));
        xpLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 0);

        gbc.gridx = 0; gbc.gridy = 0;
        add(levelLabel, gbc);

        gbc.gridy = 1;
        add(titleLabel, gbc);
        
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 0, 2, 0);
        // Placeholder for the custom painted bar, we just add a strut to reserve space
        add(Box.createVerticalStrut(12), gbc);

        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, 0, 0);
        add(xpLabel, gbc);
    }

    public void updateData(int level, String title, long currentXp, long requiredXp) {
        this.currentXp = currentXp;
        this.requiredXp = Math.max(1, requiredXp);
        
        levelLabel.setText("Level " + level);
        titleLabel.setText(title);

        NumberFormat numberFormat = NumberFormat.getInstance();
        xpLabel.setText(String.format("%s / %s XP", numberFormat.format(currentXp), numberFormat.format(this.requiredXp)));
        
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bar position is calculated relative to the xpLabel's position
        int barY = xpLabel.getY() - 14; 
        int barHeight = 12;

        // Draw background
        g2d.setColor(XP_BAR_BG_COLOR);
        g2d.fill(new RoundRectangle2D.Float(0, barY, getWidth(), barHeight, barHeight, barHeight));

        // Draw progress
        double progress = (double) currentXp / requiredXp;
        int progressWidth = (int) (getWidth() * progress);
        g2d.setColor(XP_BAR_COLOR);
        g2d.fill(new RoundRectangle2D.Float(0, barY, progressWidth, barHeight, barHeight, barHeight));

        g2d.dispose();
    }
}