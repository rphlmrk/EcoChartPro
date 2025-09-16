package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.model.Timeframe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A custom, floating dialog for changing the chart interval via keyboard input.
 * It provides real-time feedback on the entered value.
 */
public class TimeframeInputDialog extends JDialog {

    private final JTextField inputField;
    private final JLabel feedbackLabel;
    private static final Pattern TIMEFRAME_PATTERN = Pattern.compile("(\\d+)([mhd])", Pattern.CASE_INSENSITIVE);

    public TimeframeInputDialog(Frame owner) {
        super(owner, false);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0)); // Transparent background
        setFocusableWindowState(false);
        setType(Window.Type.UTILITY);

        // custom JPanel that overrides updateUI and handles custom painting.
        ThemeAwareContentPanel contentPanel = new ThemeAwareContentPanel();
        contentPanel.setLayout(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        setContentPane(contentPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);

        JLabel titleLabel = new JLabel("Change Timeframe");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        contentPanel.add(titleLabel, gbc);

        inputField = new JTextField(5);
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 24));
        inputField.setHorizontalAlignment(SwingConstants.CENTER);
        inputField.setOpaque(false);

        gbc.insets = new Insets(0, 0, 8, 0);
        contentPanel.add(inputField, gbc);
        
        feedbackLabel = new JLabel(" ");
        feedbackLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        feedbackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.insets = new Insets(0, 0, 0, 0);
        contentPanel.add(feedbackLabel, gbc);

        // This key listener is just for preventing user input in the dialog's text field itself.
        // The ChartPanel's listener will handle all the logic.
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                e.consume();
            }
        });
        
        // The updateUI call on the contentPanel will set the initial theme colors.
    }

    // Inner class to correctly handle UI updates and custom painting for the content area.
    private class ThemeAwareContentPanel extends JPanel {
        ThemeAwareContentPanel() {
            setOpaque(false);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (inputField != null) { // Guard against calls during construction
                setBackground(UIManager.getColor("app.dialog.input.background"));
                inputField.setForeground(UIManager.getColor("app.dialog.input.foreground"));
                inputField.setCaretColor(UIManager.getColor("app.dialog.input.foreground"));
                inputField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UIManager.getColor("app.dialog.input.border"), 2, true),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
                ));
                // Assuming the titleLabel is the first component added.
                ((JLabel) this.getComponent(0)).setForeground(UIManager.getColor("app.dialog.input.foreground"));
                feedbackLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!isOpaque()) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2d.dispose();
            }
            super.paintComponent(g);
        }
    }


    public void showDialog(Component parent, String initialText) {
        inputField.setText(initialText);
        updateFeedback(initialText);
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    public void updateInputText(String text) {
        inputField.setText(text);
        updateFeedback(text);
        pack(); // Repack to fit new content
    }

    private void updateFeedback(String text) {
        if (text == null || text.isEmpty()) {
            feedbackLabel.setText(" ");
            return;
        }

        Matcher matcher = TIMEFRAME_PATTERN.matcher(text);
        if (matcher.matches()) { // Case 1: The input is a complete, valid timeframe (e.g., "5m")
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            String unitFull = switch (unit) {
                case "m" -> (value == 1) ? "minute" : "minutes";
                case "h" -> (value == 1) ? "hour" : "hours";
                case "d" -> (value == 1) ? "day" : "days";
                default -> "";
            };
            feedbackLabel.setText(String.format("%d %s", value, unitFull));
        } else if (text.matches("\\d+")) { // Case 2: The input is just a number (e.g., "5")
            feedbackLabel.setText("Enter m, h, or d");
        } else { // Case 3: The input is invalid (e.g., "5x", "m5")
            feedbackLabel.setText("Invalid format");
        }
    }
}