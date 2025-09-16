package com.EcoChartPro.ui.components;

import com.EcoChartPro.ui.dashboard.theme.UITheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom overlay widget to celebrate a winning streak during a replay session.
 */
public class OnFireStreakWidget extends JPanel {

    private final JLabel iconLabel;
    private final JLabel textLabel;

    public OnFireStreakWidget() {
        setOpaque(false);
        setVisible(false);
        // Switched to GridBagLayout for more control over component placement
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        Color accentColor = javax.swing.UIManager.getColor("app.color.accent");
        if (accentColor == null) {
            accentColor = new Color(0, 191, 165); // Fallback
        }
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        
        iconLabel = new JLabel(UITheme.getIcon(UITheme.Icons.TROPHY, 24, 24, accentColor));
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 10);
        add(iconLabel, gbc);

        textLabel = new JLabel("3 Wins in a Row!");
        textLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_title"));
        textLabel.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        add(textLabel, gbc);

        // --- Add Close Button ---
        JButton closeButton = new JButton("×");
        styleCloseButton(closeButton);
        closeButton.addActionListener(e -> hideStreak());
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 10, 0, -5); // Position it neatly
        add(closeButton, gbc);
    }

    private void styleCloseButton(JButton button) {
        button.setOpaque(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 22));
        button.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                button.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
            }
            @Override public void mouseExited(MouseEvent e) {
                button.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
            }
        });
    }

    /**
     * Displays the widget with the current winning streak count.
     * @param count The number of consecutive wins.
     */
    public void showStreak(int count) {
        textLabel.setText(count + " Wins in a Row! You're on fire!");
        if (!isVisible()) {
            setVisible(true);
        }
        // Force the layout to be recalculated based on the new text length
        revalidate();
    }

    /**
     * Hides the widget.
     */
    public void hideStreak() {
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
            Color borderColor = javax.swing.UIManager.getColor("app.color.accent");
            
            g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 230));
            g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 30, 30));
            
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 28, 28));
            
            g2d.dispose();
        }
    }
}