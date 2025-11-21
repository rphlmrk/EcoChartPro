package com.EcoChartPro.ui.home.widgets;

import com.EcoChartPro.core.gamification.ProgressCardViewModel;
import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

public class CoachingCardPanel extends JPanel {

    private final JLabel titleLabel;
    private final JTextArea descriptionArea;
    private final JLabel rewardLabel;
    private final JButton viewInsightsButton;
    private Color accentColor = UIManager.getColor("app.color.neutral");

    private static final Icon REVIEW_ICON = UITheme.getIcon(UITheme.Icons.INFO, 16, 16);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel;
    private final JPanel loadingPanel;

    public CoachingCardPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JPanel cardWrapper = new JPanel(cardLayout);
        cardWrapper.setOpaque(false);

        // --- Create Loading Panel ---
        loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setOpaque(false);
        loadingPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        JLabel loadingLabel = new JLabel("Analyzing Performance...");
        loadingLabel.setFont(UIManager.getFont("app.font.widget_title"));
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);

        // --- Create Content Panel ---
        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

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

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        contentPanel.add(titleLabel, gbc);

        // Wrap the JTextArea in a configured JScrollPane
        JScrollPane textScroller = new JScrollPane(descriptionArea);
        textScroller.setOpaque(false);
        textScroller.getViewport().setOpaque(false);
        textScroller.setBorder(null);
        textScroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        textScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // [FIX] Forward mouse wheel events to the parent ScrollPane (the Dashboard
        // scroller)
        textScroller.addMouseWheelListener(e -> {
            JScrollPane parentScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class,
                    CoachingCardPanel.this);
            if (parentScroll != null) {
                // Create a new event relative to the parent and dispatch it
                parentScroll.dispatchEvent(SwingUtilities.convertMouseEvent(textScroller, e, parentScroll));
            }
        });

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 4, 8, 4);
        contentPanel.add(textScroller, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.insets = new Insets(4, 4, 0, 4);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(rewardLabel, BorderLayout.WEST);
        bottomPanel.add(viewInsightsButton, BorderLayout.EAST);

        contentPanel.add(bottomPanel, gbc);

        cardWrapper.add(contentPanel, "content");
        cardWrapper.add(loadingPanel, "loading");

        add(cardWrapper, BorderLayout.CENTER);

        updateUI();
    }

    public void setLoading(boolean isLoading) {
        cardLayout.show((JPanel) getComponent(0), isLoading ? "loading" : "content");
        repaint();
    }

    public void addInsightsButtonListener(ActionListener listener) {
        viewInsightsButton.addActionListener(listener);
    }

    public void setReviewDue(boolean isDue) {
        if (isDue) {
            viewInsightsButton.setText("Review Performance");
            viewInsightsButton.setIcon(REVIEW_ICON);
            viewInsightsButton.setToolTipText(
                    "You've completed a new month of trading! Click to review your performance trends.");
        } else {
            viewInsightsButton.setText("View All Insights");
            viewInsightsButton.setIcon(UITheme.getIcon(UITheme.Icons.REPORT, 14, 14));
            viewInsightsButton.setToolTipText(null);
        }
    }

    public void updateViewModel(ProgressCardViewModel model) {
        if (model == null) {
            setLoading(true);
            return;
        }

        titleLabel.setText(model.title());
        descriptionArea.setText(model.motivationalMessage());
        rewardLabel.setText(model.secondaryValue());

        boolean isEmptyState = model.cardType() == ProgressCardViewModel.CardType.EMPTY;
        viewInsightsButton.setVisible(!isEmptyState);
        viewInsightsButton.setEnabled(!isEmptyState);

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
                descriptionArea.setText(
                        "Your performance is stable. Keep up the great work and a new challenge or insight will appear when needed.");
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
            if (loadingPanel != null && loadingPanel.getComponentCount() > 0) {
                JLabel loadingLabel = (JLabel) loadingPanel.getComponent(0);
                loadingLabel.setFont(UIManager.getFont("app.font.widget_title"));
                loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(UIManager.getColor("Panel.background"));
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

        if (loadingPanel.isVisible()) {
            accentColor = UIManager.getColor("Component.borderColor");
        }

        g2d.setColor(accentColor);
        g2d.setStroke(new BasicStroke(2f));
        g2d.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 15, 15));

        g2d.dispose();
        super.paintComponent(g);
    }
}