package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.tools.DataImportTool;
import com.EcoChartPro.ui.components.CalendarPanel;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * [MODIFIED] A modal dialog for configuring and launching a new chart window.
 * It is now context-aware and simplifies its UI based on whether the user
 * intends to start a Replay or a Live paper trading session. It also includes
 * controls for data import and symbol renaming in replay mode.
 */
public class SessionDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(SessionDialog.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    public enum SessionMode { REPLAY, LIVE_PAPER_TRADING }

    private final JComboBox<String> providerComboBox;
    private final JComboBox<ChartDataSource> symbolComboBox;
    private final JRadioButton standardModeRadioButton;
    private final JRadioButton replayModeRadioButton;
    private final CalendarPanel calendarPanel;
    private final JButton launchButton;
    private final JButton cancelButton;
    private final JSpinner startingBalanceSpinner;
    private final JSpinner leverageSpinner;
    private final JLabel replayStartLabel;
    private final JButton importFromFileButton;
    private final JLabel importStatusLabel;
    private final JProgressBar importProgressBar;
    private final JPanel dataRangePanel;
    private final JLabel dataStartsLabel;
    private final JLabel dataEndsLabel;
    private final JPanel statusBarPanel;


    private boolean launched = false;
    private ChartDataSource selectedDataSource;
    private SessionMode sessionMode;
    private int replayStartIndex;
    private BigDecimal startingBalance;
    private BigDecimal leverage;

    public SessionDialog(Frame owner, SessionMode mode) {
        super(owner, "Launch Chart Options", true);
        this.sessionMode = mode;
        setSize(500, 720); // Adjusted height
        setLocationRelativeTo(owner);
        setLayout(new GridBagLayout());
        setResizable(false);
        
        // --- Initialize all final fields at the top of the constructor ---
        providerComboBox = new JComboBox<>();
        symbolComboBox = new JComboBox<>();
        standardModeRadioButton = new JRadioButton("Live Paper Trading", true);
        replayModeRadioButton = new JRadioButton("Replay Mode");
        launchButton = new JButton("Launch");
        cancelButton = new JButton("Cancel");
        calendarPanel = new CalendarPanel(selectedDate -> launchButton.setEnabled(true));
        startingBalanceSpinner = new JSpinner(new SpinnerNumberModel(100000.0, 100.0, 10000000.0, 1000.0));
        leverageSpinner = new JSpinner(new SpinnerNumberModel(1.0, 1.0, 100.0, 1.0));
        replayStartLabel = new JLabel("Replay Start:");
        importFromFileButton = new JButton("Import Data...");
        statusBarPanel = new JPanel(new BorderLayout(5, 0));
        importStatusLabel = new JLabel("Ready.");
        importProgressBar = new JProgressBar(0, 100);
        dataRangePanel = new JPanel(new GridLayout(1, 4, 10, 0));
        dataStartsLabel = new JLabel("--");
        dataEndsLabel = new JLabel("--");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Provider/Exchange Selection ---
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel("Exchange:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        add(providerComboBox, gbc);

        // --- Symbol Selection ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.EAST;
        add(new JLabel("Symbol:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        add(symbolComboBox, gbc);

        // --- Account Settings ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        add(new JLabel("Starting Balance ($):"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        add(startingBalanceSpinner, gbc);

        gbc.gridy++; gbc.gridx = 0;
        add(new JLabel("Leverage:"), gbc);
        gbc.gridx = 1;
        add(leverageSpinner, gbc);
        gbc.gridx = 2; add(new JLabel("x"), gbc);
        
        // --- Data Range Display ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        dataRangePanel.add(new JLabel("Data Starts:", SwingConstants.RIGHT));
        dataRangePanel.add(dataStartsLabel);
        dataRangePanel.add(new JLabel("Data Ends:", SwingConstants.RIGHT));
        dataRangePanel.add(dataEndsLabel);
        add(dataRangePanel, gbc);

        // --- Mode Selection (Hidden based on context) ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 1; gbc.weightx = 0.0;
        add(new JLabel("Mode:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(standardModeRadioButton);
        modeGroup.add(replayModeRadioButton);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.setOpaque(false);
        modePanel.add(standardModeRadioButton);
        modePanel.add(replayModeRadioButton);
        add(modePanel, gbc);

        // --- Replay Start Label (full width) ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        add(replayStartLabel, gbc);

        // --- Replay Start Calendar ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        add(calendarPanel, gbc);

        // --- Status Bar ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.weighty = 0.0;
        importProgressBar.setStringPainted(true);
        importProgressBar.setVisible(false);
        statusBarPanel.add(importStatusLabel, BorderLayout.CENTER);
        statusBarPanel.add(importProgressBar, BorderLayout.EAST);
        add(statusBarPanel, gbc);

        // --- Action Buttons ---
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.EAST;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importFromFileButton.setToolTipText("Select a CSV file to import from your computer.");
        buttonPanel.add(importFromFileButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(launchButton);
        add(buttonPanel, gbc);

        addListeners();
        populateProviderComboBox();
        configureForMode(mode);
    }

    private void configureForMode(SessionMode mode) {
        if (mode == SessionMode.REPLAY) {
            setTitle("Replay Session Options");
            standardModeRadioButton.setVisible(false);
            replayModeRadioButton.setSelected(true);
            replayModeRadioButton.setVisible(false);
            dataRangePanel.setVisible(true);
            importFromFileButton.setVisible(true);
            statusBarPanel.setVisible(true);
            updateReplayControlsState();
        } else { // LIVE_PAPER_TRADING Mode
            setTitle("Live Session Options");
            replayModeRadioButton.setVisible(false);
            standardModeRadioButton.setSelected(true);
            standardModeRadioButton.setVisible(false);
            dataRangePanel.setVisible(false);
            importFromFileButton.setVisible(false);
            statusBarPanel.setVisible(false);
            updateReplayControlsState();
            replayStartLabel.setVisible(false);
            calendarPanel.setVisible(false);
            // Adjust size for the simpler live view
            setSize(500, 320);
        }
    }

    private void addListeners() {
        providerComboBox.addActionListener(e -> populateSymbolComboBox());
        symbolComboBox.addActionListener(e -> updateCalendarRange());
        standardModeRadioButton.addActionListener(e -> updateReplayControlsState());
        replayModeRadioButton.addActionListener(e -> updateReplayControlsState());

        importFromFileButton.addActionListener(e -> handleImportFromFileAction());

        launchButton.addActionListener(e -> {
            this.launched = true;
            this.selectedDataSource = (ChartDataSource) symbolComboBox.getSelectedItem();
            this.sessionMode = replayModeRadioButton.isSelected() ? SessionMode.REPLAY : SessionMode.LIVE_PAPER_TRADING;
            
            if (this.sessionMode == SessionMode.REPLAY && selectedDataSource != null) {
                LocalDate selectedDate = calendarPanel.getSelectedDate();
                if (selectedDate != null) {
                    try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + selectedDataSource.dbPath().toAbsolutePath())) {
                        this.replayStartIndex = db.findClosestTimestampIndex(
                            new Symbol(selectedDataSource.symbol()), "1m", selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
                        );
                    }
                } else {
                    this.replayStartIndex = 0;
                }
            }
            
            this.startingBalance = BigDecimal.valueOf((Double) startingBalanceSpinner.getValue());
            this.leverage = BigDecimal.valueOf((Double) leverageSpinner.getValue());
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());
    }

    private void handleImportFromFileAction() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select CSV Data File");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        fileChooser.setAcceptAllFileFilterUsed(false);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            Path selectedFile = fileChooser.getSelectedFile().toPath();
            String baseSymbol = DataImportTool.extractBaseSymbol(selectedFile);

            setControlsEnabled(false);
            importStatusLabel.setText("Starting import from file...");
            importProgressBar.setValue(0);
            importProgressBar.setVisible(true);

            Consumer<DataImportTool.ProgressUpdate> progressConsumer = update -> SwingUtilities.invokeLater(() -> {
                importStatusLabel.setText(update.status());
                importProgressBar.setValue(update.percentage());
            });

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() throws Exception {
                    new DataImportTool(progressConsumer).importSingleFile(selectedFile);
                    return "Import completed. Refreshing data sources...";
                }

                @Override
                protected void done() {
                    handleImportWorkerCompletion(this, baseSymbol);
                }
            };
            worker.execute();
        }
    }

    private void handleImportWorkerCompletion(SwingWorker<String, Void> worker, String importedSymbolName) {
        try {
            String result = worker.get();
            importStatusLabel.setText(result);
            DataSourceManager.getInstance().scanDataDirectory();
            
            Object selectedProvider = providerComboBox.getSelectedItem();

            populateProviderComboBox();
            
            if (selectedProvider != null) providerComboBox.setSelectedItem(selectedProvider);
            
            // Find the newly imported symbol in the refreshed list
            DefaultComboBoxModel<ChartDataSource> model = (DefaultComboBoxModel<ChartDataSource>) symbolComboBox.getModel();
            ChartDataSource newlyImportedSource = null;
            int newIndex = -1;
            for (int i = 0; i < model.getSize(); i++) {
                ChartDataSource item = model.getElementAt(i);
                if (item.symbol().equals(importedSymbolName)) {
                    newlyImportedSource = item;
                    newIndex = i;
                    break;
                }
            }
            
            if (newlyImportedSource != null) {
                promptToRenameAndSelectSymbol(newlyImportedSource, newIndex);
            }
            
            JOptionPane.showMessageDialog(SessionDialog.this, "Data import process finished successfully.", "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            logger.error("Error during data import process.", ex);
            importStatusLabel.setText("Error during import. Check logs.");
            JOptionPane.showMessageDialog(SessionDialog.this, "An error occurred during import: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setControlsEnabled(true);
            importProgressBar.setVisible(false);
            importStatusLabel.setText("Ready.");
        }
    }

    private void promptToRenameAndSelectSymbol(ChartDataSource source, int index) {
        String currentName = source.displayName();
        String newName = JOptionPane.showInputDialog(this,
            "Enter display name for the newly imported symbol:",
            currentName);

        ChartDataSource finalSource = source;
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(currentName)) {
            ChartDataSource renamedSource = new ChartDataSource(
                source.providerName(),
                source.symbol(),
                newName.trim(),
                source.dbPath(),
                source.timeframes()
            );

            DefaultComboBoxModel<ChartDataSource> model = (DefaultComboBoxModel<ChartDataSource>) symbolComboBox.getModel();
            model.removeElementAt(index);
            model.insertElementAt(renamedSource, index);
            finalSource = renamedSource;
        }
        symbolComboBox.setSelectedItem(finalSource);
    }
    
    private void setControlsEnabled(boolean enabled) {
        importFromFileButton.setEnabled(enabled);
        launchButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);
        providerComboBox.setEnabled(enabled);
        symbolComboBox.setEnabled(enabled);
        calendarPanel.setEnabled(enabled);
        startingBalanceSpinner.setEnabled(enabled);
        leverageSpinner.setEnabled(enabled);
        
        if (enabled) { // On re-enabling, re-evaluate correct state
            updateReplayControlsState();
        }
    }

    private void populateProviderComboBox() {
        providerComboBox.removeAllItems();

        List<ChartDataSource> sources = DataSourceManager.getInstance().getAvailableSources();
        Stream<ChartDataSource> sourceStream;

        if (sessionMode == SessionMode.REPLAY) {
            sourceStream = sources.stream().filter(s -> s.dbPath() != null);
        } else { // LIVE
            sourceStream = sources.stream().filter(s -> s.dbPath() == null);
        }

        List<String> providers = sourceStream
                .map(ChartDataSource::providerName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        providerComboBox.addItem("All");
        for (String provider : providers) {
            providerComboBox.addItem(provider);
        }
    }

    private void populateSymbolComboBox() {
        String selectedProvider = (String) providerComboBox.getSelectedItem();
        List<ChartDataSource> allSources = DataSourceManager.getInstance().getAvailableSources();
        
        Stream<ChartDataSource> sourceStream;
        if (sessionMode == SessionMode.REPLAY) {
            sourceStream = allSources.stream().filter(s -> s.dbPath() != null);
        } else {
            sourceStream = allSources.stream().filter(s -> s.dbPath() == null);
        }

        List<ChartDataSource> filteredSources;
        if (selectedProvider != null && !"All".equals(selectedProvider)) {
            filteredSources = sourceStream
                .filter(source -> selectedProvider.equals(source.providerName()))
                .collect(Collectors.toList());
        } else {
            filteredSources = sourceStream.collect(Collectors.toList());
        }

        symbolComboBox.setModel(new DefaultComboBoxModel<>(new Vector<>(filteredSources)));

        if (filteredSources.isEmpty()) {
            launchButton.setEnabled(false);
        } else {
            launchButton.setEnabled(sessionMode == SessionMode.LIVE_PAPER_TRADING);
            symbolComboBox.setSelectedIndex(0);
        }
    }

    private void updateReplayControlsState() {
        boolean replayEnabled = replayModeRadioButton.isSelected();
        calendarPanel.setEnabled(replayEnabled);
        replayStartLabel.setEnabled(replayEnabled);
        
        // Launch button is enabled for live mode, or for replay mode if a date is selected
        if (replayEnabled) {
            launchButton.setEnabled(calendarPanel.getSelectedDate() != null);
        } else {
            launchButton.setEnabled(symbolComboBox.getItemCount() > 0);
        }
    }

    private void updateCalendarRange() {
        ChartDataSource source = (ChartDataSource) symbolComboBox.getSelectedItem();
        calendarPanel.clearSelection();
        launchButton.setEnabled(sessionMode != SessionMode.REPLAY && source != null);
        dataStartsLabel.setText("--");
        dataEndsLabel.setText("--");


        if (source == null || source.dbPath() == null) {
            calendarPanel.setDataRange(null, null);
            return;
        }
        
        try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            Optional<DatabaseManager.DataRange> rangeOpt = tempDbManager.getDataRange(new Symbol(source.symbol()), "1m");
            if (rangeOpt.isPresent()) {
                DatabaseManager.DataRange range = rangeOpt.get();
                LocalDate minDate = range.start().atZone(ZoneId.of("UTC")).toLocalDate();
                LocalDate maxDate = range.end().atZone(ZoneId.of("UTC")).toLocalDate();
                dataStartsLabel.setText(minDate.format(DATE_FORMATTER));
                dataEndsLabel.setText(maxDate.format(DATE_FORMATTER));
                calendarPanel.setDataRange(minDate, maxDate);
                calendarPanel.jumpToDate(minDate);
            } else {
                calendarPanel.setDataRange(null, null);
            }
        } catch (Exception e) {
            System.err.println("Could not get data range for calendar: " + e.getMessage());
            calendarPanel.setDataRange(null, null);
        }
    }

    public boolean isLaunched() { return launched; }
    public ChartDataSource getSelectedDataSource() { return selectedDataSource; }
    public SessionMode getSessionMode() { return sessionMode; }
    public int getReplayStartIndex() { return replayStartIndex; }
    public BigDecimal getStartingBalance() { return startingBalance; }
    public BigDecimal getLeverage() { return leverage; }
}