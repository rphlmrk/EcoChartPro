package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.model.Timeframe;

import javax.swing.*;
import java.awt.*;

public class CustomTimeframeDialog extends JDialog {

    private Timeframe customTimeframe = null;
    private final JSpinner valueSpinner;
    private final JComboBox<String> unitComboBox;

    // constructor to accept Frame, as the ultimate owner should be a top-level Frame.
    public CustomTimeframeDialog(Frame owner) {
        super(owner, "Custom Timeframe", true);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Input Panel ---
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Value Spinner
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Value:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(7, 1, 999, 1);
        valueSpinner = new JSpinner(spinnerModel);
        inputPanel.add(valueSpinner, gbc);

        // Unit ComboBox
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Unit:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        String[] units = {"Minutes", "Hours", "Days"};
        unitComboBox = new JComboBox<>(units);
        inputPanel.add(unitComboBox, gbc);

        // --- Button Panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        // --- Action Listeners ---
        okButton.addActionListener(e -> {
            int value = (int) valueSpinner.getValue();
            String selectedUnit = (String) unitComboBox.getSelectedItem();
            char unitChar = 'm'; // default
            if (selectedUnit != null) {
                unitChar = Character.toLowerCase(selectedUnit.charAt(0));
            }
            
            Timeframe.of(value, unitChar).ifPresent(tf -> this.customTimeframe = tf);

            dispose();
        });

        cancelButton.addActionListener(e -> {
            this.customTimeframe = null;
            dispose();
        });
        
        getRootPane().setDefaultButton(okButton);

        // --- Assemble Dialog ---
        add(inputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    public Timeframe getCustomTimeframe() {
        return customTimeframe;
    }
}