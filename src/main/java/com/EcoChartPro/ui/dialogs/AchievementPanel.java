package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.gamification.Achievement;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom panel to display a single achievement's state (locked, unlocked, secret).
 */
public class AchievementPanel extends JPanel {

    // Use fully qualified name to avoid ambiguity
    private static final Color UNLOCKED_BG_COLOR = javax.swing.UIManager.getColor("Panel.background");
    private static final Color LOCKED_BG_COLOR = javax.swing.UIManager.getColor("Component.borderColor");
    private static final Color UNLOCKED_TEXT_COLOR = javax.swing.UIManager.getColor("Label.foreground");
    private static final Color LOCKED_TEXT_COLOR = javax.swing.UIManager.getColor("Label.disabledForeground");

    public AchievementPanel(Achievement achievement, boolean isUnlocked) {
        setOpaque(false);
        // Switched to GridBagLayout for better vertical alignment and text wrapping.
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(10, 15, 10, 15));

        GridBagConstraints gbc = new GridBagConstraints();

        // --- Icon ---
        JLabel iconLabel = new JLabel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0; // Icon column does not stretch
        gbc.anchor = GridBagConstraints.CENTER; // Center icon vertically
        gbc.insets = new Insets(0, 0, 0, 15); // Add right padding to the icon
        add(iconLabel, gbc);

        // --- Text Panel ---
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel();
        titleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_title"));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JTextArea descriptionArea = new JTextArea();
        descriptionArea.setOpaque(false);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        // Make text area non-focusable to remove cursor artifact
        descriptionArea.setFocusable(false);
        descriptionArea.setFont(javax.swing.UIManager.getFont("app.font.widget_content"));
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descriptionArea);
        
        // --- Add Text Panel to main panel ---
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0; // Text column stretches to fill remaining space
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 0, 0, 0); // Reset insets
        add(textPanel, gbc);

        if (isUnlocked) {
            setBackground(UNLOCKED_BG_COLOR);
            iconLabel.setIcon(UITheme.getIcon(achievement.iconPath(), 48, 48, javax.swing.UIManager.getColor("app.color.accent")));
            titleLabel.setText(achievement.title());
            titleLabel.setForeground(UNLOCKED_TEXT_COLOR);
            descriptionArea.setText(achievement.description());
            descriptionArea.setForeground(UNLOCKED_TEXT_COLOR);
        } else {
            setBackground(LOCKED_BG_COLOR);
            if (achievement.isSecret()) {
                iconLabel.setIcon(UITheme.getIcon(UITheme.Icons.HELP, 48, 48, LOCKED_TEXT_COLOR));
                titleLabel.setText("Secret Achievement");
                descriptionArea.setText("Keep playing to discover and unlock this goal.");
            } else {
                iconLabel.setIcon(UITheme.getIcon(achievement.iconPath(), 48, 48, LOCKED_TEXT_COLOR));
                titleLabel.setText(achievement.title());
                descriptionArea.setText(achievement.description());
            }
            titleLabel.setForeground(LOCKED_TEXT_COLOR);
            descriptionArea.setForeground(LOCKED_TEXT_COLOR);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
        g2d.dispose();
        super.paintComponent(g);
    }
}