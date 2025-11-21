package com.EcoChartPro.ui.components;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.ui.home.theme.UITheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom overlay widget to nudge the user to take a break after a losing streak.
 */
public class StopTradingNudgeWidget extends JPanel {

    private final JLabel titleLabel;
    private final JButton fastForwardButton;
    private final JButton closeButton;

    public StopTradingNudgeWidget() {
        setOpaque(false);
        setVisible(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        
        GridBagConstraints gbc = new GridBagConstraints();

        // Icon
        JLabel iconLabel = new JLabel(UITheme.getIcon(UITheme.Icons.ERROR_CIRCLE, 24, 24, javax.swing.UIManager.getColor("app.color.negative")));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.insets = new Insets(0, 0, 0, 15);
        add(iconLabel, gbc);

        // Title
        titleLabel = new JLabel("3 Losses in a Row");
        titleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_title"));
        titleLabel.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 0);
        add(titleLabel, gbc);
        
        // Message
        JTextArea messageArea = new JTextArea("This may be a good time to take a break and avoid revenge trading.");
        messageArea.setOpaque(false);
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(javax.swing.UIManager.getFont("app.font.widget_content"));
        messageArea.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        gbc.gridy = 1;
        add(messageArea, gbc);

        // Fast Forward Button
        fastForwardButton = new JButton("Fast Forward to Next Day");
        styleButton(fastForwardButton);
        fastForwardButton.addActionListener(e -> ReplaySessionManager.getInstance().jumpToNextDay());
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 15, 0, 0);
        add(fastForwardButton, gbc);

        // Close Button
        closeButton = new JButton("X");
        styleCloseButton(closeButton);
        closeButton.addActionListener(e -> hideNudge());
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.anchor = GridBagConstraints.NORTHEAST;
        gbc.insets = new Insets(-10, 5, 0, -10); // Negative insets to push it to the very edge
        add(closeButton, gbc);
    }
    
    private void styleButton(JButton button) {
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.focusedBorderColor")),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                 button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(javax.swing.UIManager.getColor("Component.borderColor")),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }
        });
    }

    private void styleCloseButton(JButton button) {
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(javax.swing.UIManager.getColor("app.color.negative"));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
            }
        });
    }

    /**
     * Displays the nudge with the current losing streak count.
     * @param count The number of consecutive losses.
     */
    public void showNudge(int count) {
        titleLabel.setText(count + " Losses in a Row");
        if (!isVisible()) {
            setVisible(true);
        }
    }

    /**
     * Hides the nudge widget.
     */
    public void hideNudge() {
        if (isVisible()) {
            setVisible(false);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (isVisible()) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            Color bgColor = javax.swing.UIManager.getColor("Panel.background");
            g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 240));
            g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
            
            g2d.setColor(javax.swing.UIManager.getColor("Component.borderColor"));
            g2d.setStroke(new BasicStroke(1f));
            g2d.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 15, 15));
            
            g2d.dispose();
        }
    }
}