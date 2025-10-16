package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainContentPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MainContentPanel.class);
    private final CardLayout cardLayout = new CardLayout();
    
    private final ReplayViewPanel replayViewPanel;
    private final LiveViewPanel liveViewPanel;

    // [MODIFIED] Constructor now accepts the DashboardViewPanel
    public MainContentPanel(DashboardViewPanel dashboardViewPanel) {
        setOpaque(false);
        setLayout(cardLayout);
        
        this.replayViewPanel = new ReplayViewPanel();
        this.liveViewPanel = new LiveViewPanel();

        refreshWithLastSession();

        add(dashboardViewPanel, "DASHBOARD"); // Add the passed-in instance
        add(this.replayViewPanel, "REPLAY");
        add(this.liveViewPanel, "LIVE");
    }
    
    public ReplayViewPanel getReplayViewPanel() {
        return replayViewPanel;
    }

    public LiveViewPanel getLiveViewPanel() {
        return liveViewPanel;
    }

    public void switchToView(String viewName) {
        cardLayout.show(this, viewName);
    }

    public void refreshWithLastSession() {
        // [MODIFIED] Now uses the refactored getLatestSessionState()
        Optional<ReplaySessionState> lastSessionStateOpt = SessionManager.getInstance().getLatestSessionState();
        if (lastSessionStateOpt.isPresent()) {
            ReplaySessionState lastLoadedState = lastSessionStateOpt.get();

            if (lastLoadedState != null && lastLoadedState.symbolStates() != null && !lastLoadedState.symbolStates().isEmpty()) {
                // Collect all trades from all symbols to check if there's any history
                List<Trade> allTradesInSession = new ArrayList<>();
                lastLoadedState.symbolStates().values().forEach(s -> {
                    if (s.tradeHistory() != null) {
                        allTradesInSession.addAll(s.tradeHistory());
                    }
                });

                if (!allTradesInSession.isEmpty()) {
                    logger.info("Auto-loading last session data into views.");
                    // The report panel now knows how to handle the full state object
                    replayViewPanel.updateReportWithSession(lastLoadedState);
                    // Gamification service needs the full trade list
                    GamificationService.getInstance().updateProgression(allTradesInSession);
                    firePropertyChange("gamificationUpdated", null, null);
                }
            }
        } else {
            logger.info("No last session found to auto-load.");
        }
    }
    
    private JPanel createPlaceholderPanel(String text) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        
        JLabel label = new JLabel(text);
        label.setFont(UIManager.getFont("app.font.heading"));
        label.setForeground(UIManager.getColor("Label.foreground"));
        panel.add(label);
        
        return panel;
    }
}