package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import com.EcoChartPro.utils.DatabaseManager;

public class TradingSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JFormattedTextField commissionField;
    private final JFormattedTextField spreadField;
    private final JCheckBox autoJournalCheckbox;
    private final JCheckBox sessionHighlightCheckbox;
    private final JSpinner candleRetentionSpinner; 

    // Session components
    private final Map<SettingsManager.TradingSession, JCheckBox> sessionEnabledCheckboxes = new EnumMap<>(SettingsManager.TradingSession.class);
    private final Map<SettingsManager.TradingSession, JSpinner> sessionStartSpinners = new EnumMap<>(SettingsManager.TradingSession.class);
    private final Map<SettingsManager.TradingSession, JSpinner> sessionEndSpinners = new EnumMap<>(SettingsManager.TradingSession.class);
    private final Map<SettingsManager.TradingSession, JButton> sessionColorButtons = new EnumMap<>(SettingsManager.TradingSession.class);


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

        // [MODIFIED] Spinner model now starts at -1 to represent "Forever"
        candleRetentionSpinner = new JSpinner(new SpinnerNumberModel(sm.getTradeCandleRetentionMonths(), -1, 120, 1));

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Simulation Settings
        JPanel simulationPanel = new JPanel(new GridBagLayout());
        GridBagConstraints simGbc = createGbc(0,0);
        simGbc.weightx = 0;
        simulationPanel.add(new JLabel("Commission Per Trade:"), simGbc);
        simGbc.gridx++; simGbc.weightx = 1.0; simulationPanel.add(commissionField, simGbc);

        simGbc.gridx = 0; simGbc.gridy++; simGbc.weightx = 0; simulationPanel.add(new JLabel("Simulated Spread (points):"), simGbc);
        simGbc.gridx++; simGbc.weightx = 1.0; simulationPanel.add(spreadField, simGbc);
        
        gbc.gridwidth = 2;
        add(simulationPanel, gbc);

        gbc.gridy++; gbc.insets = new Insets(10,0,2,0); add(autoJournalCheckbox, gbc);
        gbc.gridy++; gbc.insets = new Insets(2,0,2,0); add(sessionHighlightCheckbox, gbc);
        
        // [MODIFIED] Add retention setting with "Clear Now" button
        gbc.gridy++; gbc.insets = new Insets(10,0,2,0); 
        JPanel retentionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        retentionPanel.add(new JLabel("Trade Candle Retention (-1 for forever):"));
        retentionPanel.add(candleRetentionSpinner);
        JButton clearDataButton = new JButton("Clear All Cached Candles Now");
        clearDataButton.addActionListener(e -> clearTradeCandleData());
        retentionPanel.add(clearDataButton);
        add(retentionPanel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(15,0,5,0);
        add(createSeparator("Trading Sessions"), gbc);

        // Session Editor
        gbc.gridy++;
        gbc.insets = new Insets(2,0,2,0);
        add(createSessionEditor(), gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }
    
    // [NEW] Logic for the "Clear Now" button
    private void clearTradeCandleData() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "This will permanently delete all saved candle data for past trades.\n" +
            "This action cannot be undone. Are you sure you want to proceed?",
            "Confirm Data Deletion",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    DatabaseManager.getInstance().clearAllTradeCandles();
                    return null;
                }
                @Override
                protected void done() {
                    JOptionPane.showMessageDialog(TradingSettingsPanel.this, "All cached trade candle data has been deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }.execute();
        }
    }

    private JPanel createSessionEditor() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.CENTER;

        // Header Font
        Font headerFont = panel.getFont().deriveFont(Font.BOLD);

        // Header
        gbc.gridy = 0;
        gbc.gridx = 0; panel.add(createHeaderLabel("Session", headerFont), gbc);
        gbc.gridx = 1; panel.add(createHeaderLabel("Enabled", headerFont), gbc);
        gbc.gridx = 2; panel.add(createHeaderLabel("Start Time (UTC)", headerFont), gbc);
        gbc.gridx = 3; panel.add(createHeaderLabel("End Time (UTC)", headerFont), gbc);
        gbc.gridx = 4; panel.add(createHeaderLabel("Color", headerFont), gbc);

        // Rows
        for(SettingsManager.TradingSession session : SettingsManager.TradingSession.values()) {
            gbc.gridy++;

            // Name
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(session.name()), gbc);
            gbc.anchor = GridBagConstraints.CENTER;

            // Enabled
            gbc.gridx = 1;
            JCheckBox enabledCheck = new JCheckBox();
            enabledCheck.setSelected(sm.getSessionEnabled().get(session));
            panel.add(enabledCheck, gbc);
            sessionEnabledCheckboxes.put(session, enabledCheck);

            // Start Time
            gbc.gridx = 2;
            JSpinner startSpinner = createTimeSpinner(sm.getSessionStartTimes().get(session));
            panel.add(startSpinner, gbc);
            sessionStartSpinners.put(session, startSpinner);

            // End Time
            gbc.gridx = 3;
            JSpinner endSpinner = createTimeSpinner(sm.getSessionEndTimes().get(session));
            panel.add(endSpinner, gbc);
            sessionEndSpinners.put(session, endSpinner);

            // Color
            gbc.gridx = 4;
            JButton colorButton = createColorButton(sm.getSessionColors().get(session));
            panel.add(colorButton, gbc);
            sessionColorButtons.put(session, colorButton);
        }
        return panel;
    }

    private JLabel createHeaderLabel(String text, Font font) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        return label;
    }
    
    private JSpinner createTimeSpinner(LocalTime time) {
        SpinnerDateModel model = new SpinnerDateModel();
        model.setValue(Date.from(time.atDate(java.time.LocalDate.EPOCH).toInstant(ZoneOffset.UTC)));
        JSpinner spinner = new JSpinner(model);
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "HH:mm");
        spinner.setEditor(editor);
        return spinner;
    }

    public void applyChanges() {
        sm.setCommissionPerTrade((BigDecimal) commissionField.getValue());
        sm.setSimulatedSpreadPoints((BigDecimal) spreadField.getValue());
        sm.setAutoJournalOnTradeClose(autoJournalCheckbox.isSelected());
        sm.setSessionHighlightingEnabled(sessionHighlightCheckbox.isSelected());
        sm.setTradeCandleRetentionMonths((Integer) candleRetentionSpinner.getValue());

        for (SettingsManager.TradingSession session : SettingsManager.TradingSession.values()) {
            sm.setSessionEnabled(session, sessionEnabledCheckboxes.get(session).isSelected());
            
            Date startDate = (Date)sessionStartSpinners.get(session).getValue();
            sm.setSessionStartTime(session, startDate.toInstant().atZone(ZoneOffset.UTC).toLocalTime());

            Date endDate = (Date)sessionEndSpinners.get(session).getValue();
            sm.setSessionEndTime(session, endDate.toInstant().atZone(ZoneOffset.UTC).toLocalTime());
            
            sm.setSessionColor(session, sessionColorButtons.get(session).getBackground());
        }
    }

    private Component createSeparator(String text) {
        JSeparator separator = new JSeparator();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 5, 5);
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

    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton(" ");
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(80, 25));
        button.addActionListener(e -> {
            Consumer<Color> onColorUpdate = newColor -> button.setBackground(newColor);
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
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