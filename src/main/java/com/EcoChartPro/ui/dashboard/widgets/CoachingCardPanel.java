package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.gamification.ProgressCardViewModel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

/**
 * A dedicated UI component for displaying coaching insights and daily challenges.
 * It is visually distinct from the ProgressCardPanel and focuses on textual information.
 */
public class CoachingCardPanel extends JPanel {

    private final JLabel titleLabel;
    private final JTextArea descriptionArea;
    private final JLabel rewardLabel;
    private final JButton viewInsightsButton;
    private Color accentColor = UIManager.getColor("app.color.neutral");
    
    private static final Icon REVIEW_ICON = UITheme.getIcon(UITheme.Icons.INFO, 16, 16);

    public CoachingCardPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        setLayout(new GridBagLayout());

        titleLabel = new JLabel("Daily Challenge");
        descriptionArea = new JTextArea("Challenge details will appear here.");
        rewardLabel = new JLabel("+50 XP");
        viewInsightsButton = new JButton("View Insights");
        viewInsightsButton.setIcon(UITheme.getIcon(UITheme.Icons.REPORT, 14, 14));
        
        descriptionArea.setOpaque(false);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFocusable(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // Row 0: Title (spans the full width)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        add(titleLabel, gbc);

        // Row 1: Description
        gbc.gridy = 1;
        gbc.weighty = 1.0; // Allow description to take up vertical space
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 4, 8, 4);
        add(descriptionArea, gbc);
        
        // Row 2: Bottom row containing the reward label and insights button
        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.SOUTH; // Pin this row to the bottom
        gbc.insets = new Insets(4, 4, 0, 4);
        
        // Create a sub-panel for the bottom elements to align them properly
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(rewardLabel, BorderLayout.WEST);
        bottomPanel.add(viewInsightsButton, BorderLayout.EAST);
        
        add(bottomPanel, gbc);

        updateUI();
    }
    
    public void addInsightsButtonListener(ActionListener listener) {
        viewInsightsButton.addActionListener(listener);
    }
    
    public void setReviewDue(boolean isDue) {
        if (isDue) {
            viewInsightsButton.setText("Review Performance");
            viewInsightsButton.setIcon(REVIEW_ICON);
            viewInsightsButton.setToolTipText("You've completed a new month of trading! Click to review your performance trends.");
        } else {
            viewInsightsButton.setText("View All Insights");
            viewInsightsButton.setIcon(UITheme.getIcon(UITheme.Icons.REPORT, 14, 14));
            viewInsightsButton.setToolTipText(null);
        }
    }

    public void updateViewModel(ProgressCardViewModel model) {
        if (model == null) {
            return;
        }

        // --- Update Content ---
        titleLabel.setText(model.title());
        descriptionArea.setText(model.motivationalMessage());
        rewardLabel.setText(model.secondaryValue());
        
        // Button is visible unless the card is empty.
        boolean isEmptyState = model.cardType() == ProgressCardViewModel.CardType.EMPTY;
        viewInsightsButton.setVisible(!isEmptyState);
        viewInsightsButton.setEnabled(!isEmptyState);

        // --- Update Visuals Based on Type ---
        switch (model.cardType()) {
            case DAILY_CHALLENGE:
                accentColor = UIManager.getColor("app.color.accent");
                rewardLabel.setVisible(true);
                break;
            case COACHING_INSIGHT:
                accentColor = UIManager.getColor("app.color.neutral");
                rewardLabel.setVisible(false);
                break;
            case CRITICAL_MISTAKE:
                accentColor = UIManager.getColor("app.color.negative");
                rewardLabel.setVisible(false);
                break;
            default: // EMPTY state
                titleLabel.setText("No Active Insight");
                descriptionArea.setText("Your performance is stable. Keep up the great work and a new challenge or insight will appear when needed.");
                accentColor = UIManager.getColor("Component.borderColor");
                rewardLabel.setVisible(false);
                break;
        }

        revalidate();
        repaint();
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setFont(UIManager.getFont("app.font.widget_title"));
            titleLabel.setForeground(UIManager.getColor("Label.foreground"));
            descriptionArea.setFont(UIManager.getFont("app.font.widget_content"));
            descriptionArea.setForeground(UIManager.getColor("Label.foreground"));
            rewardLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(Font.BOLD));
            rewardLabel.setForeground(UIManager.getColor("app.color.accent"));
            rewardLabel.setHorizontalAlignment(SwingConstants.LEFT);
            viewInsightsButton.setForeground(UIManager.getColor("Button.foreground"));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
        
        g2d.setColor(accentColor);
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 15, 15));
        
        g2d.dispose();
        super.paintComponent(g);
    }
}