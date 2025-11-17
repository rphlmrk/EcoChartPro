package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dashboard.widgets.BtcPerformanceWidget;
import com.EcoChartPro.ui.dashboard.widgets.PluginMarketplaceWidget;
import javax.swing.*;
import java.awt.*;

public class DashboardViewPanel extends JPanel {
    private final JLabel title;
    private final JLabel subTitle;
    private final BtcPerformanceWidget btcWidget; // [NEW] Keep a reference for cleanup
    private final PluginMarketplaceWidget marketplaceWidget;
    private JLabel scrollCueLabel; // [NEW]

    public DashboardViewPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // --- Welcome Panel (Row 0) ---
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
        
        // --- Widgets Panel (Row 1) ---
        JPanel widgetsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        widgetsPanel.setOpaque(false);

        btcWidget = new BtcPerformanceWidget();
        marketplaceWidget = new PluginMarketplaceWidget();

        widgetsPanel.add(btcWidget);
        widgetsPanel.add(marketplaceWidget);

        // --- Scroll Cue Panel (Row 2) ---
        JPanel scrollCuePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        scrollCuePanel.setOpaque(false);
        scrollCueLabel = new JLabel("Scroll Down for Analysis");
        scrollCueLabel.setIcon(UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 16, 16));
        scrollCueLabel.setVerticalTextPosition(SwingConstants.BOTTOM);
        scrollCueLabel.setHorizontalTextPosition(SwingConstants.CENTER);
        scrollCuePanel.add(scrollCueLabel);

        // --- Add all components to the main panel ---
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        
        gbc.gridy = 0;
        gbc.insets = new Insets(80, 0, 0, 0);
        add(welcomePanel, gbc);
        
        gbc.gridy = 1;
        gbc.insets = new Insets(40, 0, 0, 0);
        add(widgetsPanel, gbc);
        
        gbc.gridy = 2;
        gbc.weighty = 1.0; // Pushes everything above it up
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.insets = new Insets(20, 0, 40, 0);
        add(scrollCuePanel, gbc);

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
            if (scrollCueLabel != null) {
                scrollCueLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                scrollCueLabel.setIcon(UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 16, 16, UIManager.getColor("Label.disabledForeground")));
            }
        }
    }
}