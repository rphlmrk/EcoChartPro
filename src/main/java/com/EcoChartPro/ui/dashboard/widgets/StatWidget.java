package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.ui.dashboard.theme.UITheme;
import javax.swing.*;
import java.awt.*;

public class StatWidget extends JPanel {

    private final JLabel valueLabel;
    private final JPanel contentPanel;
    private final JPanel graphicPanel;
    private JComponent currentGraphic = null;
    
    private final JLabel titleLabel;
    private final JLabel infoIconLabel;
    private String tooltipText;

    // --- Dual State Fields ---
    private String overallTitle, liveTitle;
    private String overallValue, liveValue;
    private Color overallValueColor, liveValueColor;
    private JComponent overallGraphic, liveGraphic;
    private boolean isShowingLive = false;

    public StatWidget(String title, String tooltipText) {
        this.tooltipText = tooltipText; 
        this.overallTitle = title;
        this.liveTitle = "Session " + title; // Default live title

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        setLayout(new BorderLayout(0, 5));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        titlePanel.add(titleLabel, BorderLayout.WEST);

        infoIconLabel = new JLabel();
        if (tooltipText != null && !tooltipText.isBlank()) {
            infoIconLabel.setToolTipText(tooltipText);
            infoIconLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
            titlePanel.add(infoIconLabel, BorderLayout.EAST);
        }
        add(titlePanel, BorderLayout.NORTH);

        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.CENTER);

        valueLabel = new JLabel("-");
        valueLabel.setFont(UIManager.getFont("app.font.value_large"));
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        valueLabel.setVerticalAlignment(SwingConstants.CENTER);

        graphicPanel = new JPanel(new BorderLayout());
        graphicPanel.setOpaque(false);

        updateLayout();
        updateUI();
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) { 
            titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            valueLabel.setForeground(UIManager.getColor("Label.foreground"));
            if (tooltipText != null && !tooltipText.isBlank()) {
                infoIconLabel.setIcon(UITheme.getIcon(UITheme.Icons.INFO, 14, 14, UIManager.getColor("Label.disabledForeground")));
            }
            repaint();
        }
    }

    private void updateLayout() {
        contentPanel.removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

        if (currentGraphic == null) {
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 1.0;
            valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
            contentPanel.add(valueLabel, gbc);
        } else {
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 1.0;
            valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
            contentPanel.add(valueLabel, gbc);

            gbc.gridx = 1; gbc.weightx = 0.4;
            graphicPanel.removeAll();
            graphicPanel.add(currentGraphic, BorderLayout.CENTER);
            contentPanel.add(graphicPanel, gbc);
        }
        contentPanel.revalidate();
        contentPanel.repaint();
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
    
    // --- Methods for setting Overall (Static Report) State ---
    public void setOverallValue(String text, Color color) {
        this.overallValue = text;
        this.overallValueColor = color;
        if (!isShowingLive) {
            setValue(text);
            setValueColor(color);
        }
    }
    
    public void setOverallGraphic(JComponent component) {
        this.overallGraphic = component;
        if (!isShowingLive) {
            setGraphicComponent(component);
        }
    }
    
    // --- Methods for setting Live Session State ---
    public void setLiveValue(String text, Color color) {
        this.liveValue = text;
        this.liveValueColor = color;
        if (isShowingLive) {
            setValue(text);
            setValueColor(color);
        }
    }
    
    public void setLiveGraphic(JComponent component) {
        this.liveGraphic = component;
        if (isShowingLive) {
            setGraphicComponent(component);
        }
    }
    
    /**
     * Toggles the display between the live and overall stats.
     * @param showLive true to display live session data, false for overall report data.
     */
    public void toggleView(boolean showLive) {
        this.isShowingLive = showLive;
        if (isShowingLive) {
            setTitle(liveTitle);
            setValue(liveValue);
            setValueColor(liveValueColor);
            setGraphicComponent(liveGraphic);
        } else {
            setTitle(overallTitle);
            setValue(overallValue);
            setValueColor(overallValueColor);
            setGraphicComponent(overallGraphic);
        }
    }

    public void setValue(String text) {
        valueLabel.setText(text);
    }

    public void setValueColor(Color color) {
        valueLabel.setForeground(color);
    }

    public void setGraphicComponent(JComponent component) {
        this.currentGraphic = component;
        updateLayout();
    }
    
    public JComponent getGraphicComponent() {
        return this.currentGraphic;
    }

    public void setTitle(String title) {
        this.titleLabel.setText(title);
    }
}