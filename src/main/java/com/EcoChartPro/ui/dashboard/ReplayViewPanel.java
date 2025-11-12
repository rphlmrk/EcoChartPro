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
    // [REMOVED] All buttons and state for them are now obsolete.
    // private JButton resumeLastSessionButton;
    // private JButton recoverSessionButton;
    // private ReplaySessionState lastLoadedState = null;

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
        
        // [REMOVED] The bottom toolbar is no longer needed.
        // add(createBottomToolbar(), BorderLayout.SOUTH);
        
        // [REMOVED] All logic related to the old buttons is now obsolete.
    }
    
    public ComprehensiveReportPanel getReportPanel() {
        return reportView;
    }
    
    public void updateData(ReplaySessionState state) {
        if (state == null || state.symbolStates() == null || state.symbolStates().isEmpty()) {
            return;
        }

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
    
    // [REMOVED] The following methods are all obsolete as they deal with UI components
    // and logic that have been moved to PrimaryFrame.
    // createBottomToolbar()
    // styleToolbarButton()
    // createToolbarSeparator()
    // findLastSessionForResume()
    // updateRecoverButtonState()
    // checkForAutoSaveOnLaunch()
    // handleStartNewSession()
    // handleLoadSession()
    // handleResumeSession()
    // handleRecoverSession()
}