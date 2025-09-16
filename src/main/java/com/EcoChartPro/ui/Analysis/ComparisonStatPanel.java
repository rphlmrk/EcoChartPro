package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

public class ComparisonStatPanel extends JPanel {
    private final JLabel tradesA, tradesB;
    private final JLabel winRateA, winRateB;
    private final JLabel profitFactorA, profitFactorB;
    private final JLabel expectancyA, expectancyB;

    public ComparisonStatPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createTitledBorder("Statistical Comparison"));
        setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Headers
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;
        add(createHeaderLabel("Set A"), gbc);
        gbc.gridx = 2;
        add(createHeaderLabel("Set B"), gbc);
        gbc.anchor = GridBagConstraints.WEST;

        // Total Trades
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2;
        add(createStatLabel("Total Trades:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.4; tradesA = createValueLabel(); add(tradesA, gbc);
        gbc.gridx = 2; gbc.weightx = 0.4; tradesB = createValueLabel(); add(tradesB, gbc);

        // Win Rate
        gbc.gridy++;
        gbc.gridx = 0; add(createStatLabel("Win Rate:"), gbc);
        gbc.gridx = 1; winRateA = createValueLabel(); add(winRateA, gbc);
        gbc.gridx = 2; winRateB = createValueLabel(); add(winRateB, gbc);

        // Profit Factor
        gbc.gridy++;
        gbc.gridx = 0; add(createStatLabel("Profit Factor:"), gbc);
        gbc.gridx = 1; profitFactorA = createValueLabel(); add(profitFactorA, gbc);
        gbc.gridx = 2; profitFactorB = createValueLabel(); add(profitFactorB, gbc);
        
        // Expectancy
        gbc.gridy++;
        gbc.gridx = 0; add(createStatLabel("Expectancy / Trade:"), gbc);
        gbc.gridx = 1; expectancyA = createValueLabel(); add(expectancyA, gbc);
        gbc.gridx = 2; expectancyB = createValueLabel(); add(expectancyB, gbc);
    }

    public void updateStats(JournalAnalysisService.OverallStats statsA, JournalAnalysisService.OverallStats statsB) {
        DecimalFormat percentFormat = new DecimalFormat("0.0'%'");
        DecimalFormat decimalFormat = new DecimalFormat("0.00");
        DecimalFormat pnlFormat = new DecimalFormat("+$0.00;-$0.00");

        updateRow(tradesA, String.valueOf(statsA.totalTrades()), tradesB, String.valueOf(statsB.totalTrades()));
        updateRow(winRateA, percentFormat.format(statsA.winRate() * 100), winRateB, percentFormat.format(statsB.winRate() * 100));
        updateRow(profitFactorA, decimalFormat.format(statsA.profitFactor()), profitFactorB, decimalFormat.format(statsB.profitFactor()));
        updateRow(expectancyA, pnlFormat.format(statsA.expectancy()), expectancyB, pnlFormat.format(statsB.expectancy()));
    }

    private void updateRow(JLabel labelA, String valueA, JLabel labelB, String valueB) {
        labelA.setText(valueA);
        labelB.setText(valueB);
    }
    
    private JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIManager.getFont("app.font.widget_title"));
        return label;
    }
    
    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }
    
    private JLabel createValueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        return label;
    }
}