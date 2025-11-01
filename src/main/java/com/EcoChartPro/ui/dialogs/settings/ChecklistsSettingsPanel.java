package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.ChecklistManager;

import javax.swing.*;
import java.awt.*;

// Placeholder panel for future implementation of checklist management
public class ChecklistsSettingsPanel extends JPanel {

    public ChecklistsSettingsPanel(ChecklistManager checklistManager) {
        setLayout(new BorderLayout());
        JLabel placeholder = new JLabel("Checklist Management UI to be implemented here.", SwingConstants.CENTER);
        placeholder.setEnabled(false);
        add(placeholder, BorderLayout.CENTER);
    }
    
    public void applyChanges() {
        // No-op for now
    }
}