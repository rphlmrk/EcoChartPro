package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.components.FloatingToolbarPanel;
import com.EcoChartPro.ui.dialogs.SessionDialog;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReplayViewPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(ReplayViewPanel.class);

    private final ComprehensiveReportPanel reportView;
    private JButton resumeLastSessionButton;
    private JButton recoverSessionButton;
    private ReplaySessionState lastLoadedState = null;

    public ReplayViewPanel() {
        this.reportView = new ComprehensiveReportPanel();
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        add(createHeaderPanel(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(this.reportView);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getVerticalScrollBar().setUI(new com.formdev.flatlaf.ui.FlatScrollBarUI());

        add(scrollPane, BorderLayout.CENTER);
        add(createBottomToolbar(), BorderLayout.SOUTH);

        findLastSessionForResume();
        updateRecoverButtonState();
        SwingUtilities.invokeLater(this::checkForAutoSaveOnLaunch);
    }
    
    public ComprehensiveReportPanel getReportPanel() {
        return reportView;
    }
    
    public void updateReportWithSession(ReplaySessionState state) {
        if (state == null || state.symbolStates() == null || state.symbolStates().isEmpty()) {
            return;
        }

        // [FIXED] Consolidate all trades from all symbols in the session
        List<Trade> allTradesInSession = state.symbolStates().values().stream()
            .flatMap(symbolState -> {
                if (symbolState.tradeHistory() != null) {
                    return symbolState.tradeHistory().stream();
                }
                return null;
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());

        if (allTradesInSession.isEmpty()) {
            return;
        }

        JournalAnalysisService service = new JournalAnalysisService();
        BigDecimal totalPnl = allTradesInSession.stream()
            .map(Trade::profitAndLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = state.accountBalance().subtract(totalPnl);
        
        JournalAnalysisService.OverallStats stats = service.analyzeOverallPerformance(allTradesInSession, initialBalance);
        reportView.updateData(stats, service, state);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Replay Mode");
        title.setFont(UIManager.getFont("app.font.heading"));
        title.setForeground(UIManager.getColor("Label.foreground"));
        headerPanel.add(title, BorderLayout.CENTER);

        return headerPanel;
    }
    
    private JPanel createBottomToolbar() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        JButton startNewSessionButton = new JButton("Start New Session...");
        styleToolbarButton(startNewSessionButton);
        startNewSessionButton.addActionListener(e -> handleStartNewSession());
        buttonPanel.add(startNewSessionButton);

        buttonPanel.add(createToolbarSeparator());

        JButton loadSessionButton = new JButton("Load Session...");
        styleToolbarButton(loadSessionButton);
        loadSessionButton.addActionListener(e -> handleLoadSession());
        buttonPanel.add(loadSessionButton);

        resumeLastSessionButton = new JButton("Resume Last Session");
        styleToolbarButton(resumeLastSessionButton);
        resumeLastSessionButton.addActionListener(e -> handleResumeSession());
        buttonPanel.add(resumeLastSessionButton);
        
        buttonPanel.add(createToolbarSeparator());

        recoverSessionButton = new JButton("Recover Session");
        styleToolbarButton(recoverSessionButton);
        recoverSessionButton.addActionListener(e -> handleRecoverSession());
        buttonPanel.add(recoverSessionButton);
        
        FloatingToolbarPanel floatingPanel = new FloatingToolbarPanel(50, 50);
        floatingPanel.setLayout(new BorderLayout());
        floatingPanel.add(buttonPanel, BorderLayout.CENTER);
        
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        wrapper.add(floatingPanel);

        return wrapper;
    }

    private void styleToolbarButton(JButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(UIManager.getColor("Button.disabledText"));
        button.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 14f));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(2, 10, 2, 10));
        
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setForeground(UIManager.getColor("Button.foreground"));
                }
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setForeground(UIManager.getColor("Button.disabledText"));
            }
        });
    }
    
    private JSeparator createToolbarSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(UIManager.getColor("Separator.foreground"));
        return separator;
    }

    private void findLastSessionForResume() {
        Optional<Path> lastSessionPathOpt = SessionManager.getInstance().getLastSessionPath();
        if (lastSessionPathOpt.isPresent()) {
            try {
                this.lastLoadedState = SessionManager.getInstance().loadSession(lastSessionPathOpt.get().toFile());
            } catch (IOException e) {
                logger.error("Failed to auto-load last session file for resume button: {}", lastSessionPathOpt.get(), e);
                this.lastLoadedState = null;
            }
        }
        resumeLastSessionButton.setEnabled(this.lastLoadedState != null);
    }

    private void updateRecoverButtonState() {
        boolean exists = AppDataManager.getAutoSaveFilePath().map(Files::exists).orElse(false);
        recoverSessionButton.setEnabled(exists);
    }
    
    private void checkForAutoSaveOnLaunch() {
        Optional<Path> autoSavePath = AppDataManager.getAutoSaveFilePath();
        if (autoSavePath.isPresent() && Files.exists(autoSavePath.get())) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "An unsaved replay session was found. Would you like to recover it?",
                    "Recover Session",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                handleRecoverSession();
            }
        }
    }

    private void handleStartNewSession() {
        Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
        SessionDialog dialog = new SessionDialog(parentFrame, SessionDialog.SessionMode.REPLAY);
        dialog.setVisible(true);

        if (dialog.isLaunched()) {
            if (dialog.getSessionMode() == SessionDialog.SessionMode.REPLAY) {
                SessionController.getInstance().startNewSession(
                    dialog.getSelectedDataSource(),
                    dialog.getReplayStartIndex(),
                    dialog.getStartingBalance(),
                    dialog.getLeverage()
                );
            } else { // LIVE_PAPER_TRADING
                // This branch is now unlikely to be hit due to the context-aware dialog
                SessionController.getInstance().startLiveSession(
                    dialog.getSelectedDataSource(),
                    dialog.getStartingBalance(),
                    dialog.getLeverage()
                );
            }
        }
    }

    private void handleLoadSession() {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }
        fileChooser.setDialogTitle("Load Replay Session");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Replay Session (*.json)", "json"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ReplaySessionState state = SessionManager.getInstance().loadSession(fileChooser.getSelectedFile());
                Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
                SessionController.getInstance().loadSession(state, parentFrame);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load session: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleResumeSession() {
        if (lastLoadedState != null) {
            Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
            SessionController.getInstance().loadSession(lastLoadedState, parentFrame);
        }
    }

    private void handleRecoverSession() {
        Optional<Path> autoSavePath = AppDataManager.getAutoSaveFilePath();
        if (autoSavePath.isPresent() && Files.exists(autoSavePath.get())) {
            try {
                ReplaySessionState state = SessionManager.getInstance().loadSession(autoSavePath.get().toFile());
                Frame parentFrame = (Frame) SwingUtilities.getWindowAncestor(this);
                SessionController.getInstance().loadSession(state, parentFrame);
                SessionManager.getInstance().deleteAutoSaveFile();
                updateRecoverButtonState();
            } catch (IOException ex) {
                 JOptionPane.showMessageDialog(this, "Failed to recover session: " + ex.getMessage(), "Recovery Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}