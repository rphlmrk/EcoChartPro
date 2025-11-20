package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.gamification.ProgressCardViewModel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class ProgressCardPanel extends JPanel {

    private final JLabel titleLabel;
    private final JLabel primaryValueLabel;
    private final JLabel secondaryValueLabel;
    private final JLabel goalTextLabel;
    private final JTextArea motivationalMessageArea;
    private final JProgressBar consistencyMeter;
    private final JButton viewInsightsButton;

    public ProgressCardPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        setLayout(new GridBagLayout());

        // --- Initialize Components ---
        titleLabel = new JLabel("Status");
        primaryValueLabel = new JLabel("-");
        secondaryValueLabel = new JLabel("-");
        goalTextLabel = new JLabel("Goal: -");

        motivationalMessageArea = new JTextArea("Keep up the great work.");
        motivationalMessageArea.setOpaque(false);
        motivationalMessageArea.setEditable(false);
        motivationalMessageArea.setLineWrap(true);
        motivationalMessageArea.setWrapStyleWord(true);
        motivationalMessageArea.setFocusable(false);

        consistencyMeter = new JProgressBar(0, 100);

        viewInsightsButton = new JButton("View Insights");
        viewInsightsButton.setIcon(UITheme.getIcon(UITheme.Icons.REPORT, 14, 14));

        // --- Layout using GridBagLayout ---
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0: Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        add(titleLabel, gbc);

        // Row 1: Primary and Secondary Values
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.7;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(8, 4, 8, 4);
        add(primaryValueLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.3;
        gbc.anchor = GridBagConstraints.EAST;
        add(secondaryValueLabel, gbc);

        // Row 2: Consistency Meter
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 4, 0, 4);
        add(consistencyMeter, gbc);

        // Row 3: Goal Text
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 4, 8, 4);
        add(goalTextLabel, gbc);

        // Row 4: Motivational Message and Button Panel
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        JPanel bottomContentPanel = new JPanel(new BorderLayout(0, 5));
        bottomContentPanel.setOpaque(false);

        JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonWrapper.setOpaque(false);
        buttonWrapper.add(viewInsightsButton);

        bottomContentPanel.add(motivationalMessageArea, BorderLayout.CENTER);
        bottomContentPanel.add(buttonWrapper, BorderLayout.SOUTH);

        add(bottomContentPanel, gbc);

        updateUI();
    }

    public void addInsightsButtonListener(ActionListener listener) {
        viewInsightsButton.addActionListener(listener);
    }

    private String toHex(Color c) {
        if (c == null)
            return "000000";
        return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public void updateViewModel(ProgressCardViewModel model) {
        if (model == null)
            return;

        // --- Reset state before applying new model ---
        primaryValueLabel.setFont(UIManager.getFont("app.font.value_large"));
        primaryValueLabel.setText(model.primaryValue());
        secondaryValueLabel.setVisible(true);
        GridBagConstraints gbc = ((GridBagLayout) getLayout()).getConstraints(primaryValueLabel);
        if (gbc.gridwidth != 1) {
            gbc.gridwidth = 1;
            ((GridBagLayout) getLayout()).setConstraints(primaryValueLabel, gbc);
        }

        titleLabel.setText(model.title());
        secondaryValueLabel.setText(model.secondaryValue());
        goalTextLabel.setText(model.goalText());
        motivationalMessageArea.setText(model.motivationalMessage());

        int progressValue = (int) (model.progress() * 100);
        consistencyMeter.setValue(progressValue);
        viewInsightsButton.setVisible(true); // Default to visible

        switch (model.cardType()) {
            case POSITIVE_STREAK:
                primaryValueLabel.setForeground(UIManager.getColor("app.color.accent"));
                secondaryValueLabel.setForeground(UIManager.getColor("app.color.positive"));
                consistencyMeter.setForeground(UIManager.getColor("app.color.accent"));
                consistencyMeter.setVisible(true);
                goalTextLabel.setVisible(true);
                // [FIX] Show button for streaks so users can view achievements
                viewInsightsButton.setVisible(true);
                viewInsightsButton.setText("View Goals");
                break;

            case CRITICAL_MISTAKE:
                primaryValueLabel.setForeground(UIManager.getColor("app.color.negative"));
                secondaryValueLabel.setForeground(UIManager.getColor("app.color.negative"));
                consistencyMeter.setForeground(UIManager.getColor("app.color.negative"));
                consistencyMeter.setVisible(false);
                goalTextLabel.setVisible(false);
                viewInsightsButton.setText("View Insights");
                break;

            case NEXT_ACHIEVEMENT:
                primaryValueLabel.setForeground(UIManager.getColor("Label.foreground"));
                secondaryValueLabel.setVisible(false);
                consistencyMeter.setVisible(false);
                goalTextLabel.setVisible(false);

                primaryValueLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(Font.BOLD));
                String subtitleText = model.secondaryValue();
                String subtitleColorHex = toHex(UIManager.getColor("Label.disabledForeground"));
                primaryValueLabel.setText("<html><body style='width: 160px;'>"
                        + model.primaryValue() + "<br>"
                        + "<font size='-1' color='#" + subtitleColorHex + "'>" + subtitleText + "</font>"
                        + "</body></html>");

                gbc = ((GridBagLayout) getLayout()).getConstraints(primaryValueLabel);
                gbc.gridwidth = 2;
                ((GridBagLayout) getLayout()).setConstraints(primaryValueLabel, gbc);

                viewInsightsButton.setText("View Goals");
                break;

            case EMPTY:
            default:
                primaryValueLabel.setForeground(UIManager.getColor("Label.foreground"));
                secondaryValueLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                consistencyMeter.setVisible(false);
                goalTextLabel.setVisible(false);
                viewInsightsButton.setVisible(false);
                break;
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
            titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            primaryValueLabel.setFont(UIManager.getFont("app.font.value_large"));

            secondaryValueLabel.setFont(UIManager.getFont("app.font.widget_title"));

            goalTextLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(11f));
            goalTextLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            motivationalMessageArea.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.ITALIC));
            motivationalMessageArea.setForeground(UIManager.getColor("Label.foreground"));

            viewInsightsButton.setForeground(UIManager.getColor("Button.foreground"));

            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        g2d.dispose();
        super.paintComponent(g);
    }
}