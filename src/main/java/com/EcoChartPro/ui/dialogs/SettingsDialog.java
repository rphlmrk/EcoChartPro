package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.settings.ChecklistManager;
import com.EcoChartPro.core.settings.MistakeManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.ui.dialogs.settings.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main dialog window for application settings, organized into tabs.
 */
public class SettingsDialog extends JDialog {

    private final JTabbedPane tabbedPane;
    private final List<JPanel> settingPanels = new ArrayList<>();

    public SettingsDialog(Frame owner) {
        super(owner, "Settings", true);
        
        SettingsManager settingsManager = SettingsManager.getInstance();
        MistakeManager mistakeManager = MistakeManager.getInstance();
        
        tabbedPane = new JTabbedPane();

        // Create and add each settings panel
        addPanel("General", new GeneralSettingsPanel(settingsManager));
        addPanel("Chart", new ChartSettingsPanel(settingsManager));
        addPanel("Drawing", new DrawingSettingsPanel(settingsManager));
        addPanel("Trading & Sessions", new TradingSettingsPanel(settingsManager));
        addPanel("Discipline Coach", new DisciplineSettingsPanel(settingsManager));
        addPanel("Volume Profile", new VolumeProfileSettingsPanel(settingsManager));
        addPanel("Drawing Templates", new TemplatesSettingsPanel(settingsManager));
        addPanel("Mistake Library", new MistakesSettingsPanel(mistakeManager));
        // Removed Checklist Panel as it's managed in the sidebar

        initUI();
    }
    
    private void addPanel(String title, JPanel panel) {
        tabbedPane.addTab(title, panel);
        settingPanels.add(panel);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        // --- Button Panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelButton = new JButton("Cancel");
        JButton applyButton = new JButton("Apply");

        cancelButton.addActionListener(e -> dispose());
        applyButton.addActionListener(e -> {
            applyAllChanges();
            dispose();
        });
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(750, 600));
        setLocationRelativeTo(getOwner());
    }

    private void applyAllChanges() {
        for (JPanel panel : settingPanels) {
            if (panel instanceof GeneralSettingsPanel p) p.applyChanges();
            else if (panel instanceof ChartSettingsPanel p) p.applyChanges();
            else if (panel instanceof DrawingSettingsPanel p) p.applyChanges();
            else if (panel instanceof TradingSettingsPanel p) p.applyChanges();
            else if (panel instanceof DisciplineSettingsPanel p) p.applyChanges();
            else if (panel instanceof VolumeProfileSettingsPanel p) p.applyChanges();
            else if (panel instanceof TemplatesSettingsPanel p) p.applyChanges();
            else if (panel instanceof MistakesSettingsPanel p) p.applyChanges();
        }
    }
}