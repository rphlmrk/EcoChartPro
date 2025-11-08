package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig.DrawingToolTemplate;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class TemplatesSettingsPanel extends JPanel {

    private final SettingsService sm;
    private final JTabbedPane toolTabs;

    private static final String[] TEMPLATE_TOOLS = {
        "TrendlineObject", "RayObject", "HorizontalLineObject", "HorizontalRayObject",
        "VerticalLineObject", "RectangleObject", "FibonacciRetracementObject",
        "FibonacciExtensionObject", "MeasureToolObject", "ProtectedLevelPatternObject", "TextObject"
    };

    public TemplatesSettingsPanel(SettingsService settingsService) {
        this.sm = settingsService;
        this.toolTabs = new JTabbedPane();
        setLayout(new BorderLayout());
        
        for (String toolName : TEMPLATE_TOOLS) {
            // Use a more user-friendly name for the tab
            String tabTitle = toolName.replace("Object", "");
            toolTabs.addTab(tabTitle, new SingleToolTemplatePanel(toolName));
        }
        
        add(toolTabs, BorderLayout.CENTER);
    }
    
    public void applyChanges() {
        // All changes are applied instantly in this panel
    }

    private class SingleToolTemplatePanel extends JPanel {
        private final String toolName;
        private final JList<DrawingToolTemplate> templateList;
        private final DefaultListModel<DrawingToolTemplate> listModel;
        
        private final JPanel propertiesPanel;
        private final JButton colorButton;
        private final JSpinner thicknessSpinner;
        private final JCheckBox showPriceLabelCheckbox;
        private final JButton setActiveButton;
        
        // Flag to prevent recursive event firing
        private boolean isUpdatingFromSelection = false;

        SingleToolTemplatePanel(String toolName) {
            this.toolName = toolName;
            this.listModel = new DefaultListModel<>();
            this.templateList = new JList<>(listModel);

            // --- Init Components ---
            this.colorButton = new JButton(" ");
            this.thicknessSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
            this.showPriceLabelCheckbox = new JCheckBox("Show Price Label");
            this.setActiveButton = new JButton("Set as Active");
            this.propertiesPanel = createPropertiesPanel();
            
            setLayout(new BorderLayout());
            
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createTemplateListPanel(), propertiesPanel);
            splitPane.setDividerLocation(200);
            add(splitPane, BorderLayout.CENTER);

            loadTemplates();
            
            templateList.addListSelectionListener(this::onTemplateSelected);
            if (!listModel.isEmpty()) {
                templateList.setSelectedIndex(0);
            } else {
                 updatePropertiesPanel(null);
            }
        }

        private JPanel createTemplateListPanel() {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            templateList.setCellRenderer(new TemplateListRenderer());
            panel.add(new JScrollPane(templateList), BorderLayout.CENTER);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton addButton = new JButton("Add (+)");
            JButton removeButton = new JButton("Remove (-)");
            JButton renameButton = new JButton("Rename");
            buttonPanel.add(addButton);
            buttonPanel.add(removeButton);
            buttonPanel.add(renameButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);

            addButton.addActionListener(e -> addTemplate());
            removeButton.addActionListener(e -> removeTemplate());
            renameButton.addActionListener(e -> renameTemplate());

            return panel;
        }

        private JPanel createPropertiesPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createTitledBorder("Template Properties"));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;

            // Color
            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new JLabel("Color:"), gbc);
            gbc.gridx = 1;
            colorButton.setPreferredSize(new Dimension(80, 25));
            panel.add(colorButton, gbc);

            // Thickness
            gbc.gridx = 0; gbc.gridy = 1;
            panel.add(new JLabel("Thickness:"), gbc);
            gbc.gridx = 1;
            panel.add(thicknessSpinner, gbc);
            
            // Show Price Label
            gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
            panel.add(showPriceLabelCheckbox, gbc);
            
            // Set Active Button
            gbc.gridy = 3;
            panel.add(setActiveButton, gbc);

            // Spacer
            gbc.gridy = 4; gbc.weighty = 1.0;
            panel.add(new JPanel(), gbc);

            // Add listeners to update on change
            colorButton.addActionListener(e -> {
                 Consumer<Color> onColorUpdate = newColor -> {
                    colorButton.setBackground(newColor);
                    updateTemplateProperty();
                };
                CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(colorButton.getBackground(), onColorUpdate);
                JPopupMenu popupMenu = new JPopupMenu();
                popupMenu.add(colorPanel);
                popupMenu.show(colorButton, 0, colorButton.getHeight());
            });
            thicknessSpinner.addChangeListener(e -> updateTemplateProperty());
            showPriceLabelCheckbox.addActionListener(e -> updateTemplateProperty());
            setActiveButton.addActionListener(e -> {
                DrawingToolTemplate selected = templateList.getSelectedValue();
                if (selected != null) {
                    sm.setActiveTemplate(toolName, selected.id());
                    templateList.repaint(); // Repaint to show new active status
                    updatePropertiesPanel(selected); // To update button state
                }
            });

            return panel;
        }

        private void onTemplateSelected(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting()) {
                DrawingToolTemplate selected = templateList.getSelectedValue();
                isUpdatingFromSelection = true;
                updatePropertiesPanel(selected);
                isUpdatingFromSelection = false;
            }
        }

        private void updatePropertiesPanel(DrawingToolTemplate template) {
            boolean enabled = template != null;
            propertiesPanel.setEnabled(enabled);
            for(Component c : propertiesPanel.getComponents()) c.setEnabled(enabled);

            if (enabled) {
                colorButton.setBackground(template.color());
                thicknessSpinner.setValue((int)template.stroke().getLineWidth());
                showPriceLabelCheckbox.setSelected(template.showPriceLabel());
                
                UUID activeId = sm.getActiveTemplateId(toolName);
                boolean isActive = activeId != null && activeId.equals(template.id());
                setActiveButton.setText(isActive ? "Active" : "Set as Active");
                setActiveButton.setEnabled(!isActive);

                // Disable properties not applicable to certain tools
                boolean isTextTool = toolName.equals("TextObject");
                thicknessSpinner.setEnabled(!isTextTool);
            } else {
                colorButton.setBackground(UIManager.getColor("Panel.background"));
                thicknessSpinner.setValue(1);
                showPriceLabelCheckbox.setSelected(false);
                setActiveButton.setText("Set as Active");
                setActiveButton.setEnabled(false);
            }
        }
        
        private void updateTemplateProperty() {
            // If this method was called because the list selection changed, do nothing.
            if (isUpdatingFromSelection) return;

            int selectedIndex = templateList.getSelectedIndex();
            if (selectedIndex == -1) return;

            DrawingToolTemplate selected = listModel.getElementAt(selectedIndex);

            // Construct the new template based on the current UI state
            Color newColor = colorButton.getBackground();
            BasicStroke newStroke = new BasicStroke((Integer)thicknessSpinner.getValue());
            boolean newShowLabel = showPriceLabelCheckbox.isSelected();

            DrawingToolTemplate updatedTemplate = new DrawingToolTemplate(
                selected.id(), selected.name(), newColor, newStroke, newShowLabel, selected.specificProps()
            );

            // Update the setting in the manager
            sm.updateTemplate(toolName, updatedTemplate);

            // Update the UI model in-place to avoid a full reload and event loop
            listModel.setElementAt(updatedTemplate, selectedIndex);
        }

        private void loadTemplates() {
            listModel.clear();
            List<DrawingToolTemplate> templates = sm.getTemplatesForTool(toolName);
            if (templates.isEmpty()) {
                // If no templates exist, create a default one
                DrawingToolTemplate defaultTemplate = sm.getActiveTemplateForTool(toolName); // This creates a default if none exist
                sm.addTemplate(toolName, defaultTemplate);
                listModel.addElement(defaultTemplate);
            } else {
                listModel.addAll(templates);
            }
        }
        
        private void addTemplate() {
            String name = JOptionPane.showInputDialog(this, "New template name:", "Create Template", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.isBlank()) {
                DrawingToolTemplate active = sm.getActiveTemplateForTool(toolName);
                DrawingToolTemplate newTemplate = new DrawingToolTemplate(UUID.randomUUID(), name, active.color(), active.stroke(), active.showPriceLabel(), active.specificProps());
                sm.addTemplate(toolName, newTemplate);
                loadTemplates();
                templateList.setSelectedValue(newTemplate, true);
            }
        }

        private void removeTemplate() {
            DrawingToolTemplate selected = templateList.getSelectedValue();
            if (selected != null) {
                if (listModel.size() <= 1) {
                    JOptionPane.showMessageDialog(this, "Cannot delete the last template.", "Action Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete template '" + selected.name() + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    sm.deleteTemplate(toolName, selected.id());
                    loadTemplates();
                    templateList.setSelectedIndex(0);
                }
            }
        }

        private void renameTemplate() {
            DrawingToolTemplate selected = templateList.getSelectedValue();
            if (selected != null) {
                String newName = (String)JOptionPane.showInputDialog(this, "Enter new name:", "Rename Template", JOptionPane.PLAIN_MESSAGE, null, null, selected.name());
                if (newName != null && !newName.isBlank()) {
                    DrawingToolTemplate renamed = new DrawingToolTemplate(selected.id(), newName, selected.color(), selected.stroke(), selected.showPriceLabel(), selected.specificProps());
                    sm.updateTemplate(toolName, renamed);
                    int selectedIndex = templateList.getSelectedIndex();
                    loadTemplates();
                    templateList.setSelectedIndex(selectedIndex);
                }
            }
        }
        
        private class TemplateListRenderer extends DefaultListCellRenderer {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DrawingToolTemplate template) {
                    UUID activeId = sm.getActiveTemplateId(toolName);
                    boolean isActive = activeId != null && activeId.equals(template.id());
                    setText(template.name() + (isActive ? " (Active)" : ""));
                }
                return this;
            }
        }
    }
}