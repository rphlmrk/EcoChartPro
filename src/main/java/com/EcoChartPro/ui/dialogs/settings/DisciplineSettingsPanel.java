package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;

import javax.swing.*;
import java.awt.*;

public class DisciplineSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JCheckBox coachEnabledCheckbox;
    private final JCheckBox overtrainingNudgeCheckbox;
    private final JCheckBox fatigueNudgeCheckbox;
    private final JCheckBox winStreakNudgeCheckbox;
    private final JCheckBox lossStreakNudgeCheckbox;
    private final JCheckBox showPeakHoursCheckbox;
    private final JComboBox<SettingsManager.PeakHoursDisplayStyle> peakHoursStyleComboBox;

    public DisciplineSettingsPanel(SettingsManager settingsManager) {
        this.sm = settingsManager;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize components
        coachEnabledCheckbox = new JCheckBox("Enable Discipline Coach & Nudges");
        coachEnabledCheckbox.setSelected(sm.isDisciplineCoachEnabled());

        overtrainingNudgeCheckbox = new JCheckBox("Overtraining");
        overtrainingNudgeCheckbox.setSelected(sm.isOvertrainingNudgeEnabled());
        fatigueNudgeCheckbox = new JCheckBox("Fatigue");
        fatigueNudgeCheckbox.setSelected(sm.isFatigueNudgeEnabled());
        winStreakNudgeCheckbox = new JCheckBox("Win Streak (Euphoria)");
        winStreakNudgeCheckbox.setSelected(sm.isWinStreakNudgeEnabled());
        lossStreakNudgeCheckbox = new JCheckBox("Loss Streak (Tilt)");
        lossStreakNudgeCheckbox.setSelected(sm.isLossStreakNudgeEnabled());
        
        showPeakHoursCheckbox = new JCheckBox("Show Peak Performance Hours on Chart");
        showPeakHoursCheckbox.setSelected(sm.isShowPeakHoursLines());
        
        peakHoursStyleComboBox = new JComboBox<>(SettingsManager.PeakHoursDisplayStyle.values());
        peakHoursStyleComboBox.setSelectedItem(sm.getPeakHoursDisplayStyle());
        
        // Link nudge checkboxes to the main one
        coachEnabledCheckbox.addActionListener(e -> updateNudgeCheckboxes());
        updateNudgeCheckboxes();

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);
        gbc.gridwidth = 2;
        
        add(coachEnabledCheckbox, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 25, 5, 5); // Indent
        JPanel nudgePanel = new JPanel(new GridLayout(2, 2, 10, 2));
        nudgePanel.add(overtrainingNudgeCheckbox);
        nudgePanel.add(fatigueNudgeCheckbox);
        nudgePanel.add(winStreakNudgeCheckbox);
        nudgePanel.add(lossStreakNudgeCheckbox);
        add(nudgePanel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(10, 5, 5, 5); // Reset indent
        add(showPeakHoursCheckbox, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 25, 5, 5);
        add(new JLabel("Display Style:"), gbc);
        gbc.gridx++;
        add(peakHoursStyleComboBox, gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }
    
    private void updateNudgeCheckboxes() {
        boolean enabled = coachEnabledCheckbox.isSelected();
        overtrainingNudgeCheckbox.setEnabled(enabled);
        fatigueNudgeCheckbox.setEnabled(enabled);
        winStreakNudgeCheckbox.setEnabled(enabled);
        lossStreakNudgeCheckbox.setEnabled(enabled);
    }

    public void applyChanges() {
        sm.setDisciplineCoachEnabled(coachEnabledCheckbox.isSelected());
        sm.setOvertrainingNudgeEnabled(overtrainingNudgeCheckbox.isSelected());
        sm.setFatigueNudgeEnabled(fatigueNudgeCheckbox.isSelected());
        sm.setWinStreakNudgeEnabled(winStreakNudgeCheckbox.isSelected());
        sm.setLossStreakNudgeEnabled(lossStreakNudgeCheckbox.isSelected());
        sm.setShowPeakHoursLines(showPeakHoursCheckbox.isSelected());
        sm.setPeakHoursDisplayStyle((SettingsManager.PeakHoursDisplayStyle) peakHoursStyleComboBox.getSelectedItem());
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