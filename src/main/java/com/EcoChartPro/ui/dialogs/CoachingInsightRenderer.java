package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A custom panel to display a single coaching insight with severity-based styling.
 * This component is designed to be added to a container with a BoxLayout.
 * Note: The class name is kept for compatibility, but it functions as a "Card".
 */
public class CoachingInsightRenderer extends JPanel {

    private final Color accentColor;

    public CoachingInsightRenderer(CoachingInsight insight) {
        // Use GridBagLayout for precise alignment control
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();

        // --- Icon Label ---
        JLabel iconLabel = new JLabel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0; // Icon column does not stretch horizontally
        gbc.anchor = GridBagConstraints.NORTH; // Align icon to the top of its cell
        gbc.insets = new Insets(0, 0, 0, 15); // Padding to the right of the icon
        add(iconLabel, gbc);

        // --- Text Panel ---
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(insight.title());
        titleLabel.setFont(UIManager.getFont("app.font.widget_title"));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea descriptionArea = new JTextArea(insight.description());
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setEditable(false);
        descriptionArea.setFocusable(false);
        descriptionArea.setFont(UIManager.getFont("app.font.widget_content"));
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descriptionArea);

        // Add the text panel to the main layout
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0; // Allow text panel to take all remaining horizontal space
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST; // Align text panel to the top-left
        gbc.insets = new Insets(0, 0, 0, 0); // Reset insets for this component
        add(textPanel, gbc);

        // --- Populate data and set styling ---
        String iconPath;
        switch (insight.severity()) {
            case HIGH:
                accentColor = UIManager.getColor("app.color.negative");
                iconPath = UITheme.Icons.ERROR_CIRCLE;
                break;
            case MEDIUM:
                accentColor = UIManager.getColor("app.color.neutral");
                iconPath = UITheme.Icons.INFO;
                break;
            case LOW:
            default:
                accentColor = UIManager.getColor("app.color.positive");
                iconPath = UITheme.Icons.CHECKMARK;
                break;
        }
        iconLabel.setIcon(UITheme.getIcon(iconPath, 32, 32, accentColor));

        titleLabel.setForeground(UIManager.getColor("Label.foreground"));
        descriptionArea.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));

        g2d.setColor(accentColor);
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 15, 15));

        g2d.dispose();
        super.paintComponent(g);
    }
}