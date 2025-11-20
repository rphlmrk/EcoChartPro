package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.GeneralConfig;
import com.EcoChartPro.core.settings.config.TradingConfig;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.ui.components.TimezoneListPanel;

import javax.swing.*;
import java.awt.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class GeneralSettingsPanel extends JPanel {
    private final SettingsService settingsService;

    private final JRadioButton darkThemeButton;
    private final JRadioButton lightThemeButton;
    private final JComboBox<String> uiScaleComboBox;
    private final JComboBox<ZoneId> timezoneComboBox;
    private final JComboBox<GeneralConfig.ImageSource> imageSourceComboBox;

    // [NEW] Components for Status Bar Clocks
    private final DefaultListModel<GeneralConfig.StatusBarClock> clockListModel;
    private final JList<GeneralConfig.StatusBarClock> clockList;

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

        uiScaleComboBox = new JComboBox<>(new String[] { "100%", "125%", "150%", "175%", "200%" });
        uiScaleComboBox.setSelectedItem(Math.round(settingsService.getUiScale() * 100) + "%");

        Vector<ZoneId> zoneIds = new Vector<>(ZoneId.getAvailableZoneIds().stream().map(ZoneId::of)
                .sorted(Comparator.comparing(ZoneId::getId)).toList());
        timezoneComboBox = new JComboBox<>(zoneIds);
        timezoneComboBox.setSelectedItem(settingsService.getDisplayZoneId());

        imageSourceComboBox = new JComboBox<>(GeneralConfig.ImageSource.values());
        imageSourceComboBox.setSelectedItem(settingsService.getImageSource());

        // Initialize Clock List
        clockListModel = new DefaultListModel<>();
        List<GeneralConfig.StatusBarClock> clocks = settingsService.getStatusBarClocks();
        if (clocks != null) {
            clocks.forEach(clockListModel::addElement);
        }
        clockList = new JList<>(clockListModel);
        clockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        clockList.setCellRenderer(new ClockListRenderer());

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
        gbc.gridx = 0;
        gbc.gridy++;
        add(new JLabel("UI Scaling (requires restart):"), gbc);
        gbc.gridx++;
        add(uiScaleComboBox, gbc);

        // Timezone
        gbc.gridx = 0;
        gbc.gridy++;
        add(new JLabel("Display Timezone:"), gbc);
        gbc.gridx++;
        add(timezoneComboBox, gbc);

        // Dashboard Image Source
        gbc.gridx = 0;
        gbc.gridy++;
        add(new JLabel("Dashboard Image Source:"), gbc);
        gbc.gridx++;
        add(imageSourceComboBox, gbc);

        // [NEW] Status Bar Clocks Section
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 5, 5, 5);
        add(createSeparator("Status Bar Clocks"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(createClockManagementPanel(), gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    private JPanel createClockManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Active Clocks"));

        JScrollPane scrollPane = new JScrollPane(clockList);
        scrollPane.setPreferredSize(new Dimension(300, 120));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        JButton addCustomButton = new JButton("Add Custom...");
        addCustomButton.addActionListener(e -> showAddCustomClockDialog());

        JButton addSessionButton = new JButton("Add Trading Session...");
        addSessionButton.addActionListener(e -> showAddSessionClockDialog());

        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> {
            int selectedIndex = clockList.getSelectedIndex();
            if (selectedIndex != -1) {
                clockListModel.remove(selectedIndex);
            }
        });

        buttonPanel.add(addCustomButton);
        buttonPanel.add(addSessionButton);
        buttonPanel.add(removeButton);

        // Align buttons to top
        JPanel eastContainer = new JPanel(new BorderLayout());
        eastContainer.add(buttonPanel, BorderLayout.NORTH);
        panel.add(eastContainer, BorderLayout.EAST);

        return panel;
    }

    private void showAddCustomClockDialog() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        // Reuse TimezoneListPanel for selection
        TimezoneListPanel tzPanel = new TimezoneListPanel(ZoneId.systemDefault(), zoneId -> {
            String label = JOptionPane.showInputDialog(this, "Enter label for this clock (e.g., 'TK'):", "Clock Label",
                    JOptionPane.PLAIN_MESSAGE);
            if (label != null && !label.isBlank()) {
                clockListModel.addElement(new GeneralConfig.StatusBarClock(label, zoneId));
            }
            popup.setVisible(false);
        });

        popup.add(tzPanel);
        // Show relative to the add button
        // Since this is inside an ActionListener lambda, we might not have easy access
        // to the button component reference directly without making it final or a
        // field.
        // A simple workaround is to show it at the mouse position or center of dialog.
        // Better: Show it relative to the panel.
        popup.show(this, getWidth() / 2, getHeight() / 2);
    }

    private void showAddSessionClockDialog() {
        JPopupMenu menu = new JPopupMenu();

        for (TradingConfig.TradingSession session : TradingConfig.TradingSession.values()) {
            JMenuItem item = new JMenuItem(session.name());
            item.addActionListener(e -> {
                // Hardcoded mappings for standard session timezones
                ZoneId zoneId;
                String label;
                switch (session) {
                    case NEW_YORK -> {
                        zoneId = ZoneId.of("America/New_York");
                        label = "NY";
                    }
                    case LONDON -> {
                        zoneId = ZoneId.of("Europe/London");
                        label = "LDN";
                    }
                    case ASIA -> {
                        zoneId = ZoneId.of("Asia/Tokyo");
                        label = "TKY";
                    } // Usually Tokyo
                    case SYDNEY -> {
                        zoneId = ZoneId.of("Australia/Sydney");
                        label = "SYD";
                    }
                    default -> {
                        zoneId = ZoneId.of("UTC");
                        label = "UTC";
                    }
                }
                clockListModel.addElement(new GeneralConfig.StatusBarClock(label, zoneId));
            });
            menu.add(item);
        }
        menu.show(this, getWidth() / 2, getHeight() / 2);
    }

    public void applyChanges() {
        // Theme
        ThemeManager.Theme selectedTheme = lightThemeButton.isSelected() ? ThemeManager.Theme.LIGHT
                : ThemeManager.Theme.DARK;
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

        // [NEW] Save Clocks
        List<GeneralConfig.StatusBarClock> newClocks = new ArrayList<>();
        for (int i = 0; i < clockListModel.size(); i++) {
            newClocks.add(clockListModel.get(i));
        }
        settingsService.setStatusBarClocks(newClocks);
    }

    private Component createSeparator(String text) {
        JSeparator separator = new JSeparator();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);

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
        return gbc;
    }

    private static class ClockListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof GeneralConfig.StatusBarClock clock) {
                setText(clock.label() + " (" + clock.zoneId().getId() + ")");
            }
            return this;
        }
    }
}