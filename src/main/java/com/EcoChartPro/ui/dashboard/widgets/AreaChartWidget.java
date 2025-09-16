package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import java.util.Collections;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

public class AreaChartWidget extends JPanel {

    private final EquityCurveChart chart;
    private final JLabel maxDrawdownValue;
    private final JLabel maxRunupValue;
    private final JLabel titleLabel;

    // --- Dual State Fields ---
    private List<JournalAnalysisService.EquityPoint> overallEquityCurve = Collections.emptyList();
    private BigDecimal overallMaxDrawdown = BigDecimal.ZERO, overallMaxRunup = BigDecimal.ZERO;
    private List<JournalAnalysisService.EquityPoint> liveEquityCurve = Collections.emptyList();
    private boolean isShowingLive = false;

    public AreaChartWidget() {
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        titleLabel = new JLabel("Finished Trades PNL");
        titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        header.add(titleLabel, BorderLayout.WEST);
        JLabel icon = new JLabel(UITheme.getThemedIcon(UITheme.Icons.EXPAND, 16, 16));
        header.add(icon, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        chart = new EquityCurveChart();
        add(chart, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(1, 2, 20, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        maxDrawdownValue = new JLabel("-");
        maxRunupValue = new JLabel("-");
        footer.add(createFooterStat("Max Drawdown", maxDrawdownValue));
        footer.add(createFooterStat("Max Runup", maxRunupValue));
        add(footer, BorderLayout.SOUTH);
    }
    
    public void setOverallData(List<JournalAnalysisService.EquityPoint> equityCurve, BigDecimal maxDrawdown, BigDecimal maxRunup) {
        this.overallEquityCurve = equityCurve;
        this.overallMaxDrawdown = maxDrawdown;
        this.overallMaxRunup = maxRunup;
        if (!isShowingLive) {
            updateDisplay(false);
        }
    }
    
    public void setLiveData(List<JournalAnalysisService.EquityPoint> equityCurve) {
        this.liveEquityCurve = equityCurve;
        if (isShowingLive) {
            updateDisplay(true);
        }
    }
    
    public void toggleView(boolean showLive) {
        this.isShowingLive = showLive;
        updateDisplay(showLive);
    }

    private void updateDisplay(boolean isLive) {
        DecimalFormat pnlFormat = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        if (isLive) {
            titleLabel.setText("Session Equity Curve");
            chart.updateData(liveEquityCurve);
            // MFE/MAE and Drawdown/Runup are not calculated for live sessions, so hide them.
            maxDrawdownValue.setText("-");
            maxRunupValue.setText("-");
        } else {
            titleLabel.setText("Finished Trades PNL");
            chart.updateData(overallEquityCurve);
            maxDrawdownValue.setText(pnlFormat.format(overallMaxDrawdown));
            maxDrawdownValue.setForeground(UIManager.getColor("app.color.negative"));
            maxRunupValue.setText(pnlFormat.format(overallMaxRunup));
            maxRunupValue.setForeground(UIManager.getColor("app.color.accent"));
        }
    }

    private JPanel createFooterStat(String title, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        valueLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(Font.BOLD, 14f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.SOUTH);
        return panel;
    }
}