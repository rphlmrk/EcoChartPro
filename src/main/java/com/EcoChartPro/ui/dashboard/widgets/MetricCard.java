package com.EcoChartPro.ui.dashboard.widgets;

import javax.swing.*;
import java.awt.*;

public class MetricCard extends JPanel {

    private final JLabel valueLabel;

    // State for switching modes
    private String overallValue = "-";
    private Color overallColor = UIManager.getColor("Label.foreground");

    private String liveValue = "-";
    private Color liveColor = UIManager.getColor("Label.foreground");

    private boolean isLive = false;

    public MetricCard() {
        setOpaque(false);
        setLayout(new GridBagLayout()); // Centers the text vertically and horizontally

        valueLabel = new JLabel("-");
        // Increased font size since we have more room now
        valueLabel.setFont(UIManager.getFont("app.font.value_large").deriveFont(Font.BOLD, 32f));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        add(valueLabel);
    }

    public void setOverallData(String value, Color color) {
        this.overallValue = value;
        this.overallColor = color;
        if (!isLive)
            refresh();
    }

    public void setLiveData(String value, Color color) {
        this.liveValue = value;
        this.liveColor = color;
        if (isLive)
            refresh();
    }

    public void toggleMode(boolean isLive) {
        this.isLive = isLive;
        refresh();
    }

    private void refresh() {
        valueLabel.setText(isLive ? liveValue : overallValue);
        valueLabel.setForeground(isLive ? liveColor : overallColor);
        repaint();
    }
}