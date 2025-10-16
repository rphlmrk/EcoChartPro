package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * A modal dialog for configuring and launching a new chart window.
 * It allows the user to select a symbol, timeframe, and mode (Standard or Replay).
 */
public class LaunchOptionsDialog extends JDialog {

    private final JComboBox<ChartDataSource> symbolComboBox;
    private final JComboBox<String> timeframeComboBox;
    private final JRadioButton standardModeRadioButton;
    private final JRadioButton replayModeRadioButton;
    private final JSlider replayStartSlider;
    private final JLabel replaySliderValueLabel;
    private final JButton launchButton;
    private final JButton cancelButton;

    private boolean launched = false;
    private ChartDataSource selectedDataSource;
    private String selectedTimeframe;
    private boolean isReplayMode;
    private int replayStartIndex;

    public LaunchOptionsDialog(Frame owner) {
        super(owner, "Launch Chart Options", true);
        setSize(500, 300);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        setResizable(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Symbol Selection ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel("Symbol:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        symbolComboBox = new JComboBox<>();
        add(symbolComboBox, gbc);

        // --- Timeframe Selection ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        add(new JLabel("Timeframe:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        timeframeComboBox = new JComboBox<>();
        add(timeframeComboBox, gbc);

        // --- Mode Selection ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        add(new JLabel("Mode:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        standardModeRadioButton = new JRadioButton("Standard Charting", true);
        replayModeRadioButton = new JRadioButton("Replay Mode");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(standardModeRadioButton);
        modeGroup.add(replayModeRadioButton);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.add(standardModeRadioButton);
        modePanel.add(replayModeRadioButton);
        add(modePanel, gbc);

        // --- Replay Start Slider ---
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        add(new JLabel("Replay Start:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 1;
        replayStartSlider = new JSlider(0, 100, 50);
        add(replayStartSlider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.2;
        replaySliderValueLabel = new JLabel("Bar 50");
        add(replaySliderValueLabel, gbc);

        // --- Action Buttons ---
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        launchButton = new JButton("Launch Chart");
        cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(launchButton);
        add(buttonPanel, gbc);

        // --- Add Listeners and Populate Data ---
        addListeners();
        populateSymbolComboBox();
        updateReplayControlsState(); // Set initial state
    }

    private void addListeners() {
        symbolComboBox.addActionListener(e -> {
            ChartDataSource source = (ChartDataSource) symbolComboBox.getSelectedItem();
            populateTimeframeComboBox(source);
            // Update the slider range whenever the symbol changes
            updateReplaySliderRange();
        });

        // This listener is now only relevant for Standard Mode
        timeframeComboBox.addActionListener(e -> {});

        standardModeRadioButton.addActionListener(e -> updateReplayControlsState());
        replayModeRadioButton.addActionListener(e -> updateReplayControlsState());

        replayStartSlider.addChangeListener(e -> {
            NumberFormat formatter = NumberFormat.getInstance(Locale.US);
            replaySliderValueLabel.setText("Bar " + formatter.format(replayStartSlider.getValue()));
        });

        launchButton.addActionListener(e -> {
            // Save final selections
            this.launched = true;
            this.selectedDataSource = (ChartDataSource) symbolComboBox.getSelectedItem();
            this.selectedTimeframe = (String) timeframeComboBox.getSelectedItem();
            this.isReplayMode = replayModeRadioButton.isSelected();
            this.replayStartIndex = replayStartSlider.getValue();
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());
    }

    private void populateSymbolComboBox() {
        List<ChartDataSource> sources = DataSourceManager.getInstance().getAvailableSources();
        if (sources.isEmpty()) {
            // [FIX] Added a placeholder string for the missing providerName argument.
            symbolComboBox.addItem(new ChartDataSource("System", "No Data", "No Data Found", null, List.of()));
            launchButton.setEnabled(false);
        } else {
            for (ChartDataSource source : sources) {
                symbolComboBox.addItem(source);
            }
        }
    }

    private void populateTimeframeComboBox(ChartDataSource source) {
        timeframeComboBox.removeAllItems();
        if (source != null && source.timeframes() != null && !source.timeframes().isEmpty()) {
            for (String tf : source.timeframes()) {
                timeframeComboBox.addItem(tf);
            }
            timeframeComboBox.setSelectedIndex(0);
        }
    }

    private void updateReplayControlsState() {
        boolean replayEnabled = replayModeRadioButton.isSelected();
        replayStartSlider.setEnabled(replayEnabled);
        replaySliderValueLabel.setEnabled(replayEnabled);

        // Disable the timeframe selector in replay mode, as the base is always 1m.
        timeframeComboBox.setEnabled(!replayEnabled);
    }

    private void updateReplaySliderRange() {
        ChartDataSource source = (ChartDataSource) symbolComboBox.getSelectedItem();
        if (source == null || source.dbPath() == null) {
            replayStartSlider.setMaximum(0);
            return;
        }

        // In replay mode, the slider range is based on the 1-minute data count.
        DatabaseManager tempDbManager = null;
        try {
            String jdbcUrl = "jdbc:sqlite:" + source.dbPath().toAbsolutePath();
            tempDbManager = new DatabaseManager(jdbcUrl);
            // method call from getKLineCount to getTotalKLineCount ---
            int count = tempDbManager.getTotalKLineCount(new Symbol(source.symbol()), "1m");
            replayStartSlider.setMinimum(0);
            replayStartSlider.setMaximum(Math.max(0, count - 1)); // Max is index, so count - 1
            replayStartSlider.setValue(0); // Reset to beginning
        } catch (Exception e) {
            System.err.println("Could not get 1m kline count for slider: " + e.getMessage());
            replayStartSlider.setMaximum(0);
        } finally {
            if (tempDbManager != null) {
                tempDbManager.close();
            }
        }
    }

    // --- Public Getters for Retrieving User Choices ---

    public boolean isLaunched() {
        return launched;
    }

    public ChartDataSource getSelectedDataSource() {
        return selectedDataSource;
    }

    public String getSelectedTimeframe() {
        return selectedTimeframe;
    }

    public boolean isReplayModeSelected() {
        return isReplayMode;
    }

    public int getReplayStartIndex() {
        return replayStartIndex;
    }
}