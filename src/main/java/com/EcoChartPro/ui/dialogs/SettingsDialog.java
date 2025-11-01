package com.EcoChartPro.ui.dialogs;

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
        
        tabbedPane = new JTabbedPane();

        // Create and add each settings panel
        GeneralSettingsPanel generalPanel = new GeneralSettingsPanel(settingsManager);
        tabbedPane.addTab("General", generalPanel);
        settingPanels.add(generalPanel);

        ChartSettingsPanel chartPanel = new ChartSettingsPanel(settingsManager);
        tabbedPane.addTab("Chart", chartPanel);
        settingPanels.add(chartPanel);
        
        DrawingSettingsPanel drawingPanel = new DrawingSettingsPanel(settingsManager);
        tabbedPane.addTab("Drawing", drawingPanel);
        settingPanels.add(drawingPanel);
        
        TradingSettingsPanel tradingPanel = new TradingSettingsPanel(settingsManager);
        tabbedPane.addTab("Trading & Sessions", tradingPanel);
        settingPanels.add(tradingPanel);
        
        DisciplineSettingsPanel disciplinePanel = new DisciplineSettingsPanel(settingsManager);
        tabbedPane.addTab("Discipline Coach", disciplinePanel);
        settingPanels.add(disciplinePanel);
        
        VolumeProfileSettingsPanel vpPanel = new VolumeProfileSettingsPanel(settingsManager);
        tabbedPane.addTab("Volume Profile", vpPanel);
        settingPanels.add(vpPanel);

        initUI();
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
        setMinimumSize(new Dimension(600, 450));
        setLocationRelativeTo(getOwner());
    }

    private void applyAllChanges() {
        for (JPanel panel : settingPanels) {
            if (panel instanceof GeneralSettingsPanel p) p.applyChanges();
            if (panel instanceof ChartSettingsPanel p) p.applyChanges();
            if (panel instanceof DrawingSettingsPanel p) p.applyChanges();
            if (panel instanceof TradingSettingsPanel p) p.applyChanges();
            if (panel instanceof DisciplineSettingsPanel p) p.applyChanges();
            if (panel instanceof VolumeProfileSettingsPanel p) p.applyChanges();
        }
    }
}