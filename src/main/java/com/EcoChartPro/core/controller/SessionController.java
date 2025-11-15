package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.ui.ChartWorkspacePanel;
import com.EcoChartPro.ui.PrimaryFrame;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;


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
    
    // [MODIFIED] Helper to find the correct workspace panel instance
    private Optional<ChartWorkspacePanel> getWorkspacePanel(PrimaryFrame frame, boolean isReplay) {
        return Arrays.stream(frame.getMainContentPanel().getComponents())
                .filter(c -> c instanceof ChartWorkspacePanel)
                .map(c -> (ChartWorkspacePanel) c)
                .filter(p -> p.isReplayMode() == isReplay)
                .findFirst();
    }


    public void startNewReplaySession(PrimaryFrame primaryFrame, DataSourceManager.ChartDataSource source, int startIndex, BigDecimal startingBalance, BigDecimal leverage) {
        SwingUtilities.invokeLater(() -> {
            Optional<ChartWorkspacePanel> replayPanelOpt = getWorkspacePanel(primaryFrame, true);

            if (replayPanelOpt.isPresent()) {
                ChartWorkspacePanel replayPanel = replayPanelOpt.get();
                primaryFrame.getReplayContext().getPaperTradingService().resetSession(startingBalance, leverage);
                replayPanel.startReplaySession(source, startIndex);
                
                primaryFrame.getNavGroup().setSelected(primaryFrame.getReplayNavButton().getModel(), true);
                primaryFrame.getMainCardLayout().show(primaryFrame.getMainContentPanel(), "REPLAY");
                logger.info("New replay session started for symbol '{}'", source.symbol());
            } else {
                logger.error("Could not find the replay workspace panel in PrimaryFrame.");
            }
        });
    }

    public void startNewLiveSession(PrimaryFrame primaryFrame, DataSourceManager.ChartDataSource source, BigDecimal startingBalance, BigDecimal leverage) {
        SwingUtilities.invokeLater(() -> {
            SessionManager.getInstance().deleteLiveAutoSaveFile();
            LiveWindowManager.getInstance().startSession(source);
            
            Optional<ChartWorkspacePanel> livePanelOpt = getWorkspacePanel(primaryFrame, false);

            if (livePanelOpt.isPresent()) {
                ChartWorkspacePanel livePanel = livePanelOpt.get();
                primaryFrame.getLiveContext().getPaperTradingService().resetSession(startingBalance, leverage);
                livePanel.startLiveSession(source);

                primaryFrame.getNavGroup().setSelected(primaryFrame.getLiveNavButton().getModel(), true);
                primaryFrame.getMainCardLayout().show(primaryFrame.getMainContentPanel(), "LIVE");
                logger.info("New live session started for symbol '{}'", source.symbol());
            } else {
                logger.error("Could not find the live workspace panel in PrimaryFrame.");
            }
        });
    }

    public void loadReplaySession(PrimaryFrame primaryFrame, ReplaySessionState state) {
        if (state.symbolStates() != null) {
            state.symbolStates().forEach((symbol, symbolState) -> 
                SymbolProgressCache.getInstance().updateProgressForSymbol(symbol, symbolState)
            );
        }

        SwingUtilities.invokeLater(() -> {
            Optional<ChartWorkspacePanel> replayPanelOpt = getWorkspacePanel(primaryFrame, true);
            
            if (replayPanelOpt.isPresent()) {
                replayPanelOpt.get().loadSessionState(state);
                primaryFrame.getNavGroup().setSelected(primaryFrame.getReplayNavButton().getModel(), true);
                primaryFrame.getMainCardLayout().show(primaryFrame.getMainContentPanel(), "REPLAY");
                logger.info("Loaded replay session state.");
            } else {
                logger.error("Could not find the replay workspace panel to load state.");
            }
        });
    }

    public void loadLiveSession(PrimaryFrame primaryFrame, ReplaySessionState state) {
        SwingUtilities.invokeLater(() -> {
            Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();

            if (sourceOpt.isPresent()) {
                LiveWindowManager.getInstance().startSession(sourceOpt.get());
            } else {
                String message = "Could not load live session: Data source for symbol '" + state.lastActiveSymbol() + "' not found.";
                logger.error(message);
                JOptionPane.showMessageDialog(primaryFrame, message, "Load Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            Optional<ChartWorkspacePanel> livePanelOpt = getWorkspacePanel(primaryFrame, false);

            if (livePanelOpt.isPresent()) {
                livePanelOpt.get().loadSessionState(state);
                primaryFrame.getNavGroup().setSelected(primaryFrame.getLiveNavButton().getModel(), true);
                primaryFrame.getMainCardLayout().show(primaryFrame.getMainContentPanel(), "LIVE");
                logger.info("Loaded live session state.");
            } else {
                logger.error("Could not find the live workspace panel to load state.");
            }
        });
    }


    public void handleWindowClose(Frame owner, boolean isReplayMode, WorkspaceContext context) {
        boolean hasAnyTrades = context.getPaperTradingService().hasAnyTradesOrPositions();
        
        if (!hasAnyTrades) {
            owner.dispose();
            return;
        }

        if (isReplayMode) {
            int choice = JOptionPane.showConfirmDialog(
                    owner,
                    "Do you want to save the current replay session before closing?",
                    "Save Session",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION) return;
            if (choice == JOptionPane.YES_OPTION) saveSessionWithUI(owner, true, context);
        }
        
        owner.dispose();
    }
    
    public boolean saveSessionWithUI(Frame owner, boolean isReplayMode, WorkspaceContext context) {
        ReplaySessionState state = context.getPaperTradingService().getCurrentSessionState();
        if (state == null) {
            JOptionPane.showMessageDialog(owner, "There is no active session state to save.", "Save Error", JOptionPane.ERROR_MESSAGE);
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

        if (fileChooser.showSaveDialog(owner) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".json")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".json");
            }
            try {
                SessionManager.getInstance().saveSession(state, fileToSave, isReplayMode);
                JOptionPane.showMessageDialog(owner, "Session saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                return true;
            } catch (IOException ex) {
                logger.error("Failed to save session via UI", ex);
                JOptionPane.showMessageDialog(owner, "Failed to save session: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return false;
    }
    
    public void loadLiveSessionFromFile(PrimaryFrame primaryFrame) {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Load Live Session");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Saved Live Session (*.json)", "json"));
        
        if (fileChooser.showOpenDialog(primaryFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                ReplaySessionState state = SessionManager.getInstance().loadStateFromLiveFile(file);
                loadLiveSession(primaryFrame, state);
            } catch (IOException e) {
                logger.error("Failed to load live session from file: {}", file.getAbsolutePath(), e);
                JOptionPane.showMessageDialog(primaryFrame, "Failed to load session: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    public void loadReplaySessionFromFile(PrimaryFrame primaryFrame) {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Load Replay Session");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Replay Session (*.json)", "json"));
    
        if (fileChooser.showOpenDialog(primaryFrame) == JFileChooser.APPROVE_OPTION) {
            try {
                ReplaySessionState state = SessionManager.getInstance().loadSession(fileChooser.getSelectedFile());
                loadReplaySession(primaryFrame, state);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(primaryFrame, "Failed to load session: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}