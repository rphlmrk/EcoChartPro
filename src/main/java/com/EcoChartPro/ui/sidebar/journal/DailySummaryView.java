package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.journal.JournalAnalysisService;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DailySummaryView extends JPanel {

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final JLabel dateLabel;
    private final JLabel pnlValueLabel, tradesValueLabel, winsValueLabel, lossesValueLabel;
    private final JLabel noTradesLabel;
    private final JLabel pnlTitleLabel, tradesTitleLabel, winsTitleLabel, lossesTitleLabel;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");

    public DailySummaryView() {
        super(new BorderLayout(0, 15));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        setPreferredSize(new Dimension(260, 120));

        dateLabel = new JLabel("No Session Loaded", SwingConstants.CENTER);
        add(dateLabel, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setOpaque(false);

        // --- Stats View ---
        JPanel statsGridPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        statsGridPanel.setOpaque(false);

        pnlValueLabel = new JLabel();
        pnlTitleLabel = new JLabel("P&L");
        statsGridPanel.add(createSingleStatCard(pnlTitleLabel, pnlValueLabel));

        tradesValueLabel = new JLabel();
        tradesTitleLabel = new JLabel("Trades");
        statsGridPanel.add(createSingleStatCard(tradesTitleLabel, tradesValueLabel));

        winsValueLabel = new JLabel();
        lossesValueLabel = new JLabel();
        winsTitleLabel = new JLabel("Wins");
        lossesTitleLabel = new JLabel("Losses");
        statsGridPanel.add(createPairedStatCard(winsTitleLabel, winsValueLabel, lossesTitleLabel, lossesValueLabel));

        JPanel gridWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        gridWrapper.setOpaque(false);
        gridWrapper.add(statsGridPanel);

        // --- No Trades View ---
        JPanel noTradesPanel = new JPanel(new GridBagLayout());
        noTradesPanel.setOpaque(false);
        noTradesPanel.setPreferredSize(new Dimension(75, 55));
        noTradesLabel = new JLabel("No trades on this day.");
        noTradesPanel.add(noTradesLabel);

        contentPanel.add(gridWrapper, "STATS");
        contentPanel.add(noTradesPanel, "NO_TRADES");

        add(contentPanel, BorderLayout.CENTER);
        updateView(null, LocalDate.now());
        updateUI(); // Apply initial theme
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (dateLabel != null) { // Check for null during construction
            Font widgetTitleFont = UIManager.getFont("app.font.widget_title");
            Font widgetContentFont = UIManager.getFont("app.font.widget_content");
            Color textNormal = UIManager.getColor("Label.foreground");
            Color textMuted = UIManager.getColor("Label.disabledForeground");

            dateLabel.setFont(widgetTitleFont.deriveFont(Font.BOLD, 18f));
            dateLabel.setForeground(textNormal);

            pnlTitleLabel.setFont(widgetContentFont.deriveFont(13f));
            pnlTitleLabel.setForeground(textMuted);
            pnlValueLabel.setFont(widgetTitleFont);

            tradesTitleLabel.setFont(widgetContentFont.deriveFont(13f));
            tradesTitleLabel.setForeground(textMuted);
            tradesValueLabel.setFont(widgetTitleFont);
            
            winsTitleLabel.setFont(widgetContentFont.deriveFont(13f));
            winsTitleLabel.setForeground(textMuted);
            winsValueLabel.setFont(widgetContentFont.deriveFont(Font.BOLD));
            winsValueLabel.setForeground(textNormal);

            lossesTitleLabel.setFont(widgetContentFont.deriveFont(13f));
            lossesTitleLabel.setForeground(textMuted);
            lossesValueLabel.setFont(widgetContentFont.deriveFont(Font.BOLD));
            lossesValueLabel.setForeground(textNormal);
            
            noTradesLabel.setForeground(textMuted);
        }
    }

    public void updateView(JournalAnalysisService.DailyStats stats, LocalDate date) {
        dateLabel.setText(date.format(DATE_FORMATTER));

        if (stats == null) {
            cardLayout.show(contentPanel, "NO_TRADES");
        } else {
            pnlValueLabel.setText(PNL_FORMAT.format(stats.totalPnl()));
            pnlValueLabel.setForeground(stats.totalPnl().compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));

            tradesValueLabel.setText(String.valueOf(stats.tradeCount()));
            tradesValueLabel.setForeground(UIManager.getColor("Label.foreground"));

            long winCount = Math.round(stats.tradeCount() * stats.winRatio());
            long lossCount = stats.tradeCount() - winCount;

            winsValueLabel.setText(String.valueOf(winCount));
            lossesValueLabel.setText(String.valueOf(lossCount));

            cardLayout.show(contentPanel, "STATS");
        }
        revalidate();
        repaint();
    }
    
    private JPanel createSingleStatCard(JLabel titleLabel, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        card.setPreferredSize(new Dimension(75, 55));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createPairedStatCard(JLabel title1, JLabel valueLabel1, JLabel title2, JLabel valueLabel2) {
        JPanel card = new JPanel(new GridLayout(2, 1));
        card.setOpaque(true);
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        card.setPreferredSize(new Dimension(75, 55));
        card.add(createPairedRow(title1, valueLabel1));
        card.add(createPairedRow(title2, valueLabel2));
        return card;
    }
    
    private JPanel createPairedRow(JLabel titleLabel, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(titleLabel, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }
}