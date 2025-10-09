package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService.MonthlyStats;
import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

public class WeeklySummaryPanel extends JPanel {
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0%");
    private final JLabel tradeCountLabel, pnlLabel, planRateLabel, winRateLabel;

    public WeeklySummaryPanel() {
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 25, 0));
        setBorder(BorderFactory.createEmptyBorder(15, 5, 0, 0));

        tradeCountLabel = createStatLabel();
        pnlLabel = createStatLabel();
        planRateLabel = createStatLabel();
        winRateLabel = createStatLabel();

        add(tradeCountLabel);
        add(pnlLabel);
        add(planRateLabel);
        add(winRateLabel);
    }

    private JLabel createStatLabel() {
        JLabel label = new JLabel();
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(13f));
        label.setForeground(UIManager.getColor("Label.foreground"));
        return label;
    }

    public void updateData(MonthlyStats stats) {
        if (stats == null) {
            clearData();
            return;
        }
        tradeCountLabel.setText(stats.tradeCount() + " trades");
        pnlLabel.setText("P&L: " + PNL_FORMAT.format(stats.totalPnl()));
        planRateLabel.setText("Plan Adherence: " + PERCENT_FORMAT.format(stats.planFollowedPercentage()));
        winRateLabel.setText("Win Rate: " + PERCENT_FORMAT.format(stats.winRatio()));
        setVisible(true);
    }

    public void clearData() {
        setVisible(false);
    }
}