package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.Parameter;
import com.EcoChartPro.api.indicator.ParameterType;
import com.EcoChartPro.core.indicator.CustomIndicatorAdapter;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.indicator.IndicatorManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IndicatorDialog extends JDialog {

    private final ChartDataModel dataModel;
    private final ChartPanel chartPanel;
    private final IndicatorManager indicatorManager;

    private final JList<CustomIndicator> availableIndicatorsList;
    private final JList<Indicator> activeIndicatorsList;
    private final DefaultListModel<Indicator> activeIndicatorsListModel;
    private final PropertyChangeListener indicatorManagerListener;
    private final PropertyChangeListener pluginManagerListener;

    public IndicatorDialog(Frame owner, ChartPanel chartPanel) {
        super(owner, "Indicators", true);
        this.chartPanel = chartPanel;
        this.dataModel = chartPanel.getDataModel();
        this.indicatorManager = dataModel.getIndicatorManager();

        setSize(600, 450);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane splitPane = new JSplitPane();
        splitPane.setResizeWeight(0.4);

        availableIndicatorsList = new JList<>();
        availableIndicatorsList.setCellRenderer(new CustomIndicatorRenderer());
        populateAvailableIndicators();
        JPanel availablePanel = createListPanel("Available", availableIndicatorsList);
        splitPane.setLeftComponent(availablePanel);

        activeIndicatorsListModel = new DefaultListModel<>();
        activeIndicatorsList = new JList<>(activeIndicatorsListModel);
        activeIndicatorsList.setCellRenderer(new ActiveIndicatorRenderer());
        JPanel activePanel = createActiveIndicatorsPanel();
        splitPane.setRightComponent(activePanel);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        addListeners();
        this.indicatorManagerListener = evt -> refreshActiveIndicatorsList();
        this.indicatorManager.addPropertyChangeListener(indicatorManagerListener);

        this.pluginManagerListener = evt -> {
            if ("pluginListChanged".equals(evt.getPropertyName())) {
                populateAvailableIndicators();
            }
        };
        PluginManager.getInstance().addPropertyChangeListener(pluginManagerListener);

        refreshActiveIndicatorsList();
    }

    @Override
    public void dispose() {
        this.indicatorManager.removePropertyChangeListener(indicatorManagerListener);
        PluginManager.getInstance().removePropertyChangeListener(pluginManagerListener);
        super.dispose();
    }

    private JPanel createListPanel(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(content), BorderLayout.CENTER);

        JButton refreshButton = new JButton("Refresh List", UITheme.getIcon(UITheme.Icons.REFRESH, 16, 16));
        refreshButton.addActionListener(e -> {
            PluginManager.getInstance().rescanPlugins();
        });
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(refreshButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createActiveIndicatorsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Active on Chart"));
        panel.add(new JScrollPane(activeIndicatorsList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton settingsButton = new JButton("Settings...");
        settingsButton.addActionListener(e -> handleEditIndicator());
        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> handleRemoveIndicator());

        buttonPanel.add(settingsButton);
        buttonPanel.add(removeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void addListeners() {
        availableIndicatorsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleAddIndicator();
                }
            }
        });

        activeIndicatorsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleEditIndicator();
                }
            }
        });
    }

    private void populateAvailableIndicators() {
        SwingUtilities.invokeLater(() -> {
            DefaultListModel<CustomIndicator> model = new DefaultListModel<>();
            PluginManager.getInstance().getLoadedIndicators().forEach(model::addElement);
            availableIndicatorsList.setModel(model);
        });
    }

    private void refreshActiveIndicatorsList() {
        SwingUtilities.invokeLater(() -> {
            activeIndicatorsListModel.clear();
            activeIndicatorsListModel.addAll(indicatorManager.getIndicators());
        });
    }

    private void handleAddIndicator() {
        CustomIndicator selected = availableIndicatorsList.getSelectedValue();
        if (selected == null) return;
        showDynamicSettingsDialog(selected, null);
    }

    private void handleEditIndicator() {
        Indicator selected = activeIndicatorsList.getSelectedValue();
        if (selected == null) return;

        if (selected instanceof CustomIndicatorAdapter adapter) {
            showDynamicSettingsDialog(adapter.getPlugin(), adapter);
        }
    }

    private void handleRemoveIndicator() {
        Indicator selected = activeIndicatorsList.getSelectedValue();
        if (selected == null) return;
        indicatorManager.removeIndicator(selected.getId());
        // After removing, we need to trigger a recalculation and repaint
        dataModel.triggerIndicatorRecalculation();
        dataModel.fireDataUpdated();
    }

    private void showDynamicSettingsDialog(CustomIndicator plugin, Indicator existingInstance) {
        List<Parameter> parameters = plugin.getParameters();
        if (parameters.isEmpty()) {
            if (existingInstance == null) {
                Indicator newInstance = new CustomIndicatorAdapter(plugin, new HashMap<>());
                // [MODIFIED] Pass the dataModel to addIndicator.
                indicatorManager.addIndicator(newInstance, dataModel);
                dataModel.triggerIndicatorRecalculation();
                dataModel.fireDataUpdated();
            } else {
                JOptionPane.showMessageDialog(this, "This indicator has no settings to edit.", "Info", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        JDialog dialog = new JDialog(this, plugin.getName() + " Settings", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        Map<String, JComponent> settingsComponents = new HashMap<>();
        Map<String, Object> currentSettings = (existingInstance != null) ? existingInstance.getSettings() : new HashMap<>();

        for (int i = 0; i < parameters.size(); i++) {
            Parameter param = parameters.get(i);
            gbc.gridx = 0;
            gbc.gridy = i;
            contentPanel.add(new JLabel(param.key() + ":"), gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            Object currentValue = currentSettings.getOrDefault(param.key(), param.defaultValue());
            JComponent component = createComponentForParameter(param, currentValue);
            settingsComponents.put(param.key(), component);
            contentPanel.add(component, gbc);
        }

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            Map<String, Object> newSettings = new HashMap<>();
            settingsComponents.forEach((key, comp) -> newSettings.put(key, getComponentValue(comp)));

            if (existingInstance == null) {
                Indicator newInstance = new CustomIndicatorAdapter(plugin, newSettings);
                // [MODIFIED] Pass the dataModel to addIndicator.
                indicatorManager.addIndicator(newInstance, dataModel);
            } else {
                indicatorManager.updateIndicatorSettings(existingInstance.getId(), newSettings);
            }

            dataModel.triggerIndicatorRecalculation();
            dataModel.fireDataUpdated();
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        
        // Constrain dialog size after packing to prevent it from becoming too large
        Dimension packedSize = dialog.getPreferredSize();
        int width = Math.min(packedSize.width, 500);
        int height = Math.min(packedSize.height, 600);
        dialog.setSize(width, height);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JComponent createComponentForParameter(Parameter param, Object value) {
        switch (param.type()) {
            case INTEGER:
                SpinnerNumberModel intModel = new SpinnerNumberModel(((Number) value).intValue(), -10000, 10000, 1);
                return new JSpinner(intModel);
            case DECIMAL:
                SpinnerNumberModel decModel = new SpinnerNumberModel(((Number) value).doubleValue(), -10000.0, 10000.0, 0.1);
                return new JSpinner(decModel);
            case CHOICE:
                JComboBox<String> comboBox = new JComboBox<>(param.choices());
                comboBox.setSelectedItem(value);
                return comboBox;
            case BOOLEAN:
                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected((Boolean) value);
                return checkBox;
            case COLOR:
                Color initialColor = (Color) value;
                JButton colorButton = new JButton(" ");
                colorButton.setOpaque(true);
                colorButton.setBackground(initialColor);
                colorButton.putClientProperty("selectedColor", initialColor);
                colorButton.addActionListener(e -> {
                    Consumer<Color> onColorUpdate = newColor -> {
                        colorButton.setBackground(newColor);
                        colorButton.putClientProperty("selectedColor", newColor);
                    };

                    CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(colorButton.getBackground(), onColorUpdate);
                    JPopupMenu popupMenu = new JPopupMenu();
                    popupMenu.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
                    popupMenu.add(colorPanel);
                    popupMenu.show(colorButton, 0, colorButton.getHeight());
                });
                return colorButton;
            default:
                return new JLabel("Unsupported type");
        }
    }

    private Object getComponentValue(JComponent component) {
        if (component instanceof JSpinner) {
            Object value = ((JSpinner) component).getValue();
            if (value instanceof Double) {
                return BigDecimal.valueOf((Double) value);
            }
            return value;
        } else if (component instanceof JComboBox) {
            return ((JComboBox<?>) component).getSelectedItem();
        } else if (component instanceof JCheckBox) {
            return ((JCheckBox) component).isSelected();
        } else if (component instanceof JButton) { // For the color button
            return ((JButton) component).getClientProperty("selectedColor");
        }
        return null;
    }

    private static class ActiveIndicatorRenderer extends DefaultListCellRenderer {
        
        /**
         * [MODIFIED] Helper method to create a more readable string representation of indicator settings.
         */
        private String formatSettings(Map<String, Object> settings) {
            if (settings == null || settings.isEmpty()) {
                return "";
            }
            return settings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Sort for consistent order
                .map(entry -> {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    String valueStr;
                    if (value instanceof BigDecimal) {
                        valueStr = ((BigDecimal) value).toPlainString();
                    } else if (value instanceof Color c) {
                        valueStr = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                    } else {
                        valueStr = value.toString();
                    }
                    return key + "=" + valueStr;
                })
                .collect(Collectors.joining(", ", " (", ")"));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Indicator indicator) {
                // [MODIFIED] Use the new formatting helper for a cleaner display.
                String text = indicator.getName() + formatSettings(indicator.getSettings());
                label.setText(text);
            }
            return label;
        }
    }

    private static class CustomIndicatorRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CustomIndicator indicator) {
                label.setText(indicator.getName());
                label.setIcon(UITheme.getIcon("/icons/java.svg", 16, 16));
                label.setForeground(isSelected ? Color.WHITE : new Color(0x61AFEF));
            }
            return label;
        }
    }
}