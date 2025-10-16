package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.ui.dashboard.widgets.BtcPerformanceWidget;
import com.EcoChartPro.ui.dashboard.widgets.PluginMarketplaceWidget;
import javax.swing.*;
import java.awt.*;

public class DashboardViewPanel extends JPanel {
    private final JLabel title;
    private final JLabel subTitle;
    private final BtcPerformanceWidget btcWidget; // [NEW] Keep a reference for cleanup
    private final PluginMarketplaceWidget marketplaceWidget;

    public DashboardViewPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // --- Welcome Panel (Top Center) ---
        JPanel welcomePanel = new JPanel();
        welcomePanel.setLayout(new BoxLayout(welcomePanel, BoxLayout.Y_AXIS));
        welcomePanel.setOpaque(false);
        
        title = new JLabel("Eco Chart Pro");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        subTitle = new JLabel("Your Professional Charting and Replay Solution");
        subTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        welcomePanel.add(title);
        welcomePanel.add(Box.createVerticalStrut(10));
        welcomePanel.add(subTitle);
        
        // --- Widgets Panel (Bottom Right) ---
        JPanel rightColumn = new JPanel();
        rightColumn.setOpaque(false);
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));

        btcWidget = new BtcPerformanceWidget();
        marketplaceWidget = new PluginMarketplaceWidget();

        rightColumn.add(btcWidget);
        rightColumn.add(Box.createVerticalStrut(15));
        rightColumn.add(marketplaceWidget);

        // This single cell will fill the entire panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        // Add the welcome panel, anchored to the TOP of the cell
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(80, 0, 0, 0); // Push it down below the navigation bar
        add(welcomePanel, gbc);
        
        // Add the widgets panel to the SAME cell, anchored to the BOTTOM-RIGHT
        gbc.anchor = GridBagConstraints.SOUTHEAST;
        gbc.insets = new Insets(0, 0, 20, 20); // Padding from bottom and right edges
        add(rightColumn, gbc);

        updateUI(); // Apply initial theme
    }
    
    // [NEW] Expose cleanup method for the frame to call
    public void cleanup() {
        if (btcWidget != null) {
            btcWidget.cleanup();
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (title != null) { // Check for null during construction
            title.setFont(UIManager.getFont("app.font.heading").deriveFont(48f));
            title.setForeground(UIManager.getColor("Label.foreground"));
            subTitle.setFont(UIManager.getFont("app.font.subheading"));
            subTitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }
}