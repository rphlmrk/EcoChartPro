package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.ui.Analysis.TitledContentPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

public class PluginMarketplaceWidget extends TitledContentPanel {

    public PluginMarketplaceWidget() {
        super("Community Marketplace", new JPanel(new BorderLayout(0, 10)));
        JPanel content = (JPanel) this.getContentPane();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Description ---
        JTextArea description = new JTextArea("Coming Soon!\nShare, discover, and install custom indicators and tools built by the Eco Chart Pro community.");
        description.setOpaque(false);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFocusable(false);
        description.setFont(UIManager.getFont("app.font.widget_content"));
        description.setForeground(UIManager.getColor("Label.disabledForeground"));
        content.add(description, BorderLayout.CENTER);

        // --- Placeholder Plugins ---
        JPanel pluginGrid = new JPanel(new GridLayout(1, 3, 10, 10));
        pluginGrid.setOpaque(false);
        pluginGrid.add(createPluginCard("Session Volume", UITheme.Icons.INDICATORS));
        pluginGrid.add(createPluginCard("Market Profile", UITheme.Icons.INDICATORS));
        pluginGrid.add(createPluginCard("Auto FVG", UITheme.Icons.INDICATORS));
        content.add(pluginGrid, BorderLayout.NORTH);
        
        // --- Call to Action Button ---
        JButton learnApiButton = new JButton("Learn the API & Build Your Own");
        learnApiButton.setOpaque(false);
        learnApiButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/rphlmrk/EcoChartPro"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        content.add(learnApiButton, BorderLayout.SOUTH);
    }

    private JPanel createPluginCard(String name, String iconPath) {
        JPanel card = new JPanel(new BorderLayout(0, 5));
        card.setOpaque(true);
        card.setBackground(UIManager.getColor("Component.borderColor"));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        JLabel icon = new JLabel(UITheme.getIcon(iconPath, 24, 24, UIManager.getColor("Label.foreground")));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel label = new JLabel(name, SwingConstants.CENTER);
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(11f));
        
        card.add(icon, BorderLayout.CENTER);
        card.add(label, BorderLayout.SOUTH);
        return card;
    }
}