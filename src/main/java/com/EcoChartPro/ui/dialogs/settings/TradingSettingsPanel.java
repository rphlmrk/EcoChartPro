package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class TradingSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JFormattedTextField commissionField;
    private final JFormattedTextField spreadField;
    private final JCheckBox autoJournalCheckbox;
    private final JCheckBox sessionHighlightCheckbox;

    public TradingSettingsPanel(SettingsManager settingsManager) {
        this.sm = settingsManager;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Formatter for decimal fields
        NumberFormatter formatter = new NumberFormatter(new DecimalFormat("#0.00"));
        formatter.setValueClass(BigDecimal.class);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        // Initialize components
        commissionField = new JFormattedTextField(formatter);
        commissionField.setValue(sm.getCommissionPerTrade());
        commissionField.setColumns(8);

        spreadField = new JFormattedTextField(formatter);
        spreadField.setValue(sm.getSimulatedSpreadPoints());
        spreadField.setColumns(8);

        autoJournalCheckbox = new JCheckBox("Automatically open journal entry on trade close");
        autoJournalCheckbox.setSelected(sm.isAutoJournalOnTradeClose());

        sessionHighlightCheckbox = new JCheckBox("Enable session highlighting on chart");
        sessionHighlightCheckbox.setSelected(sm.isSessionHighlightingEnabled());

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);

        // Simulation Settings
        add(new JLabel("Commission Per Trade:"), gbc);
        gbc.gridx++; add(commissionField, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Simulated Spread (points):"), gbc);
        gbc.gridx++; add(spreadField, gbc);

        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2; add(autoJournalCheckbox, gbc);
        
        gbc.gridy++; add(sessionHighlightCheckbox, gbc);

        // TODO: Add controls for editing session times and colors here later

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    public void applyChanges() {
        sm.setCommissionPerTrade((BigDecimal) commissionField.getValue());
        sm.setSimulatedSpreadPoints((BigDecimal) spreadField.getValue());
        sm.setAutoJournalOnTradeClose(autoJournalCheckbox.isSelected());
        sm.setSessionHighlightingEnabled(sessionHighlightCheckbox.isSelected());
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