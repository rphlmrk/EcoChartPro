package com.EcoChartPro.ui.components;

import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom overlay widget to display the internet connection status during a session.
 */
public class ConnectionStatusWidget extends JPanel {

    private final JLabel iconLabel;
    private final JLabel textLabel;
    private Color borderColor;

    public ConnectionStatusWidget() {
        setOpaque(false);
        setVisible(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        this.borderColor = UIManager.getColor("Component.borderColor"); // Default border

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // Icon will be set dynamically
        iconLabel = new JLabel();
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 0, 10);
        add(iconLabel, gbc);

        textLabel = new JLabel("Connection Lost");
        textLabel.setFont(UIManager.getFont("app.font.widget_content"));
        textLabel.setForeground(UIManager.getColor("Label.foreground"));
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        add(textLabel, gbc);
    }

    /**
     * Displays the widget with a specific status message and icon.
     * @param message The message to display.
     * @param iconPath The path to the icon resource.
     * @param statusColor The color for the icon and border, indicating severity.
     */
    public void showStatus(String message, String iconPath, Color statusColor) {
        this.textLabel.setText(message);
        this.iconLabel.setIcon(UITheme.getIcon(iconPath, 20, 20, statusColor));
        this.borderColor = statusColor;
        if (!isVisible()) {
            setVisible(true);
        }
        revalidate();
        repaint();
    }

    /**
     * Hides the widget.
     */
    public void hideStatus() {
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

            Color bgColor = UIManager.getColor("Panel.background");

            g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 230));
            g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 30, 30));

            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 28, 28));

            g2d.dispose();
        }
    }
}