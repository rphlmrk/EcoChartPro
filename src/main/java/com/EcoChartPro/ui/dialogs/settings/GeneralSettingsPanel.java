package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.GeneralConfig;
import com.EcoChartPro.core.theme.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

public class GeneralSettingsPanel extends JPanel {
    private final SettingsService settingsService;

    private final JRadioButton darkThemeButton;
    private final JRadioButton lightThemeButton;
    private final JComboBox<String> uiScaleComboBox;
    private final JComboBox<ZoneId> timezoneComboBox;
    private final JComboBox<GeneralConfig.ImageSource> imageSourceComboBox;

    public GeneralSettingsPanel(SettingsService settingsService) {
        this.settingsService = settingsService;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize components
        ThemeManager.Theme currentTheme = settingsService.getCurrentTheme();
        darkThemeButton = new JRadioButton("Dark");
        darkThemeButton.setSelected(currentTheme == ThemeManager.Theme.DARK);
        lightThemeButton = new JRadioButton("Light");
        lightThemeButton.setSelected(currentTheme == ThemeManager.Theme.LIGHT);
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(darkThemeButton);
        themeGroup.add(lightThemeButton);

        uiScaleComboBox = new JComboBox<>(new String[]{"100%", "125%", "150%", "175%", "200%"});
        uiScaleComboBox.setSelectedItem(Math.round(settingsService.getUiScale() * 100) + "%");

        Vector<ZoneId> zoneIds = new Vector<>(ZoneId.getAvailableZoneIds().stream().map(ZoneId::of).sorted(Comparator.comparing(ZoneId::getId)).toList());
        timezoneComboBox = new JComboBox<>(zoneIds);
        timezoneComboBox.setSelectedItem(settingsService.getDisplayZoneId());

        imageSourceComboBox = new JComboBox<>(GeneralConfig.ImageSource.values());
        imageSourceComboBox.setSelectedItem(settingsService.getImageSource());

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);

        // Theme
        add(new JLabel("Application Theme:"), gbc);
        gbc.gridx++;
        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        themePanel.add(darkThemeButton);
        themePanel.add(lightThemeButton);
        add(themePanel, gbc);

        // UI Scale
        gbc.gridx = 0; gbc.gridy++;
        add(new JLabel("UI Scaling (requires restart):"), gbc);
        gbc.gridx++;
        add(uiScaleComboBox, gbc);

        // Timezone
        gbc.gridx = 0; gbc.gridy++;
        add(new JLabel("Display Timezone:"), gbc);
        gbc.gridx++;
        add(timezoneComboBox, gbc);
        
        // Dashboard Image Source
        gbc.gridx = 0; gbc.gridy++;
        add(new JLabel("Dashboard Image Source:"), gbc);
        gbc.gridx++;
        add(imageSourceComboBox, gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    public void applyChanges() {
        // Theme
        ThemeManager.Theme selectedTheme = lightThemeButton.isSelected() ? ThemeManager.Theme.LIGHT : ThemeManager.Theme.DARK;
        if (selectedTheme != settingsService.getCurrentTheme()) {
            settingsService.setCurrentTheme(selectedTheme);
        }

        // UI Scale
        String scaleStr = (String) uiScaleComboBox.getSelectedItem();
        float newScale = Float.parseFloat(scaleStr.replace("%", "")) / 100.0f;
        if (newScale != settingsService.getUiScale()) {
            settingsService.setUiScale(newScale);
            JOptionPane.showMessageDialog(this,
                "UI scaling changes will take effect the next time you start the application.",
                "Restart Required",
                JOptionPane.INFORMATION_MESSAGE);
        }
        
        // Timezone
        settingsService.setDisplayZoneId((ZoneId) timezoneComboBox.getSelectedItem());
        
        // Image Source
        settingsService.setImageSource((GeneralConfig.ImageSource) imageSourceComboBox.getSelectedItem());
    }

    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        return gbc;
    }
}