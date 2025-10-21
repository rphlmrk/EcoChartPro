package com.EcoChartPro.ui.dashboard.widgets;

import com.EcoChartPro.core.plugin.MarketplaceService;
import com.EcoChartPro.core.plugin.PluginInfo;
import com.EcoChartPro.ui.Analysis.TitledContentPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.MarketplaceDialog;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PluginMarketplaceWidget extends TitledContentPanel {

    private final JPanel pluginGrid;
    private final MarketplaceService marketplaceService;

    public PluginMarketplaceWidget() {
        super("Community Marketplace", new JPanel(new BorderLayout(0, 10)));
        this.marketplaceService = new MarketplaceService();
        JPanel content = (JPanel) this.getContentPane();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Description ---
        JTextArea description = new JTextArea("Discover indicators built by the community. Here are some of the latest additions:");
        description.setOpaque(false);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFocusable(false);
        description.setFont(UIManager.getFont("app.font.widget_content"));
        description.setForeground(UIManager.getColor("Label.disabledForeground"));
        content.add(description, BorderLayout.CENTER);

        // --- Featured Plugins ---
        pluginGrid = new JPanel(new GridLayout(1, 3, 10, 10));
        pluginGrid.setOpaque(false);
        content.add(pluginGrid, BorderLayout.NORTH);
        
        // --- Call to Action Button ---
        JButton exploreButton = new JButton("Explore Marketplace...");
        exploreButton.setOpaque(false);
        exploreButton.addActionListener(e -> {
            // Find the parent frame and open the modal dialog
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            new MarketplaceDialog(parentFrame).setVisible(true);
        });
        content.add(exploreButton, BorderLayout.SOUTH);

        // Fetch and display the featured plugins on a background thread
        fetchAndDisplayPlugins();
    }

    private void fetchAndDisplayPlugins() {
        // Use a SwingWorker to fetch data off the Event Dispatch Thread
        new SwingWorker<List<PluginInfo>, Void>() {
            @Override
            protected List<PluginInfo> doInBackground() throws Exception {
                // Fetch the entire index from the marketplace
                return marketplaceService.fetchPluginIndex();
            }

            @Override
            protected void done() {
                try {
                    List<PluginInfo> plugins = get();
                    pluginGrid.removeAll(); // Clear any existing placeholders

                    // Display the first 3 plugins as "featured"
                    plugins.stream().limit(3).forEach(plugin -> {
                        pluginGrid.add(createPluginCard(plugin));
                    });
                    
                    // If there are fewer than 3 plugins, fill the rest with empty panels
                    for (int i = pluginGrid.getComponentCount(); i < 3; i++) {
                        JPanel emptyPanel = new JPanel();
                        emptyPanel.setOpaque(false);
                        pluginGrid.add(emptyPanel);
                    }

                } catch (Exception e) {
                    // Handle case where marketplace can't be reached
                    pluginGrid.removeAll();
                    JLabel errorLabel = new JLabel("Could not load marketplace items.");
                    errorLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
                    pluginGrid.add(errorLabel);
                } finally {
                    pluginGrid.revalidate();
                    pluginGrid.repaint();
                }
            }
        }.execute();
    }

    private JPanel createPluginCard(PluginInfo plugin) {
        JPanel card = new JPanel(new BorderLayout(0, 5));
        card.setOpaque(true);
        card.setBackground(UIManager.getColor("Component.borderColor"));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        // In the future, this could be updated to load plugin.iconUrl()
        JLabel icon = new JLabel(UITheme.getIcon(UITheme.Icons.PLUGIN_JAVA, 24, 24, UIManager.getColor("Label.foreground")));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel label = new JLabel(plugin.name(), SwingConstants.CENTER);
        label.setToolTipText(plugin.name() + " by " + plugin.author());
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(11f));
        
        card.add(icon, BorderLayout.CENTER);
        card.add(label, BorderLayout.SOUTH);
        return card;
    }
}