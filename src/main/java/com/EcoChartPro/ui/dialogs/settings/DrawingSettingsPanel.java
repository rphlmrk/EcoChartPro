package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;

import javax.swing.*;
import java.awt.*;

public class DrawingSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JSpinner snapRadiusSpinner;
    private final JSpinner hitThresholdSpinner;
    private final JSpinner handleSizeSpinner;
    private final JComboBox<SettingsManager.ToolbarPosition> toolbarPositionComboBox;

    public DrawingSettingsPanel(SettingsManager settingsManager) {
        this.sm = settingsManager;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize components
        snapRadiusSpinner = new JSpinner(new SpinnerNumberModel(sm.getSnapRadius(), 0, 50, 1));
        hitThresholdSpinner = new JSpinner(new SpinnerNumberModel(sm.getDrawingHitThreshold(), 1, 30, 1));
        handleSizeSpinner = new JSpinner(new SpinnerNumberModel(sm.getDrawingHandleSize(), 4, 20, 1));
        toolbarPositionComboBox = new JComboBox<>(SettingsManager.ToolbarPosition.values());
        toolbarPositionComboBox.setSelectedItem(sm.getDrawingToolbarPosition());

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);

        // Drawing Interaction
        add(new JLabel("Snap-to-Price Radius (pixels):"), gbc);
        gbc.gridx++; add(snapRadiusSpinner, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Object Hit Threshold (pixels):"), gbc);
        gbc.gridx++; add(hitThresholdSpinner, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Object Handle Size (pixels):"), gbc);
        gbc.gridx++; add(handleSizeSpinner, gbc);
        
        gbc.gridy++; add(createSeparator("Drawing Toolbar"), gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Toolbar Position:"), gbc);
        gbc.gridx++; add(toolbarPositionComboBox, gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    public void applyChanges() {
        sm.setSnapRadius((Integer) snapRadiusSpinner.getValue());
        sm.setDrawingHitThreshold((Integer) hitThresholdSpinner.getValue());
        sm.setDrawingHandleSize((Integer) handleSizeSpinner.getValue());
        sm.setDrawingToolbarPosition((SettingsManager.ToolbarPosition) toolbarPositionComboBox.getSelectedItem());
    }

    private Component createSeparator(String text) {
        JSeparator separator = new JSeparator();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(text), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(separator, gbc);
        return panel;
    }
    
    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }
}