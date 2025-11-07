package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.dashboard.DashboardFrame;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of replay sessions, including starting, loading,
 * and handling window closing logic.
 */
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private static volatile SessionController instance;

    private SessionController() {}

    public static SessionController getInstance() {
        if (instance == null) {
            synchronized (SessionController.class) {
                if (instance == null) {
                    instance = new SessionController();
                }
            }
        }
        return instance;
    }

    public void startNewSession(DataSourceManager.ChartDataSource source, int startIndex, BigDecimal startingBalance, BigDecimal leverage) {
        SwingUtilities.invokeLater(() -> {
            findAndSetDashboardVisible(false);
            PaperTradingService.getInstance().setActiveSessionType(SessionType.REPLAY);
            LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.REPLAY);
            PaperTradingService.getInstance().resetSession(startingBalance, leverage);
            MainWindow mainWindow = new MainWindow(true);
            mainWindow.startReplaySession(source, startIndex);
        });
    }

    public void startNewLiveSession(DataSourceManager.ChartDataSource source, BigDecimal startingBalance, BigDecimal leverage) {
        SwingUtilities.invokeLater(() -> {
            // [MODIFIED] Explicitly clear any old live session file when starting a NEW one
            SessionManager.getInstance().deleteLiveAutoSaveFile();
            
            findAndSetDashboardVisible(false);
            PaperTradingService.getInstance().setActiveSessionType(SessionType.LIVE);
            LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.LIVE);
            PaperTradingService.getInstance().resetSession(startingBalance, leverage);
            
            LiveWindowManager.getInstance().startSession(source);

            MainWindow mainWindow = new MainWindow(false);
            mainWindow.startLiveSession(source);
        });
    }

    public void startLiveSession(ReplaySessionState state) {
        SwingUtilities.invokeLater(() -> {
            findAndSetDashboardVisible(false);
            PaperTradingService.getInstance().setActiveSessionType(SessionType.LIVE);
            LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.LIVE);
            PaperTradingService.getInstance().restoreState(state);
            
            Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();

            if (sourceOpt.isPresent()) {
                LiveWindowManager.getInstance().startSession(sourceOpt.get());
            } else {
                // [FIX] More robust error handling
                String message = "Could not resume live session: the saved session file is missing a valid symbol.\n" +
                                 "This can happen if a session was closed before a chart was loaded.\n\n" +
                                 "Please start a new live session instead.";
                logger.error("Could not start LiveWindowManager session: data source for null symbol found in saved state.");
                JOptionPane.showMessageDialog(null, message, "Load Error", JOptionPane.ERROR_MESSAGE);
                findAndSetDashboardVisible(true); // Go back to dashboard
                return; // Abort the launch
            }
            
            MainWindow mainWindow = new MainWindow(false);
            // [MODIFIED] We load the session state into the existing MainWindow instance
            mainWindow.loadSessionState(state);
        });
    }

    public void loadSession(ReplaySessionState state, Frame parentFrame) {
        PaperTradingService.getInstance().setActiveSessionType(SessionType.REPLAY);
        LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.REPLAY);

        if (state.symbolStates() != null) {
            state.symbolStates().forEach((symbol, symbolState) -> 
                SymbolProgressCache.getInstance().updateProgressForSymbol(symbol, symbolState)
            );
        }

        SwingUtilities.invokeLater(() -> {
            if (parentFrame instanceof DashboardFrame) {
                ((DashboardFrame) parentFrame).getReportPanel().activateLiveMode(LiveSessionTrackerService.getInstance());
            }
            findAndSetDashboardVisible(false);
            MainWindow mainWindow = new MainWindow(true);
            mainWindow.loadSessionState(state);
        });
    }

    public void handleWindowClose(MainWindow window, boolean isReplayMode) {
        PaperTradingService pts = PaperTradingService.getInstance();
        boolean hasAnyTrades = pts.hasAnyTradesOrPositions();
        
        if (!hasAnyTrades) {
            endSessionAndShowDashboard(window, isReplayMode);
            return;
        }

        if (isReplayMode) {
            int choice = JOptionPane.showConfirmDialog(
                    window,
                    "Do you want to save the current replay session before closing?",
                    "Save Session",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION) return;
            if (choice == JOptionPane.YES_OPTION) saveSessionWithUI(window, true);
        } else { // Live mode
            try {
                ReplaySessionState state = pts.getCurrentSessionState();
                SessionManager.getInstance().saveLiveSession(state);
                logger.info("Live session state automatically saved on close.");
            } catch (IOException e) {
                logger.error("Failed to auto-save live session state on close.", e);
                JOptionPane.showMessageDialog(window, "Could not save live session progress: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        
        endSessionAndShowDashboard(window, isReplayMode);
    }

    public void endSessionAndShowDashboard(Window window, boolean isReplayMode) {
        if (!isReplayMode) {
            LiveWindowManager.getInstance().endSession();
        }
        SessionManager.getInstance().deleteAutoSaveFile(); // This is for replay mode's tick-by-tick auto-save
        window.dispose();
        findAndSetDashboardVisible(true);
    }

    public boolean saveSessionWithUI(MainWindow window, boolean isReplayMode) {
        ReplaySessionState state = PaperTradingService.getInstance().getCurrentSessionState();
        if (state == null) {
            JOptionPane.showMessageDialog(window, "There is no active session state to save.", "Save Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        
        String dialogTitle = isReplayMode ? "Save Replay Session" : "Save Live Session";
        String fileFilterDesc = isReplayMode ? "Replay Session (*.json)" : "Saved Live Session (*.json)";
        fileChooser.setDialogTitle(dialogTitle);
        
        String defaultFileName = (state.lastActiveSymbol() != null ? state.lastActiveSymbol() : "session") 
                                 + "_" + System.currentTimeMillis() + ".json";
        fileChooser.setSelectedFile(new File(defaultFileName));
        fileChooser.setFileFilter(new FileNameExtensionFilter(fileFilterDesc, "json"));

        if (fileChooser.showSaveDialog(window) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".json")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".json");
            }
            try {
                SessionManager.getInstance().saveSession(state, fileToSave, isReplayMode);
                JOptionPane.showMessageDialog(window, "Session saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (IOException ex) {
                logger.error("Failed to save session via UI", ex);
                JOptionPane.showMessageDialog(window, "Failed to save session: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return false;
    }
    
    // [MODIFIED] Implementation of the "Load Live Session" feature
    public void loadLiveSessionFromFile(MainWindow currentWindow) {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Load Live Session");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Saved Live Session (*.json)", "json"));
        
        // Use dashboard frame if currentWindow is null (when called from dashboard)
        Frame parent = (currentWindow != null) ? currentWindow : getDashboardFrame();

        if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                ReplaySessionState state = SessionManager.getInstance().loadStateFromLiveFile(file);
                // If a live window is already open, close it first. This is a safe no-op if currentWindow is null.
                if (currentWindow != null) {
                    endSessionAndShowDashboard(currentWindow, false);
                }
                // Start the new session from the loaded state
                startLiveSession(state);
            } catch (IOException e) {
                logger.error("Failed to load live session from file: {}", file.getAbsolutePath(), e);
                JOptionPane.showMessageDialog(parent, "Failed to load session: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public boolean exportTradeHistory(MainWindow owner) {
        PaperTradingService tradingService = PaperTradingService.getInstance();
        List<Trade> tradeHistory = tradingService.getTradeHistory();
        if (tradeHistory.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "There is no trade history to export.", "Export Trades", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Export Trade History to CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        DataSourceManager.ChartDataSource source = ReplaySessionManager.getInstance().getCurrentSource();
        String symbol = (source != null) ? source.symbol() : "trades";
        String defaultFileName = String.format("%s_export_%s.csv", symbol,
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now()));
        fileChooser.setSelectedFile(new File(defaultFileName));
        if (fileChooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".csv");
            }
            String header = "id,symbol,direction,entryTime,entryPrice,exitTime,exitPrice,quantity,profitAndLoss,planFollowed,notes,tags,checklistId\n";
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                writer.write(header);
                for (Trade trade : tradeHistory) {
                    writer.write(formatTradeAsCsvRow(trade));
                    writer.newLine();
                }
                JOptionPane.showMessageDialog(owner, "Trade history exported successfully!", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(owner, "Failed to export trade history: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
                logger.error("Error exporting trade history to CSV", ex);
            }
        }
        return false;
    }
    
    public void importTradeHistory(MainWindow owner) {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Import Trade History from CSV");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        if (fileChooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            if (JOptionPane.showConfirmDialog(owner, "Warning: This will overwrite the current session's trade history.", "Confirm Import", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            File fileToImport = fileChooser.getSelectedFile();
            List<Trade> importedTrades = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(fileToImport))) {
                reader.readLine(); // Skip header
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        importedTrades.add(parseCsvRowAsTrade(line));
                    }
                }
                PaperTradingService.getInstance().importTradeHistory(importedTrades, new BigDecimal("100000"));
                JOptionPane.showMessageDialog(owner, "Successfully imported " + importedTrades.size() + " trades.", "Import Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(owner, "Failed to import trades: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                logger.error("Error importing trade history from CSV", ex);
            }
        }
    }

    private Trade parseCsvRowAsTrade(String line) throws IllegalArgumentException {
        try {
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length < 12) throw new IllegalArgumentException("Incorrect number of columns. Expected at least 12, got " + parts.length);
            
            // Handle optional checklistId at the end
            UUID checklistId = null;
            if (parts.length > 12 && parts[12] != null && !parts[12].trim().isEmpty()) {
                checklistId = UUID.fromString(parts[12].trim());
            }

            return new Trade(
                UUID.fromString(parts[0].trim()), new Symbol(parts[1].trim()), TradeDirection.valueOf(parts[2].trim().toUpperCase()),
                Instant.parse(parts[3].trim()), new BigDecimal(parts[4].trim()), Instant.parse(parts[5].trim()),
                new BigDecimal(parts[6].trim()), new BigDecimal(parts[7].trim()), new BigDecimal(parts[8].trim()),
                Boolean.parseBoolean(parts[9].trim()), unquote(parts[10].trim()),
                Arrays.asList(unquote(parts[11].trim()).split("\\|")),
                checklistId
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse trade row. Error: " + e.getMessage(), e);
        }
    }

    private String formatTradeAsCsvRow(Trade trade) {
        String notes = trade.notes() == null ? "" : trade.notes();
        String tags = (trade.tags() == null || trade.tags().isEmpty()) ? "" : trade.tags().stream().collect(Collectors.joining("|"));
        String checklistId = trade.checklistId() == null ? "" : trade.checklistId().toString();
        
        return String.join(",",
            trade.id().toString(), trade.symbol().name(), trade.direction().toString(),
            trade.entryTime().toString(), trade.entryPrice().toPlainString(),
            trade.exitTime().toString(), trade.exitPrice().toPlainString(),
            trade.quantity().toPlainString(), trade.profitAndLoss().toPlainString(),
            String.valueOf(trade.planFollowed()),
            quote(notes), quote(tags), checklistId
        );
    }

    private String quote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    
    private String unquote(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }

    private void findAndSetDashboardVisible(boolean visible) {
        for (Frame frame : Frame.getFrames()) {
            if (frame instanceof DashboardFrame) {
                if (visible) {
                    frame.setVisible(true);
                    frame.toFront();
                    frame.requestFocus();
                } else {
                    frame.setVisible(false);
                }
                break; 
            }
        }
    }

    // [NEW] Helper to get the dashboard frame for parenting dialogs
    private Frame getDashboardFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame instanceof DashboardFrame) {
                return frame;
            }
        }
        return null;
    }
}