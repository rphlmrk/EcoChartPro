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

/**
 * A streamlined dialog for configuring and launching a new replay session.
 * It combines symbol selection with a calendar for choosing the start date.
 */
public class NewReplayDialog extends JDialog {

    private static final Logger logger = LoggerFactory.getLogger(NewReplayDialog.class);

    private final JComboBox<ChartDataSource> symbolComboBox;
    private final CalendarPanel calendarPanel;
    private JButton launchButton;
    private JButton importCsvButton;
    private JButton cancelButton;
    private JLabel dataRangeLabel;
    private JFormattedTextField balanceField;
    private JComboBox<String> leverageComboBox;

    private boolean launched = false;
    private ChartDataSource selectedDataSource;
    private int replayStartIndex;
    private LocalDate selectedDate;

    public NewReplayDialog(Frame owner) {
        super(owner, "Start New Replay Session", true);
        setSize(450, 600); // Increased height for config fields
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(15, 15));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setResizable(false);

        // --- Top Panel: Symbol Selection ---
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.add(new JLabel("Symbol:"), BorderLayout.WEST);
        symbolComboBox = new JComboBox<>();
        topPanel.add(symbolComboBox, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- Center Panel: Calendar ---
        calendarPanel = new CalendarPanel(date -> {
            this.selectedDate = date;
            launchButton.setEnabled(true);
        });
        add(calendarPanel, BorderLayout.CENTER);

        // --- Bottom Panel: Config, Label, and Buttons ---
        JPanel southPanel = new JPanel(new BorderLayout(0, 15));

        // Config Panel: Balance & Leverage
        JPanel configPanel = new JPanel(new java.awt.GridLayout(0, 2, 10, 8));
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
        launchButton.setEnabled(false); // Disabled until a date is selected

        buttonPanel.add(importCsvButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(launchButton);

        JPanel bottomContainer = new JPanel(new BorderLayout(0, 10));
        dataRangeLabel = new JLabel("Select a symbol to see available data range.", SwingConstants.CENTER);
        dataRangeLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        bottomContainer.add(dataRangeLabel, BorderLayout.CENTER);
        bottomContainer.add(buttonPanel, BorderLayout.SOUTH);

        southPanel.add(bottomContainer, BorderLayout.CENTER);

        add(southPanel, BorderLayout.SOUTH);

        addListeners();
        populateSymbolComboBox();
        updateDataRange();
    }

    private void addListeners() {
        symbolComboBox.addActionListener(e -> updateDataRange());
        launchButton.addActionListener(e -> handleLaunch());
        cancelButton.addActionListener(e -> dispose());
        importCsvButton.addActionListener(e -> handleImportCsv());
    }

    private void updateDataRange() {
        ChartDataSource source = (ChartDataSource) symbolComboBox.getSelectedItem();
        if (source == null || source.dbPath() == null) {
            dataRangeLabel.setText("No data source selected.");
            calendarPanel.setDataRange(null, null);
            launchButton.setEnabled(false);
            return;
        }

        dataRangeLabel.setText("Loading data range...");
        calendarPanel.setDataRange(null, null);
        launchButton.setEnabled(false);

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
                        dataRangeLabel.setText(String.format("Data available from %s to %s",
                                formatter.format(minDate), formatter.format(maxDate)));
                        
                        calendarPanel.setDataRange(minDate, maxDate);
                        
                        // [FIX] Jump to the latest month with available data
                        calendarPanel.jumpToDate(maxDate);
                    } else {
                        dataRangeLabel.setText("No 1-minute data found for this symbol.");
                        calendarPanel.setDataRange(null, null);
                        launchButton.setEnabled(false);
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving data range in SwingWorker.", e);
                    dataRangeLabel.setText("Error retrieving data range.");
                    calendarPanel.setDataRange(null, null);
                    launchButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void handleLaunch() {
        this.selectedDataSource = (ChartDataSource) symbolComboBox.getSelectedItem();
        if (this.selectedDataSource == null || this.selectedDataSource.dbPath() == null || this.selectedDate == null) {
            JOptionPane.showMessageDialog(this, "Please select a valid symbol and a start date.", "Configuration Incomplete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // highly efficient, memory-safe method to find the start index ---
        try (DatabaseManager tempDbManager = new DatabaseManager("jdbc:sqlite:" + this.selectedDataSource.dbPath().toAbsolutePath())) {
            this.replayStartIndex = tempDbManager.findClosestTimestampIndex(
                    new Symbol(this.selectedDataSource.symbol()),
                    "1m",
                    this.selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            );
            this.launched = true;
            dispose();
        } catch (Exception ex) {
            logger.error("Failed to determine replay start index.", ex);
            JOptionPane.showMessageDialog(this, "Error accessing data for the selected symbol:\n" + ex.getMessage(), "Data Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populateSymbolComboBox() {
        Object previouslySelected = symbolComboBox.getSelectedItem();
        symbolComboBox.removeAllItems();
        List<ChartDataSource> sources = DataSourceManager.getInstance().getAvailableSources();

        if (sources.isEmpty()) {
            symbolComboBox.addItem(new ChartDataSource("No Data", "No Data Found", null, List.of()));
            launchButton.setEnabled(false);
            symbolComboBox.setEnabled(false);
        } else {
            ChartDataSource toSelect = null;
            for (ChartDataSource source : sources) {
                symbolComboBox.addItem(source);
                if (source.equals(previouslySelected)) {
                    toSelect = source;
                }
            }
            if (toSelect != null) {
                symbolComboBox.setSelectedItem(toSelect);
            }
            symbolComboBox.setEnabled(true);
        }
    }
    
    /**
     * Helper method to infer a base symbol name from a CSV file name.
     * This logic is mirrored from DataImportTool to provide a good default name.
     * @param file The file to inspect.
     * @return The inferred base symbol name (e.g., "btcusdt" from "BTCUSDT_2023.csv").
     */
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
            if (selectedFiles.length == 0) {
                return;
            }
    
            // Suggest a symbol name based on the first selected file.
            String suggestedName = extractBaseSymbolFromFile(selectedFiles[0]);
            String newSymbolName = (String) JOptionPane.showInputDialog(
                    this,
                    "Enter a name for this symbol:",
                    "Rename Symbol on Import",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    suggestedName
            );
    
            if (newSymbolName == null || newSymbolName.trim().isEmpty()) {
                return; // User cancelled or entered an empty name.
            }
    
            CsvImportWorker worker = new CsvImportWorker(selectedFiles, this, newSymbolName.trim());
            worker.execute();
        }
    }
    
    private class CsvImportWorker extends SwingWorker<String, DataImportTool.ProgressUpdate> {
        private final File[] sourceFiles;
        private final NewReplayDialog parentDialog;
        private Exception error = null;
        private final JDialog progressDialog;
        private final JProgressBar progressBar;
        private final JLabel progressLabel;
        private final String customSymbolName;

        CsvImportWorker(File[] sourceFiles, NewReplayDialog parentDialog, String customSymbolName) {
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
    
                // Sanitize the custom name for file system usage and get the original name for replacement.
                String sanitizedSymbolName = this.customSymbolName.toLowerCase().replaceAll("[^a-z0-9_./-]", "").replace("/", "_");
                String originalBaseSymbol = parentDialog.extractBaseSymbolFromFile(sourceFiles[0]);
    
                for (File sourceFile : sourceFiles) {
                    // Construct the new filename by replacing the old symbol part with the new one.
                    String newFileName = sourceFile.getName().toLowerCase().replace(originalBaseSymbol, sanitizedSymbolName);
                    Path targetPath = importDir.resolve(newFileName);
                    Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                DataImportTool importer = new DataImportTool(this::publish);
                importer.run();
    
                // Return the new, sanitized symbol name so the UI can select it after the import.
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
                    parentDialog.populateSymbolComboBox();

                    if (importedSymbol != null) {
                        for (int i = 0; i < symbolComboBox.getItemCount(); i++) {
                            if (symbolComboBox.getItemAt(i).symbol().equalsIgnoreCase(importedSymbol)) {
                                symbolComboBox.setSelectedIndex(i);
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

    public boolean isLaunched() { return launched; }
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
}