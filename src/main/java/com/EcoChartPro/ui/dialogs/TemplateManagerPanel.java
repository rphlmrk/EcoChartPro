package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig.DrawingToolTemplate;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List; // Import the missing List class
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A reusable panel for managing the templates of a single drawing tool.
 */
class TemplateManagerPanel extends JPanel implements ListSelectionListener {
    private final String toolClassName;
    private final JList<DrawingToolTemplate> templateList;
    private final DefaultListModel<DrawingToolTemplate> listModel;
    private final SettingsService settingsService;

    private final JPanel propertiesPanel;
    private JButton colorButton;
    private JSpinner thicknessSpinner;
    private JCheckBox showLabelCheckBox;
    private JButton setActiveButton;

    TemplateManagerPanel(String toolClassName, String toolDisplayName) {
        this.toolClassName = toolClassName;
        this.settingsService = SettingsService.getInstance();
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Left: List of Templates ---
        listModel = new DefaultListModel<>();
        templateList = new JList<>(listModel);
        templateList.setCellRenderer(new TemplateListCellRenderer());
        templateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        templateList.addListSelectionListener(this);

        JScrollPane listScrollPane = new JScrollPane(templateList);
        listScrollPane.setPreferredSize(new Dimension(200, 0));

        // --- Center: Properties Editor ---
        propertiesPanel = new JPanel(new GridBagLayout());
        propertiesPanel.setBorder(BorderFactory.createTitledBorder("Template Properties"));
        createPropertiesControls();

        // --- Bottom: Action Buttons ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addButton = new JButton("Add (+)");
        JButton removeButton = new JButton("Remove (-)");
        JButton renameButton = new JButton("Rename");
        actionPanel.add(addButton);
        actionPanel.add(removeButton);
        actionPanel.add(renameButton);

        addButton.addActionListener(e -> addTemplate());
        removeButton.addActionListener(e -> removeTemplate());
        renameButton.addActionListener(e -> renameTemplate());

        JPanel listContainer = new JPanel(new BorderLayout(0, 5));
        listContainer.add(listScrollPane, BorderLayout.CENTER);
        listContainer.add(actionPanel, BorderLayout.SOUTH);

        // --- Assembly ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listContainer, propertiesPanel);
        splitPane.setResizeWeight(0.3);
        add(splitPane, BorderLayout.CENTER);

        loadTemplates();
    }

    private void createPropertiesControls() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Color
        gbc.gridx = 0; gbc.gridy = 0;
        propertiesPanel.add(new JLabel("Color:"), gbc);
        gbc.gridx = 1;
        colorButton = createColorPickerButton(Color.WHITE, this::onTemplatePropertyChange);
        propertiesPanel.add(colorButton, gbc);

        // Thickness
        gbc.gridy++;
        gbc.gridx = 0;
        propertiesPanel.add(new JLabel("Thickness:"), gbc);
        gbc.gridx = 1;
        thicknessSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
        thicknessSpinner.addChangeListener(e -> onTemplatePropertyChange(null));
        propertiesPanel.add(thicknessSpinner, gbc);

        // Show Label
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        showLabelCheckBox = new JCheckBox("Show Price Label");
        showLabelCheckBox.addActionListener(e -> onTemplatePropertyChange(null));
        propertiesPanel.add(showLabelCheckBox, gbc);

        // Set Active Button
        gbc.gridy++;
        setActiveButton = new JButton("Set as Active");
        setActiveButton.addActionListener(e -> setActiveTemplate());
        propertiesPanel.add(setActiveButton, gbc);

        // Glue to push everything up
        gbc.gridy++;
        gbc.weighty = 1.0;
        propertiesPanel.add(new JLabel(), gbc);
    }

    private void loadTemplates() {
        listModel.clear();
        List<DrawingToolTemplate> templates = settingsService.getTemplatesForTool(toolClassName);
        if (templates != null) {
            listModel.addAll(templates);
        }
        if (!listModel.isEmpty()) {
            templateList.setSelectedIndex(0);
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            DrawingToolTemplate selected = templateList.getSelectedValue();
            updatePropertiesPanel(selected);
        }
    }

    private void updatePropertiesPanel(DrawingToolTemplate template) {
        boolean isEnabled = template != null;
        colorButton.setEnabled(isEnabled);
        thicknessSpinner.setEnabled(isEnabled);
        showLabelCheckBox.setEnabled(isEnabled);
        setActiveButton.setEnabled(isEnabled);

        if (template != null) {
            colorButton.setBackground(template.color());
            thicknessSpinner.setValue((int)template.stroke().getLineWidth());
            showLabelCheckBox.setSelected(template.showPriceLabel());
            UUID activeId = settingsService.getActiveTemplateId(toolClassName);
            setActiveButton.setEnabled(!template.id().equals(activeId));
        }
    }
    
    private void onTemplatePropertyChange(Color newColor) {
        DrawingToolTemplate selected = templateList.getSelectedValue();
        if (selected == null) return;

        DrawingToolTemplate updatedTemplate = new DrawingToolTemplate(
            selected.id(),
            selected.name(),
            colorButton.getBackground(),
            new BasicStroke((Integer) thicknessSpinner.getValue()),
            showLabelCheckBox.isSelected(),
            selected.specificProps() // Preserve specific properties
        );
        settingsService.updateTemplate(toolClassName, updatedTemplate);
    }

    private void addTemplate() {
        String name = JOptionPane.showInputDialog(this, "Enter template name:", "New Template", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            DrawingToolTemplate defaultTemplate = settingsService.getActiveTemplateForTool(toolClassName);
            DrawingToolTemplate newTemplate = new DrawingToolTemplate(UUID.randomUUID(), name, defaultTemplate.color(), defaultTemplate.stroke(), defaultTemplate.showPriceLabel(), Collections.emptyMap());
            settingsService.addTemplate(toolClassName, newTemplate);
            loadTemplates();
            templateList.setSelectedValue(newTemplate, true);
        }
    }

    private void removeTemplate() {
        DrawingToolTemplate selected = templateList.getSelectedValue();
        if (selected != null) {
            int choice = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete '" + selected.name() + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                settingsService.deleteTemplate(toolClassName, selected.id());
                loadTemplates();
            }
        }
    }
    
    private void renameTemplate() {
        DrawingToolTemplate selected = templateList.getSelectedValue();
        if (selected != null) {
            String newName = (String) JOptionPane.showInputDialog(this, "Enter new name:", "Rename Template", JOptionPane.PLAIN_MESSAGE, null, null, selected.name());
            if (newName != null && !newName.isBlank()) {
                DrawingToolTemplate updated = new DrawingToolTemplate(selected.id(), newName, selected.color(), selected.stroke(), selected.showPriceLabel(), selected.specificProps());
                settingsService.updateTemplate(toolClassName, updated);
                loadTemplates();
                templateList.setSelectedValue(updated, true);
            }
        }
    }
    
    private void setActiveTemplate() {
        DrawingToolTemplate selected = templateList.getSelectedValue();
        if (selected != null) {
            settingsService.setActiveTemplate(toolClassName, selected.id());
            setActiveButton.setEnabled(false);
            templateList.repaint(); // To update the bold font
        }
    }
    
    private JButton createColorPickerButton(Color initialColor, Consumer<Color> onColorUpdate) {
        JButton button = new JButton();
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(100, 25));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        button.addActionListener(e -> {
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), newColor -> {
                button.setBackground(newColor);
                onColorUpdate.accept(newColor);
            });
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }

    private class TemplateListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DrawingToolTemplate template) {
                label.setText(template.name());
                UUID activeId = settingsService.getActiveTemplateId(toolClassName);
                if (template.id().equals(activeId)) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    label.setText(template.name() + " (Active)");
                } else {
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                }
            }
            return label;
        }
    }
}