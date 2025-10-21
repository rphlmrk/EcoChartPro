package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.plugin.MarketplaceService;
import com.EcoChartPro.core.plugin.PluginInfo;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MarketplaceDialog extends JDialog {

    private final MarketplaceService marketplaceService;
    private final JPanel pluginsPanel;
    private final JScrollPane scrollPane;
    private final JTextField searchField;

    public MarketplaceDialog(Frame owner) {
        super(owner, "Community Marketplace", true);
        this.marketplaceService = new MarketplaceService();
        setSize(800, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- Top Panel: Search and Filters ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by name, author, or tag...");
        searchField.addActionListener(e -> refreshPluginList());
        topPanel.add(searchField, BorderLayout.CENTER);

        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> refreshPluginList());
        topPanel.add(searchButton, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // --- Center Panel: Plugin List ---
        pluginsPanel = new JPanel();
        pluginsPanel.setLayout(new BoxLayout(pluginsPanel, BoxLayout.Y_AXIS));
        pluginsPanel.setBackground(UIManager.getColor("List.background"));

        scrollPane = new JScrollPane(pluginsPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        
        // --- Bottom Panel: Disclaimer ---
        JLabel disclaimerLabel = new JLabel("Plugins are community-provided. Review code before use. Use at your own risk.", SwingConstants.CENTER);
        disclaimerLabel.setFont(disclaimerLabel.getFont().deriveFont(10f));
        disclaimerLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        add(disclaimerLabel, BorderLayout.SOUTH);

        refreshPluginList();
    }

    private void refreshPluginList() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        pluginsPanel.removeAll();

        new SwingWorker<List<PluginInfo>, Void>() {
            @Override
            protected List<PluginInfo> doInBackground() throws Exception {
                return marketplaceService.fetchPluginIndex();
            }

            @Override
            protected void done() {
                try {
                    List<PluginInfo> plugins = get();
                    String searchText = searchField.getText().toLowerCase();

                    List<PluginInfo> filteredPlugins = plugins.stream()
                        .filter(p -> searchText.isEmpty() ||
                                     p.name().toLowerCase().contains(searchText) ||
                                     p.author().toLowerCase().contains(searchText) ||
                                     p.description().toLowerCase().contains(searchText) ||
                                     p.tags().stream().anyMatch(tag -> tag.toLowerCase().contains(searchText)))
                        .sorted(Comparator.comparing(PluginInfo::name))
                        .collect(Collectors.toList());

                    if (filteredPlugins.isEmpty()) {
                        pluginsPanel.add(createPlaceholder("No plugins found."));
                    } else {
                        for (PluginInfo plugin : filteredPlugins) {
                            pluginsPanel.add(new PluginEntryPanel(plugin, marketplaceService, MarketplaceDialog.this));
                            // [FIX] Use a proper separator instead of a strut for better visuals
                            pluginsPanel.add(new JSeparator());
                        }
                    }

                } catch (Exception e) {
                    pluginsPanel.add(createPlaceholder("Failed to fetch marketplace index: " + e.getMessage()));
                } finally {
                    pluginsPanel.revalidate();
                    pluginsPanel.repaint();
                    // Scroll back to the top after refreshing
                    SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(0));
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }
    
    private JPanel createPlaceholder(String text) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.add(new JLabel(text));
        panel.setPreferredSize(new Dimension(100, 100));
        return panel;
    }
    
    // --- Inner Class for each plugin entry in the list ---
    private static class PluginEntryPanel extends JPanel {
        private final PluginInfo plugin;
        private final MarketplaceService service;
        private final MarketplaceDialog parentDialog;
        private final JButton actionButton;

        PluginEntryPanel(PluginInfo plugin, MarketplaceService service, MarketplaceDialog parentDialog) {
            this.plugin = plugin;
            this.service = service;
            this.parentDialog = parentDialog;
            
            // [FIX] Use GridBagLayout for precise control over component sizing and alignment.
            setLayout(new GridBagLayout());
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setOpaque(false); // Let the list background show through
            
            GridBagConstraints gbc = new GridBagConstraints();

            // --- Icon ---
            // [FIX] Reduced icon size for a more compact look.
            JLabel iconLabel = new JLabel(UITheme.getIcon(UITheme.Icons.PLUGIN_JAVA, 24, 24));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 0;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(0, 0, 0, 15);
            add(iconLabel, gbc);

            // --- Info Panel ---
            JPanel infoPanel = new JPanel();
            infoPanel.setOpaque(false);
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            
            JLabel nameLabel = new JLabel(plugin.name() + " (v" + plugin.version() + ")");
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            JLabel authorLabel = new JLabel("by " + plugin.author());
            authorLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            JLabel descLabel = new JLabel(plugin.description());

            infoPanel.add(nameLabel);
            infoPanel.add(authorLabel);
            infoPanel.add(Box.createVerticalStrut(5));
            infoPanel.add(descLabel);
            
            // [FIX] Make the info panel expand horizontally to fill space.
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(0, 0, 0, 0);
            add(infoPanel, gbc);

            // --- Action Button ---
            actionButton = new JButton();
            updateButtonState();
            
            // [FIX] The button will now only take its preferred size, not stretch vertically.
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(0, 15, 0, 0);
            add(actionButton, gbc);
        }

        private void updateButtonState() {
            if (service.isUpdateAvailable(plugin)) {
                configureButton("Update", this::handleInstall);
            } else if (service.isInstalled(plugin.id())) {
                configureButton("Uninstall", this::handleUninstall);
            } else {
                configureButton("Install", this::handleInstall);
            }
        }

        private void configureButton(String text, Runnable action) {
            actionButton.setText(text);
            for (var l : actionButton.getActionListeners()) {
                actionButton.removeActionListener(l);
            }
            actionButton.addActionListener(e -> action.run());
        }

        private void handleInstall() {
            actionButton.setText("Installing...");
            actionButton.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    service.installPlugin(plugin);
                    PluginManager.getInstance().rescanPlugins();
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(parentDialog, "Successfully installed '" + plugin.name() + "'.", "Install Complete", JOptionPane.INFORMATION_MESSAGE);
                        parentDialog.refreshPluginList();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(parentDialog, "Failed to install plugin: " + e.getCause().getMessage(), "Install Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        actionButton.setEnabled(true);
                        updateButtonState();
                    }
                }
            }.execute();
        }

        private void handleUninstall() {
            int choice = JOptionPane.showConfirmDialog(parentDialog, "Are you sure you want to uninstall '" + plugin.name() + "'?", "Confirm Uninstall", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;

            actionButton.setText("Uninstalling...");
            actionButton.setEnabled(false);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    service.uninstallPlugin(plugin);
                    PluginManager.getInstance().rescanPlugins();
                    return null;
                }
                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(parentDialog, "Successfully uninstalled '" + plugin.name() + "'.", "Uninstall Complete", JOptionPane.INFORMATION_MESSAGE);
                        parentDialog.refreshPluginList();
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(parentDialog, "Failed to uninstall plugin: " + e.getCause().getMessage(), "Uninstall Error", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        actionButton.setEnabled(true);
                        updateButtonState();
                    }
                }
            }.execute();
        }
    }
}