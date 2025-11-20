package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;

public class DailyDisciplineWidget extends JPanel {

    private final JLabel valueLabel;
    private final JLabel titleLabel;
    private final JProgressBar progressBar;

    // --- Dual State Fields ---
    private int overallValue, overallTotal;
    private int liveValue, liveTotal;
    private boolean isShowingLive = false;

    public DailyDisciplineWidget() {
        setOpaque(false);
        setLayout(new GridBagLayout());

        // GridBag constraints
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 5, 0, 5);

        // 1. Title (Top Left)
        titleLabel = new JLabel("Daily Limit");
        titleLabel.setFont(UIManager.getFont("app.font.widget_title"));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        // 2. Value (Center, Large)
        valueLabel = new JLabel("- / -");
        valueLabel.setFont(UIManager.getFont("app.font.value_large").deriveFont(Font.BOLD, 24f));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 3. Linear Progress Bar (Bottom)
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(100, 6));
        progressBar.setBorderPainted(false);

        // Layout
        gbc.gridy = 0;
        add(titleLabel, gbc);

        gbc.gridy = 1;
        gbc.weighty = 1.0; // Push value to center
        add(valueLabel, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0;
        gbc.insets = new Insets(0, 10, 10, 10);
        add(progressBar, gbc);

        updateUI();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            valueLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    public void setOverallData(int tradesToday, int optimalTradeCount) {
        this.overallValue = tradesToday;
        this.overallTotal = optimalTradeCount;
        if (!isShowingLive)
            updateDisplay(false);
    }

    public void setLiveData(int score, int maxScore) {
        this.liveValue = score;
        this.liveTotal = maxScore;
        if (isShowingLive)
            updateDisplay(true);
    }

    public void toggleView(boolean showLive) {
        this.isShowingLive = showLive;
        updateDisplay(showLive);
    }

    private void updateDisplay(boolean isLive) {
        if (isLive) {
            titleLabel.setText("Session Score");
            valueLabel.setText(String.format("%d / %d", liveValue, liveTotal));

            double progress = (double) liveValue / liveTotal;
            updateBarColor(progress, true);
            progressBar.setValue((int) (progress * 100));
        } else {
            titleLabel.setText("Trades Taken");
            int total = Math.max(1, overallTotal);
            valueLabel.setText(String.format("%d / %d", overallValue, total));

            double progress = (double) overallValue / total;
            updateBarColor(progress, false);
            progressBar.setValue((int) (progress * 100));
        }
    }

    private void updateBarColor(double progress, boolean isScore) {
        Color c;
        if (isScore) {
            // High score is green
            if (progress >= 0.8)
                c = UIManager.getColor("app.color.positive");
            else if (progress >= 0.5)
                c = UIManager.getColor("app.trading.pending");
            else
                c = UIManager.getColor("app.color.negative");
        } else {
            // High trade count (overtrading) is red
            if (progress > 1.0)
                c = UIManager.getColor("app.color.negative");
            else if (progress > 0.8)
                c = UIManager.getColor("app.trading.pending");
            else
                c = UIManager.getColor("app.color.positive");
        }
        progressBar.setForeground(c);
    }
}