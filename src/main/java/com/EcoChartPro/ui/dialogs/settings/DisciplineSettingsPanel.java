package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public class DisciplineSettingsPanel extends JPanel {
    private final SettingsManager sm;

    // --- Components ---
    // Make mainPanel an instance field to be accessible by helper methods
    private JPanel mainPanel;
    private JCheckBox coachEnabledCheckbox;
    private JRadioButton autoTradeCountRadio, manualTradeCountRadio;
    private JSpinner manualTradeCountSpinner;
    private final JCheckBox[] preferredSessionCheckboxes = new JCheckBox[4];
    private final JCheckBox[] peakHourCheckboxes = new JCheckBox[24];
    private JRadioButton autoPeakHoursRadio, manualPeakHoursRadio;
    private JCheckBox overtrainingNudgeCheckbox, fatigueNudgeCheckbox;
    private JCheckBox winStreakNudgeCheckbox, lossStreakNudgeCheckbox;
    private JSpinner fastForwardTimeSpinner;
    private JCheckBox showVisualAidsCheckbox;
    private JRadioButton shadeAreaRadio, indicatorLinesRadio, bottomBarRadio;
    private JSpinner barHeightSpinner;
    private JButton shadeColorButton, startIndicatorColorButton, endIndicatorColorButton;

    public DisciplineSettingsPanel(SettingsManager settingsManager) {
        this.sm = settingsManager;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        initUI();
    }

    private void initUI() {
        // Initialize the instance field instead of a local variable
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        coachEnabledCheckbox = new JCheckBox("Enable Discipline Coach");
        coachEnabledCheckbox.setSelected(sm.isDisciplineCoachEnabled());
        coachEnabledCheckbox.setFont(coachEnabledCheckbox.getFont().deriveFont(Font.BOLD));
        mainPanel.add(coachEnabledCheckbox);

        mainPanel.add(createTradeLimitPanel());
        mainPanel.add(createPreferredSessionsPanel());
        mainPanel.add(createPeakHoursPanel());
        mainPanel.add(createNudgesPanel());
        mainPanel.add(createVisualAidsPanel());

        add(new JScrollPane(mainPanel), BorderLayout.CENTER);

        // Add listener and call the corrected toggle function
        coachEnabledCheckbox.addActionListener(e -> toggleAllControls(coachEnabledCheckbox.isSelected()));
        toggleAllControls(coachEnabledCheckbox.isSelected());
    }

    private JPanel createTradeLimitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Daily Trade Limit"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);

        autoTradeCountRadio = new JRadioButton("Auto-detect optimal trade count (Recommended)");
        manualTradeCountRadio = new JRadioButton("Manually set trade count limit:");
        ButtonGroup group = new ButtonGroup();
        group.add(autoTradeCountRadio);
        group.add(manualTradeCountRadio);

        manualTradeCountSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, sm.getOptimalTradeCountOverride()), 1, 100, 1));

        boolean isManual = sm.getOptimalTradeCountOverride() > -1;
        autoTradeCountRadio.setSelected(!isManual);
        manualTradeCountRadio.setSelected(isManual);
        manualTradeCountSpinner.setEnabled(isManual);
        autoTradeCountRadio.addActionListener(e -> manualTradeCountSpinner.setEnabled(false));
        manualTradeCountRadio.addActionListener(e -> manualTradeCountSpinner.setEnabled(true));

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; panel.add(autoTradeCountRadio, gbc);
        gbc.gridy = 1; gbc.gridwidth = 1; panel.add(manualTradeCountRadio, gbc);
        gbc.gridx = 1; panel.add(manualTradeCountSpinner, gbc);

        return panel;
    }

    private JPanel createPreferredSessionsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Preferred Trading Sessions (for Auto-Tagging)"));
        List<SettingsManager.TradingSession> preferred = sm.getPreferredTradingSessions();
        int i = 0;
        for (SettingsManager.TradingSession session : SettingsManager.TradingSession.values()) {
            preferredSessionCheckboxes[i] = new JCheckBox(session.name());
            preferredSessionCheckboxes[i].setSelected(preferred.contains(session));
            panel.add(preferredSessionCheckboxes[i]);
            i++;
        }
        return panel;
    }
    
    private JPanel createPeakHoursPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Peak Performance Hours (UTC)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;

        autoPeakHoursRadio = new JRadioButton("Auto-detect based on performance (Recommended)");
        manualPeakHoursRadio = new JRadioButton("Manually define peak performance hours");
        ButtonGroup group = new ButtonGroup();
        group.add(autoPeakHoursRadio);
        group.add(manualPeakHoursRadio);
        
        JPanel gridPanel = new JPanel(new GridLayout(4, 6, 5, 5));
        for (int i = 0; i < 24; i++) {
            peakHourCheckboxes[i] = new JCheckBox(String.format("%02d", i));
            peakHourCheckboxes[i].setSelected(sm.getPeakPerformanceHoursOverride().contains(i));
            gridPanel.add(peakHourCheckboxes[i]);
        }
        
        boolean isManual = !sm.getPeakPerformanceHoursOverride().isEmpty();
        manualPeakHoursRadio.setSelected(isManual);
        autoPeakHoursRadio.setSelected(!isManual);
        togglePeakHoursGrid(isManual);

        autoPeakHoursRadio.addActionListener(e -> togglePeakHoursGrid(false));
        manualPeakHoursRadio.addActionListener(e -> togglePeakHoursGrid(true));

        gbc.gridwidth = 2; gbc.gridy = 0; panel.add(autoPeakHoursRadio, gbc);
        gbc.gridy = 1; panel.add(manualPeakHoursRadio, gbc);
        gbc.gridy = 2; panel.add(gridPanel, gbc);

        return panel;
    }
    
    private JPanel createNudgesPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Real-time Nudges"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 2, 2, 2);

        overtrainingNudgeCheckbox = new JCheckBox("Notify when approaching daily trade limit.");
        overtrainingNudgeCheckbox.setSelected(sm.isOvertrainingNudgeEnabled());
        fatigueNudgeCheckbox = new JCheckBox("Notify when trading outside peak hours.");
        fatigueNudgeCheckbox.setSelected(sm.isFatigueNudgeEnabled());
        
        gbc.gridy = 0; panel.add(overtrainingNudgeCheckbox, gbc);
        gbc.gridy = 1; panel.add(fatigueNudgeCheckbox, gbc);
        
        JPanel streakPanel = new JPanel(new GridBagLayout());
        streakPanel.setBorder(BorderFactory.createTitledBorder("In-Session Streak Notifications"));
        winStreakNudgeCheckbox = new JCheckBox("Show 'On Fire' notification for winning streaks (3+ wins).");
        winStreakNudgeCheckbox.setSelected(sm.isWinStreakNudgeEnabled());
        lossStreakNudgeCheckbox = new JCheckBox("Show 'Take a Break' notification for losing streaks (3+ losses).");
        lossStreakNudgeCheckbox.setSelected(sm.isLossStreakNudgeEnabled());
        
        SpinnerDateModel timeModel = new SpinnerDateModel();
        timeModel.setValue(Date.from(sm.getFastForwardTime().atDate(java.time.LocalDate.EPOCH).toInstant(ZoneOffset.UTC)));
        fastForwardTimeSpinner = new JSpinner(timeModel);
        fastForwardTimeSpinner.setEditor(new JSpinner.DateEditor(fastForwardTimeSpinner, "HH:mm"));
        
        gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=2; streakPanel.add(winStreakNudgeCheckbox, gbc);
        gbc.gridy=1; streakPanel.add(lossStreakNudgeCheckbox, gbc);
        gbc.gridy=2; gbc.gridwidth=1; streakPanel.add(new JLabel("Fast forward to (UTC):"), gbc);
        gbc.gridx=1; streakPanel.add(fastForwardTimeSpinner, gbc);
        
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.add(panel);
        container.add(streakPanel);
        return container;
    }
    
    private JPanel createVisualAidsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Visual Aids"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 5, 2, 5);
        
        showVisualAidsCheckbox = new JCheckBox("Visually highlight peak performance hours on the chart");
        showVisualAidsCheckbox.setSelected(sm.isShowPeakHoursLines());
        
        shadeAreaRadio = new JRadioButton("Shade Background Area");
        indicatorLinesRadio = new JRadioButton("Start/End Indicator Lines");
        bottomBarRadio = new JRadioButton("Bar Along Time Axis");
        ButtonGroup group = new ButtonGroup();
        group.add(shadeAreaRadio); group.add(indicatorLinesRadio); group.add(bottomBarRadio);
        
        switch(sm.getPeakHoursDisplayStyle()){
            case SHADE_AREA -> shadeAreaRadio.setSelected(true);
            case INDICATOR_LINES -> indicatorLinesRadio.setSelected(true);
            case BOTTOM_BAR -> bottomBarRadio.setSelected(true);
        }
        
        barHeightSpinner = new JSpinner(new SpinnerNumberModel(sm.getPeakHoursBottomBarHeight(), 1, 20, 1));
        shadeColorButton = createColorButton(sm.getPeakHoursColorShade());
        startIndicatorColorButton = createColorButton(sm.getPeakHoursColorStart());
        endIndicatorColorButton = createColorButton(sm.getPeakHoursColorEnd());
        
        gbc.gridwidth = 4; gbc.gridy = 0; panel.add(showVisualAidsCheckbox, gbc);
        
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(shadeAreaRadio); radioPanel.add(indicatorLinesRadio); radioPanel.add(bottomBarRadio);
        gbc.insets.left = 25; gbc.gridy++; panel.add(radioPanel, gbc);
        
        JPanel propsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc2 = (GridBagConstraints)gbc.clone();
        gbc2.gridwidth = 1;
        
        gbc2.gridx = 0; gbc2.gridy = 0; propsPanel.add(new JLabel("Bar Height:"), gbc2);
        gbc2.gridx++; propsPanel.add(barHeightSpinner, gbc2);
        gbc2.gridx = 0; gbc2.gridy = 1; propsPanel.add(new JLabel("Shade/Bar Color:"), gbc2);
        gbc2.gridx++; propsPanel.add(shadeColorButton, gbc2);
        gbc2.gridx = 0; gbc2.gridy = 2; propsPanel.add(new JLabel("Start Indicator Color:"), gbc2);
        gbc2.gridx++; propsPanel.add(startIndicatorColorButton, gbc2);
        gbc2.gridx = 0; gbc2.gridy = 3; propsPanel.add(new JLabel("End Indicator Color:"), gbc2);
        gbc2.gridx++; propsPanel.add(endIndicatorColorButton, gbc2);
        gbc.gridy++; panel.add(propsPanel, gbc);
        
        return panel;
    }

    public void applyChanges() {
        sm.setDisciplineCoachEnabled(coachEnabledCheckbox.isSelected());
        
        // Trade Limit
        if (autoTradeCountRadio.isSelected()) {
            sm.setOptimalTradeCountOverride(-1);
        } else {
            sm.setOptimalTradeCountOverride((Integer) manualTradeCountSpinner.getValue());
        }
        
        // Preferred Sessions
        List<SettingsManager.TradingSession> preferred = new ArrayList<>();
        for (int i=0; i<4; i++) {
            if (preferredSessionCheckboxes[i].isSelected()) {
                preferred.add(SettingsManager.TradingSession.values()[i]);
            }
        }
        sm.setPreferredTradingSessions(preferred);
        
        // Peak Hours
        if (autoPeakHoursRadio.isSelected()) {
            sm.setPeakPerformanceHoursOverride(new ArrayList<>());
        } else {
            List<Integer> hours = new ArrayList<>();
            for(int i=0; i<24; i++) {
                if (peakHourCheckboxes[i].isSelected()) hours.add(i);
            }
            sm.setPeakPerformanceHoursOverride(hours);
        }
        
        // Nudges
        sm.setOvertrainingNudgeEnabled(overtrainingNudgeCheckbox.isSelected());
        sm.setFatigueNudgeEnabled(fatigueNudgeCheckbox.isSelected());
        sm.setWinStreakNudgeEnabled(winStreakNudgeCheckbox.isSelected());
        sm.setLossStreakNudgeEnabled(lossStreakNudgeCheckbox.isSelected());
        Date fastForwardDate = (Date) fastForwardTimeSpinner.getValue();
        sm.setFastForwardTime(fastForwardDate.toInstant().atZone(ZoneOffset.UTC).toLocalTime());
        
        // Visual Aids
        sm.setShowPeakHoursLines(showVisualAidsCheckbox.isSelected());
        if(shadeAreaRadio.isSelected()) sm.setPeakHoursDisplayStyle(SettingsManager.PeakHoursDisplayStyle.SHADE_AREA);
        else if (indicatorLinesRadio.isSelected()) sm.setPeakHoursDisplayStyle(SettingsManager.PeakHoursDisplayStyle.INDICATOR_LINES);
        else sm.setPeakHoursDisplayStyle(SettingsManager.PeakHoursDisplayStyle.BOTTOM_BAR);
        sm.setPeakHoursBottomBarHeight((Integer)barHeightSpinner.getValue());
        sm.setPeakHoursColorShade(shadeColorButton.getBackground());
        sm.setPeakHoursColorStart(startIndicatorColorButton.getBackground());
        sm.setPeakHoursColorEnd(endIndicatorColorButton.getBackground());
    }
    
    // --- CORRECTED METHOD ---
    private void toggleAllControls(boolean enabled) {
        // Recursively enable/disable all components within a container
        setComponentsEnabled(mainPanel, enabled);
    
        // After the main toggle, apply special logic for controls that
        // might need to be re-enabled or disabled based on other states.
        if (enabled) {
            manualTradeCountSpinner.setEnabled(manualTradeCountRadio.isSelected());
            togglePeakHoursGrid(manualPeakHoursRadio.isSelected());
        }
        
        mainPanel.repaint(); // Repaint to reflect title color changes
    }

    private void setComponentsEnabled(Container container, boolean enabled) {
        for (Component comp : container.getComponents()) {
            // The master checkbox should not be disabled by this process
            if (comp == coachEnabledCheckbox) {
                continue;
            }
            comp.setEnabled(enabled);
            
            if (comp instanceof Container) {
                setComponentsEnabled((Container) comp, enabled);
            }
            
            // Update TitledBorder color
            if (comp instanceof JPanel && ((JPanel) comp).getBorder() instanceof TitledBorder border) {
                border.setTitleColor(enabled ? UIManager.getColor("TitledBorder.titleColor") : UIManager.getColor("Label.disabledForeground"));
            }
        }
    }
    
    private void togglePeakHoursGrid(boolean enabled) {
        for(JCheckBox cb : peakHourCheckboxes) {
            cb.setEnabled(enabled);
        }
    }
    
    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton(" ");
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(80, 25));
        button.addActionListener(e -> {
            Consumer<Color> onColorUpdate = newColor -> button.setBackground(newColor);
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }
}