package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * [MODIFIED] A modal dialog for configuring and launching a new chart window.
 * It is now context-aware and simplifies its UI based on whether the user
 * intends to start a Replay or a Live paper trading session.
 */
public class SessionDialog extends JDialog {

    // --- NEW: Context-aware Session Mode ---
    public enum SessionMode { REPLAY, LIVE_PAPER_TRADING }

    private final JComboBox<ChartDataSource> symbolComboBox;
    private final JRadioButton standardModeRadioButton;
    private final JRadioButton replayModeRadioButton;
    private final JSlider replayStartSlider;
    private final JLabel replaySliderValueLabel;
    private final JButton launchButton;
    private final JButton cancelButton;
    private final JSpinner startingBalanceSpinner;
    private final JSpinner leverageSpinner;
    private final JLabel replayStartLabel;

    private boolean launched = false;
    private ChartDataSource selectedDataSource;
    private SessionMode sessionMode;
    private int replayStartIndex;
    private BigDecimal startingBalance;
    private BigDecimal leverage;

    public SessionDialog(Frame owner, SessionMode mode) {
        super(owner, "Launch Chart Options", true);
        this.sessionMode = mode;
        setSize(500, 320);
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        setResizable(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Symbol Selection ---
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel("Symbol:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        symbolComboBox = new JComboBox<>();
        add(symbolComboBox, gbc);

        // --- Account Settings ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        add(new JLabel("Starting Balance ($):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        startingBalanceSpinner = new JSpinner(new SpinnerNumberModel(100000.0, 100.0, 10000000.0, 1000.0));
        add(startingBalanceSpinner, gbc);

        gbc.gridy++; gbc.gridx = 0;
        add(new JLabel("Leverage:"), gbc);
        gbc.gridx = 1;
        leverageSpinner = new JSpinner(new SpinnerNumberModel(1.0, 1.0, 100.0, 1.0));
        add(leverageSpinner, gbc);
        gbc.gridx = 2; add(new JLabel("x"), gbc);

        // --- Mode Selection (Hidden based on context) ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        add(new JLabel("Mode:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        standardModeRadioButton = new JRadioButton("Live Paper Trading", true);
        replayModeRadioButton = new JRadioButton("Replay Mode");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(standardModeRadioButton);
        modeGroup.add(replayModeRadioButton);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.setOpaque(false);
        modePanel.add(standardModeRadioButton);
        modePanel.add(replayModeRadioButton);
        add(modePanel, gbc);

        // --- Replay Start Slider ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1;
        replayStartLabel = new JLabel("Replay Start:");
        add(replayStartLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 1;
        replayStartSlider = new JSlider(0, 100, 0);
        add(replayStartSlider, gbc);
        gbc.gridx = 2; gbc.weightx = 0.2;
        replaySliderValueLabel = new JLabel("Bar 0");
        add(replaySliderValueLabel, gbc);

        // --- Action Buttons ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.CENTER;
        launchButton = new JButton("Launch");
        cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(launchButton);
        add(buttonPanel, gbc);

        addListeners();
        populateSymbolComboBox();
        configureForMode(mode);
    }

    private void configureForMode(SessionMode mode) {
        if (mode == SessionMode.REPLAY) {
            setTitle("Replay Session Options");
            standardModeRadioButton.setVisible(false);
            replayModeRadioButton.setSelected(true);
            replayModeRadioButton.setVisible(false);
            updateReplayControlsState();
        } else { // LIVE_PAPER_TRADING Mode
            setTitle("Live Session Options");
            replayModeRadioButton.setVisible(false);
            standardModeRadioButton.setSelected(true);
            standardModeRadioButton.setVisible(false);
            updateReplayControlsState();

            // Visually hide the replay controls for a cleaner UI
            replayStartLabel.setVisible(false);
            replayStartSlider.setVisible(false);
            replaySliderValueLabel.setVisible(false);
        }
    }

    private void addListeners() {
        symbolComboBox.addActionListener(e -> updateReplaySliderRange());

        standardModeRadioButton.addActionListener(e -> updateReplayControlsState());
        replayModeRadioButton.addActionListener(e -> updateReplayControlsState());

        replayStartSlider.addChangeListener(e -> {
            NumberFormat formatter = NumberFormat.getInstance(Locale.US);
            replaySliderValueLabel.setText("Bar " + formatter.format(replayStartSlider.getValue()));
        });

        launchButton.addActionListener(e -> {
            this.launched = true;
            this.selectedDataSource = (ChartDataSource) symbolComboBox.getSelectedItem();
            this.sessionMode = replayModeRadioButton.isSelected() ? SessionMode.REPLAY : SessionMode.LIVE_PAPER_TRADING;
            this.replayStartIndex = replayStartSlider.getValue();
            this.startingBalance = BigDecimal.valueOf((Double) startingBalanceSpinner.getValue());
            this.leverage = BigDecimal.valueOf((Double) leverageSpinner.getValue());
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());
    }

    private void populateSymbolComboBox() {
        List<ChartDataSource> sources = DataSourceManager.getInstance().getAvailableSources();
        if (sources.isEmpty()) {
            symbolComboBox.addItem(new ChartDataSource("No Data", "No Data Found", null, List.of()));
            launchButton.setEnabled(false);
        } else {
            for (ChartDataSource source : sources) {
                symbolComboBox.addItem(source);
            }
        }
    }

    private void updateReplayControlsState() {
        boolean replayEnabled = replayModeRadioButton.isSelected();
        replayStartSlider.setEnabled(replayEnabled);
        replaySliderValueLabel.setEnabled(replayEnabled);
        replayStartLabel.setEnabled(replayEnabled);
    }

    private void updateReplaySliderRange() {
        ChartDataSource source = (ChartDataSource) symbolComboBox.getSelectedItem();
        if (source == null || source.dbPath() == null) {
            replayStartSlider.setMaximum(0);
            return;
        }

        try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            int count = tempDbManager.getTotalKLineCount(new Symbol(source.symbol()), "1m");
            replayStartSlider.setMinimum(0);
            replayStartSlider.setMaximum(Math.max(0, count - 1));
            replayStartSlider.setValue(0);
        } catch (Exception e) {
            System.err.println("Could not get 1m kline count for slider: " + e.getMessage());
            replayStartSlider.setMaximum(0);
        }
    }

    public boolean isLaunched() { return launched; }
    public ChartDataSource getSelectedDataSource() { return selectedDataSource; }
    public SessionMode getSessionMode() { return sessionMode; }
    public int getReplayStartIndex() { return replayStartIndex; }
    public BigDecimal getStartingBalance() { return startingBalance; }
    public BigDecimal getLeverage() { return leverage; }
}