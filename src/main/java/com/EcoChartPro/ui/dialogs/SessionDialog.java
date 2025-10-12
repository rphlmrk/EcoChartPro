package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.data.provider.BinanceProvider;
import com.EcoChartPro.data.DataProvider;
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
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A streamlined dialog for configuring and launching a new replay or live session.
 */
public class SessionDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(SessionDialog.class);

    // --- NEW: Enum to define session mode ---
    public enum SessionMode { REPLAY, LIVE_PAPER_TRADING }

    private final CardLayout configCardLayout = new CardLayout();
    private final JPanel configCardPanel;

    // --- Replay Mode Components ---
    private final JComboBox<ChartDataSource> replaySymbolComboBox;
    private final CalendarPanel calendarPanel;
    private JLabel replayDataRangeLabel;
    private JButton importCsvButton;

    // --- Live Mode Components ---
    private final JComboBox<ChartDataSource> liveSymbolComboBox;

    // --- Common Components ---
    private JButton launchButton;
    private JButton cancelButton;
    private JFormattedTextField balanceField;
    private JComboBox<String> leverageComboBox;

    // --- State Holders ---
    private boolean launched = false;
    private SessionMode selectedMode = SessionMode.REPLAY;
    private ChartDataSource selectedDataSource;
    private int replayStartIndex;
    private LocalDate selectedDate;

    public SessionDialog(Frame owner) {
        super(owner, "Start New Session", true);
        setSize(450, 650);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setResizable(false);

        // --- Top Panel: Mode Selection ---
        JRadioButton replayRadio = new JRadioButton("Replay Session", true);
        JRadioButton liveRadio = new JRadioButton("Live Paper Trading");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(replayRadio);
        modeGroup.add(liveRadio);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        modePanel.add(replayRadio);
        modePanel.add(liveRadio);

        // --- Config Card Panel (swaps between Replay and Live) ---
        configCardPanel = new JPanel(configCardLayout);
        this.replaySymbolComboBox = new JComboBox<>();
        this.calendarPanel = new CalendarPanel(this::onDateSelected);
        configCardPanel.add(createReplayConfigPanel(), SessionMode.REPLAY.name());

        this.liveSymbolComboBox = new JComboBox<>();
        configCardPanel.add(createLiveConfigPanel(), SessionMode.LIVE_PAPER_TRADING.name());
        
        add(modePanel, BorderLayout.NORTH);
        add(configCardPanel, BorderLayout.CENTER);

        // --- Bottom Panel: Account Config and Buttons ---
        add(createBottomPanel(), BorderLayout.SOUTH);

        addListeners(replayRadio, liveRadio);
        populateReplaySymbolComboBox();
        populateLiveSymbolComboBox();
        updateReplayDataRange();
    }
    
    // --- Getters for SessionController ---
    public boolean isLaunched() { return launched; }
    public SessionMode getSessionMode() { return selectedMode; }
    public ChartDataSource getSelectedDataSource() { return selectedDataSource; }
    public int getReplayStartIndex() { return replayStartIndex; }
    public BigDecimal getStartingBalance() {
        Object value = balanceField.getValue();
        return (value instanceof Number) ? new BigDecimal(value.toString()) : new BigDecimal("100000");
    }
    public BigDecimal getLeverage() {
        String selected = (String) leverageComboBox.getSelectedItem();
        if (selected == null) return BigDecimal.ONE;
        String numericPart = selected.replace("x", "").trim();
        return new BigDecimal(numericPart);
    }
    
    // --- UI Panel Creation Methods ---

    private JPanel createReplayConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.add(new JLabel("Symbol:"), BorderLayout.WEST);
        topPanel.add(replaySymbolComboBox, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(calendarPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLiveConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 0, 5, 0);
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Symbol:"), gbc);
        gbc.gridy = 1;
        panel.add(liveSymbolComboBox, gbc);

        // Add a filler panel to push components to the top
        gbc.gridy = 2; gbc.weighty = 1.0;
        panel.add(new JPanel(), gbc);
        return panel;
    }

    private JPanel createBottomPanel() {
        JPanel southPanel = new JPanel(new BorderLayout(0, 15));
        JPanel configPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        configPanel.setBorder(BorderFactory.createTitledBorder("Initial Account State"));
        
        configPanel.add(new JLabel("Starting Balance:"));
        java.text.NumberFormat currencyFormat = java.text.NumberFormat.getCurrencyInstance();
        NumberFormatter currencyFormatter = new NumberFormatter(currencyFormat);
        currencyFormatter.setAllowsInvalid(false);
        currencyFormatter.setCommitsOnValidEdit(true);
        balanceField = new JFormattedTextField(currencyFormatter);
        balanceField.setValue(new BigDecimal("100000"));
        configPanel.add(balanceField);

        configPanel.add(new JLabel("Leverage:"));
        String[] leverageOptions = {"1x", "5x", "10x", "20x"};
        leverageComboBox = new JComboBox<>(leverageOptions);
        configPanel.add(leverageComboBox);
        southPanel.add(configPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        importCsvButton = new JButton("Import New Data...");
        cancelButton = new JButton("Cancel");
        launchButton = new JButton("Launch");
        launchButton.setFont(launchButton.getFont().deriveFont(Font.BOLD));
        launchButton.setEnabled(false);

        buttonPanel.add(importCsvButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(launchButton);

        JPanel bottomContainer = new JPanel(new BorderLayout(0, 10));
        replayDataRangeLabel = new JLabel("Select a symbol to see available data range.", SwingConstants.CENTER);
        replayDataRangeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        bottomContainer.add(replayDataRangeLabel, BorderLayout.CENTER);
        bottomContainer.add(buttonPanel, BorderLayout.SOUTH);

        southPanel.add(bottomContainer, BorderLayout.CENTER);
        return southPanel;
    }

    // --- Event Handling and Logic ---

    private void addListeners(JRadioButton replayRadio, JRadioButton liveRadio) {
        replayRadio.addActionListener(e -> switchMode(SessionMode.REPLAY));
        liveRadio.addActionListener(e -> switchMode(SessionMode.LIVE_PAPER_TRADING));
        
        replaySymbolComboBox.addActionListener(e -> updateReplayDataRange());
        launchButton.addActionListener(e -> handleLaunch());
        cancelButton.addActionListener(e -> dispose());
        importCsvButton.addActionListener(e -> handleImportCsv());
    }
    
    private void switchMode(SessionMode mode) {
        this.selectedMode = mode;
        configCardLayout.show(configCardPanel, mode.name());
        
        boolean isReplay = (mode == SessionMode.REPLAY);
        replayDataRangeLabel.setVisible(isReplay);
        importCsvButton.setVisible(isReplay);
        
        // Launch button is always enabled for Live mode (if symbols exist), but depends on date for Replay
        launchButton.setEnabled(!isReplay || selectedDate != null);
    }

    private void onDateSelected(LocalDate date) {
        this.selectedDate = date;
        launchButton.setEnabled(true);
    }

    private void updateReplayDataRange() {
        ChartDataSource source = (ChartDataSource) replaySymbolComboBox.getSelectedItem();
        this.selectedDate = null;
        launchButton.setEnabled(false);
        calendarPanel.setEnabled(true);
        calendarPanel.clearSelection();
        
        if (source == null || source.dbPath() == null) {
            replayDataRangeLabel.setText("No data source selected.");
            calendarPanel.setDataRange(null, null);
            return;
        }

        replayDataRangeLabel.setText("Loading data range...");
        calendarPanel.setDataRange(null, null);

        SwingWorker<Optional<DatabaseManager.DataRange>, Void> worker = new SwingWorker<>() {
            @Override
            protected Optional<DatabaseManager.DataRange> doInBackground() {
                try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
                    return tempDbManager.getDataRange(new Symbol(source.symbol()), "1m");
                } catch (Exception e) {
                    logger.error("Failed to query data range for {}", source.symbol(), e);
                    return Optional.empty();
                }
            }
            @Override
            protected void done() {
                try {
                    Optional<DatabaseManager.DataRange> rangeOpt = get();
                    if (rangeOpt.isPresent()) {
                        DatabaseManager.DataRange range = rangeOpt.get();
                        LocalDate minDate = range.start().atZone(ZoneOffset.UTC).toLocalDate();
                        LocalDate maxDate = range.end().atZone(ZoneOffset.UTC).toLocalDate();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
                        replayDataRangeLabel.setText(String.format("Data available from %s to %s", formatter.format(minDate), formatter.format(maxDate)));
                        calendarPanel.setDataRange(minDate, maxDate);
                        calendarPanel.jumpToDate(maxDate);
                    } else {
                        replayDataRangeLabel.setText("No 1-minute data found for this symbol.");
                        calendarPanel.setDataRange(null, null);
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving data range in SwingWorker.", e);
                    replayDataRangeLabel.setText("Error retrieving data range.");
                    calendarPanel.setDataRange(null, null);
                }
            }
        };
        worker.execute();
    }
    
    private void handleLaunch() {
        if (selectedMode == SessionMode.REPLAY) {
            this.selectedDataSource = (ChartDataSource) replaySymbolComboBox.getSelectedItem();
            if (this.selectedDataSource == null || this.selectedDataSource.dbPath() == null || this.selectedDate == null) {
                JOptionPane.showMessageDialog(this, "Please select a valid symbol and a start date.", "Configuration Incomplete", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + this.selectedDataSource.dbPath().toAbsolutePath())) {
                this.replayStartIndex = tempDbManager.findClosestTimestampIndex(new Symbol(this.selectedDataSource.symbol()), "1m", this.selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC));
                this.launched = true;
                dispose();
            } catch (Exception ex) {
                logger.error("Failed to determine replay start index.", ex);
                JOptionPane.showMessageDialog(this, "Error accessing data for the selected symbol:\n" + ex.getMessage(), "Data Error", JOptionPane.ERROR_MESSAGE);
            }
        } else { // LIVE_PAPER_TRADING
            this.selectedDataSource = (ChartDataSource) liveSymbolComboBox.getSelectedItem();
            if (this.selectedDataSource == null) {
                 JOptionPane.showMessageDialog(this, "Please select a symbol to trade.", "Configuration Incomplete", JOptionPane.WARNING_MESSAGE);
                return;
            }
            this.launched = true;
            dispose();
        }
    }

    private void populateReplaySymbolComboBox() {
        Object previouslySelected = replaySymbolComboBox.getSelectedItem();
        replaySymbolComboBox.removeAllItems();
        List<ChartDataSource> sources = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.dbPath() != null) // Only local file sources for replay
                .collect(Collectors.toList());

        if (sources.isEmpty()) {
            replaySymbolComboBox.addItem(new ChartDataSource("No Data", "No Data Found", null, List.of()));
            launchButton.setEnabled(false);
            replaySymbolComboBox.setEnabled(false);
        } else {
            ChartDataSource toSelect = null;
            for (ChartDataSource source : sources) {
                replaySymbolComboBox.addItem(source);
                if (source.equals(previouslySelected)) toSelect = source;
            }
            if (toSelect != null) replaySymbolComboBox.setSelectedItem(toSelect);
            replaySymbolComboBox.setEnabled(true);
        }
    }
    
    private void populateLiveSymbolComboBox() {
        // For now, hardcode to Binance provider. In the future, this could be more dynamic.
        new SwingWorker<List<ChartDataSource>, Void>() {
            @Override
            protected List<ChartDataSource> doInBackground() throws Exception {
                DataProvider liveProvider = new BinanceProvider();
                return liveProvider.getAvailableSymbols();
            }

            @Override
            protected void done() {
                try {
                    List<ChartDataSource> liveSources = get();
                    liveSymbolComboBox.removeAllItems();
                    if (liveSources.isEmpty()) {
                        liveSymbolComboBox.addItem(new ChartDataSource("No Live Data", "Could not connect", null, List.of()));
                        liveSymbolComboBox.setEnabled(false);
                    } else {
                        liveSources.forEach(liveSymbolComboBox::addItem);
                        liveSymbolComboBox.setEnabled(true);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load live symbols", e);
                }
            }
        }.execute();
    }
    
    // --- CSV Import (unchanged from SessionDialog) ---
    private String extractBaseSymbolFromFile(File file) {
        if (file == null) return "new_symbol";
        String fileName = file.getName().toLowerCase();
        return fileName.replaceAll("_\\d{4}\\.csv", ".csv").replace(".csv", "");
    }

    private void handleImportCsv() {
        JFileChooser fileChooser = new JFileChooser(System.getProperty("user.home"));
        fileChooser.setDialogTitle("Select CSV Data File(s)");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Data Files (*.csv)", "csv"));
        fileChooser.setMultiSelectionEnabled(true);
    
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length == 0) return;
    
            String suggestedName = extractBaseSymbolFromFile(selectedFiles[0]);
            String newSymbolName = (String) JOptionPane.showInputDialog(this, "Enter a name for this symbol:", "Rename Symbol on Import", JOptionPane.PLAIN_MESSAGE, null, null, suggestedName);
    
            if (newSymbolName == null || newSymbolName.trim().isEmpty()) return; 
    
            CsvImportWorker worker = new CsvImportWorker(selectedFiles, this, newSymbolName.trim());
            worker.execute();
        }
    }
    
    private class CsvImportWorker extends SwingWorker<String, DataImportTool.ProgressUpdate> {
        private final File[] sourceFiles;
        private final SessionDialog parentDialog;
        private Exception error = null;
        private final JDialog progressDialog;
        private final JProgressBar progressBar;
        private final JLabel progressLabel;
        private final String customSymbolName;

        CsvImportWorker(File[] sourceFiles, SessionDialog parentDialog, String customSymbolName) {
            this.sourceFiles = sourceFiles;
            this.parentDialog = parentDialog;
            this.customSymbolName = customSymbolName;
            
            progressDialog = new JDialog(parentDialog, "Importing Data", false);
            progressBar = new JProgressBar(0, 100);
            progressLabel = new JLabel("Starting import...");
            
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            panel.add(progressLabel, BorderLayout.NORTH);
            panel.add(progressBar, BorderLayout.CENTER);
            
            progressDialog.add(panel);
            progressDialog.pack();
            progressDialog.setSize(350, progressDialog.getHeight());
            progressDialog.setLocationRelativeTo(parentDialog);
            progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        }

        @Override
        protected String doInBackground() {
            SwingUtilities.invokeLater(() -> {
                parentDialog.importCsvButton.setEnabled(false);
                progressDialog.setVisible(true);
            });
            try {
                Path importDir = Paths.get(System.getProperty("user.dir"), "import_data");
                Files.createDirectories(importDir);
    
                String sanitizedSymbolName = this.customSymbolName.toLowerCase().replaceAll("[^a-z0-9_./-]", "").replace("/", "_");
                String originalBaseSymbol = parentDialog.extractBaseSymbolFromFile(sourceFiles[0]);
    
                for (File sourceFile : sourceFiles) {
                    String newFileName = sourceFile.getName().toLowerCase().replace(originalBaseSymbol, sanitizedSymbolName);
                    Path targetPath = importDir.resolve(newFileName);
                    Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                DataImportTool importer = new DataImportTool(this::publish);
                importer.run();
    
                return sanitizedSymbolName;
            } catch (Exception e) {
                this.error = e;
                return null;
            }
        }
        
        @Override
        protected void process(List<DataImportTool.ProgressUpdate> chunks) {
            DataImportTool.ProgressUpdate latest = chunks.get(chunks.size() - 1);
            progressLabel.setText(latest.status());
            progressBar.setValue(latest.percentage());
        }

        @Override
        protected void done() {
            progressDialog.dispose();
            parentDialog.importCsvButton.setEnabled(true);

            try {
                String importedSymbol = get();
                if (error == null) {
                    JOptionPane.showMessageDialog(parentDialog, "Import process completed!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    DataSourceManager.getInstance().scanDataDirectory();
                    parentDialog.populateReplaySymbolComboBox();

                    if (importedSymbol != null) {
                        for (int i = 0; i < replaySymbolComboBox.getItemCount(); i++) {
                            if (replaySymbolComboBox.getItemAt(i).symbol().equalsIgnoreCase(importedSymbol)) {
                                replaySymbolComboBox.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } else {
                    throw error;
                }
            } catch (Exception ex) {
                logger.error("Error during CSV import process.", ex);
                JOptionPane.showMessageDialog(parentDialog, "An error occurred during import:\n" + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}