package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;

/**
 * A widget that displays the user's progress toward their optimal daily trade count
 * OR their live session discipline score.
 * The visual indicator changes color based on performance relative to the goal.
 */
public class DailyDisciplineWidget extends JPanel {

    private final JLabel valueLabel;
    private final GaugeChart gaugeChart;
    private final JLabel titleLabel;

    // --- Dual State Fields ---
    private int overallValue, overallTotal;
    private int liveValue, liveTotal;
    private boolean isShowingLive = false;

    public DailyDisciplineWidget() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setLayout(new BorderLayout(0, 5));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titleLabel = new JLabel("Daily Trade Limit");
        titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        titlePanel.add(titleLabel, BorderLayout.WEST);
        add(titlePanel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 2));
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.CENTER);

        gaugeChart = new GaugeChart(GaugeChart.GaugeType.FULL_CIRCLE);
        contentPanel.add(gaugeChart, BorderLayout.CENTER);

        valueLabel = new JLabel("- / - Trades");
        valueLabel.setFont(UIManager.getFont("app.font.widget_title"));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentPanel.add(valueLabel, BorderLayout.SOUTH);

        updateUI(); // Apply initial theme
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            valueLabel.setForeground(UIManager.getColor("Label.foreground"));
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

    public void setOverallData(int tradesToday, int optimalTradeCount) {
        this.overallValue = tradesToday;
        this.overallTotal = optimalTradeCount;
        if (!isShowingLive) {
            updateDisplay(false);
        }
    }

    public void setLiveData(int score, int maxScore) {
        this.liveValue = score;
        this.liveTotal = maxScore;
        if (isShowingLive) {
            updateDisplay(true);
        }
    }

    public void toggleView(boolean showLive) {
        this.isShowingLive = showLive;
        updateDisplay(showLive);
    }
    
    private void updateDisplay(boolean isLive) {
        if (isLive) {
            titleLabel.setText("Session Discipline");
            valueLabel.setText(String.format("%d / %d", liveValue, liveTotal));
            
            double progress = (double) liveValue / liveTotal;
            Color progressColor;
            if (progress >= 0.8) progressColor = UIManager.getColor("app.color.positive");
            else if (progress >= 0.5) progressColor = UIManager.getColor("app.trading.pending");
            else progressColor = UIManager.getColor("app.color.negative");
            gaugeChart.setTwoColorData(progress, 0, progressColor, progressColor);
        } else {
            titleLabel.setText("Daily Trade Limit");
            int total = Math.max(1, overallTotal);
            valueLabel.setText(String.format("%d / %d Trades", overallValue, total));

            double progress = (double) overallValue / total;
            Color progressColor;
            if (progress > 1.0) progressColor = UIManager.getColor("app.color.negative");
            else if (progress > 0.8) progressColor = UIManager.getColor("app.trading.pending");
            else progressColor = UIManager.getColor("app.color.positive");
            gaugeChart.setTwoColorData(progress, 0, progressColor, progressColor);
        }
    }
}