package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService.WeeklyStats;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;

public class WeeklySummaryPanel extends JPanel {
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0%");
    private final JLabel tradeCountLabel, pnlLabel, planRateLabel, winRateLabel;
    private final CardLayout cardLayout;
    private final JPanel cardWrapper;

    public WeeklySummaryPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cardWrapper = new JPanel(cardLayout);
        cardWrapper.setOpaque(false);
        JPanel placeholderWrapper = new JPanel(new GridBagLayout());
        placeholderWrapper.setOpaque(false);
        JLabel placeholderLabel = new JLabel("No trades this week", SwingConstants.CENTER);
        placeholderLabel.setFont(UIManager.getFont("app.font.widget_content"));
        placeholderLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        placeholderWrapper.add(placeholderLabel);
        StatCard placeholderCard = new StatCard();
        placeholderCard.add(placeholderWrapper, BorderLayout.CENTER);
        cardWrapper.add(placeholderCard, "placeholder");
        
        // Use a single panel for all data
        JPanel dataPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        dataPanel.setOpaque(false);
        
        StatCard statsCard = new StatCard();
        statsCard.setLayout(new BoxLayout(statsCard, BoxLayout.Y_AXIS));
        tradeCountLabel = createStatLabel(Font.PLAIN);
        pnlLabel = createStatLabel(Font.PLAIN);
        planRateLabel = createStatLabel(Font.PLAIN);
        winRateLabel = createStatLabel(Font.PLAIN); // New label for win rate
        
        statsCard.add(tradeCountLabel);
        statsCard.add(pnlLabel);
        statsCard.add(planRateLabel);
        statsCard.add(winRateLabel); // Add it to the list
        
        dataPanel.add(statsCard);
        
        cardWrapper.add(dataPanel, "data");
        cardWrapper.setPreferredSize(new Dimension(cardWrapper.getPreferredSize().width, 80));
        add(cardWrapper, BorderLayout.CENTER);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (tradeCountLabel != null) { // Guard against calls during construction
            tradeCountLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.PLAIN, 13f));
            tradeCountLabel.setForeground(UIManager.getColor("Label.foreground"));
            pnlLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.PLAIN, 13f));
            pnlLabel.setForeground(UIManager.getColor("Label.foreground"));
            planRateLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.PLAIN, 13f));
            planRateLabel.setForeground(UIManager.getColor("Label.foreground"));
            winRateLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.PLAIN, 13f));
            winRateLabel.setForeground(UIManager.getColor("Label.foreground"));
            repaint();
        }
    }

    private JLabel createStatLabel(int style) {
        JLabel label = new JLabel();
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(style, 13f));
        label.setForeground(UIManager.getColor("Label.foreground"));
        return label;
    }

    public void updateData(WeeklyStats stats, DayCellPanel.ViewMode viewMode) {
        tradeCountLabel.setText(stats.tradeCount() + " trade" + (stats.tradeCount() == 1 ? "" : "s"));
        pnlLabel.setText("P&L: " + PNL_FORMAT.format(stats.totalPnl()));
        planRateLabel.setText("Plan Adherence: " + PERCENT_FORMAT.format(stats.planFollowedPercentage()));
        winRateLabel.setText("Win Rate: " + PERCENT_FORMAT.format(stats.winRatio()));
        cardLayout.show(cardWrapper, "data");
    }

    public void clearData() { cardLayout.show(cardWrapper, "placeholder"); }

    private static class StatCard extends JPanel {
        StatCard() {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(UIManager.getColor("Panel.background"));
            g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
            g2d.dispose();
            super.paintComponent(g);
        }
    }
}