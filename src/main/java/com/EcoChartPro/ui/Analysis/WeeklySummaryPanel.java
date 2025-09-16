package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService.WeeklyStats;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;

public class WeeklySummaryPanel extends JPanel {
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0%");
    private final JLabel tradeCountLabel, pnlLabel, planRateLabel;
    private final JLabel winRateValueLabel;
    private final WinRateBar winRateBar;
    private final CardLayout cardLayout;
    private final JPanel cardWrapper;
    private final StatCard winRateCard;

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
        JPanel dataPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        dataPanel.setOpaque(false);
        StatCard pnlPlanCard = new StatCard();
        pnlPlanCard.setLayout(new BoxLayout(pnlPlanCard, BoxLayout.Y_AXIS));
        tradeCountLabel = createStatLabel(Font.PLAIN);
        pnlLabel = createStatLabel(Font.PLAIN);
        planRateLabel = createStatLabel(Font.PLAIN);
        pnlPlanCard.add(tradeCountLabel);
        pnlPlanCard.add(pnlLabel);
        pnlPlanCard.add(planRateLabel);
        dataPanel.add(pnlPlanCard);

        winRateCard = new StatCard();
        winRateCard.setLayout(new BorderLayout(0, 2));
        JLabel winRateTitleLabel = new JLabel("Win Rate");
        winRateTitleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        winRateTitleLabel.setForeground(UIManager.getColor("Label.foreground"));
        winRateValueLabel = new JLabel();
        winRateValueLabel.setFont(UIManager.getFont("app.font.widget_content"));
        winRateValueLabel.setForeground(UIManager.getColor("Label.foreground"));
        winRateBar = new WinRateBar();
        JPanel winRateHeader = new JPanel(new BorderLayout());
        winRateHeader.setOpaque(false);
        winRateHeader.add(winRateTitleLabel, BorderLayout.WEST);
        winRateHeader.add(winRateValueLabel, BorderLayout.EAST);
        winRateCard.add(winRateHeader, BorderLayout.NORTH);
        winRateCard.add(winRateBar, BorderLayout.CENTER);
        dataPanel.add(winRateCard);
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
            
            if (winRateCard != null && winRateCard.getComponentCount() > 0) {
                JPanel winRateHeader = (JPanel) winRateCard.getComponent(0);
                JLabel title = (JLabel) winRateHeader.getComponent(0);
                title.setFont(UIManager.getFont("app.font.widget_content"));
                title.setForeground(UIManager.getColor("Label.foreground"));
                
                winRateValueLabel.setFont(UIManager.getFont("app.font.widget_content"));
                winRateValueLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
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
        pnlLabel.setText(PNL_FORMAT.format(stats.totalPnl()));
        planRateLabel.setText(PERCENT_FORMAT.format(stats.planFollowedPercentage()) + " plan");
        winRateValueLabel.setText(PERCENT_FORMAT.format(stats.winRatio()));
        winRateBar.setProgress(stats.winRatio());
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