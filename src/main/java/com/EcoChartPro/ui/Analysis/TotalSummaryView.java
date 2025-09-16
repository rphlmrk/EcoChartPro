package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class TotalSummaryView extends JPanel {

    private final JLabel pnlValueLabel;
    private final JLabel tradesValueLabel;
    private final JLabel winRateValueLabel;

    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0'%'");

    public TotalSummaryView() {
        super(new GridLayout(1, 3, 10, 0));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(10, 0, 10, 0)
        ));
        setPreferredSize(new Dimension(0, 70));

        pnlValueLabel = createValueLabel();
        tradesValueLabel = createValueLabel();
        winRateValueLabel = createValueLabel();

        add(createStatCard("Overall P&L", pnlValueLabel));
        add(createStatCard("Total Trades", tradesValueLabel));
        add(createStatCard("Win Rate", winRateValueLabel));

        updateStats(null); // Initialize to empty/default state
    }

    public void updateStats(JournalAnalysisService.OverallStats stats) {
        if (stats == null) {
            pnlValueLabel.setText(PNL_FORMAT.format(BigDecimal.ZERO));
            pnlValueLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            tradesValueLabel.setText("0");
            tradesValueLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            winRateValueLabel.setText(PERCENT_FORMAT.format(0));
            winRateValueLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            return;
        }

        pnlValueLabel.setText(PNL_FORMAT.format(stats.totalPnl()));
        pnlValueLabel.setForeground(stats.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));

        tradesValueLabel.setText(String.valueOf(stats.totalTrades()));
        tradesValueLabel.setForeground(UIManager.getColor("Label.foreground"));

        winRateValueLabel.setText(PERCENT_FORMAT.format(stats.winRate() * 100));
        winRateValueLabel.setForeground(UIManager.getColor("Label.foreground"));
    }
    
    private JLabel createValueLabel() {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setFont(UIManager.getFont("app.font.widget_title").deriveFont(15f));
        return label;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(75, 50));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }
}