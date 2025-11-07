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

    // [MODIFIED] Define the preferred size as a field.
    // This is the SINGLE place to control the dashboard's default launch size.
    private final Dimension defaultDashboardSize = new Dimension(740, 680);

    public MainContentPanel(DashboardViewPanel dashboardViewPanel) {
        setOpaque(false);
        setLayout(cardLayout);
        
        this.replayViewPanel = new ReplayViewPanel();
        this.liveViewPanel = new LiveViewPanel();

        refreshWithLastSession();

        add(dashboardViewPanel, "DASHBOARD");
        add(this.replayViewPanel, "REPLAY");
        add(this.liveViewPanel, "LIVE");
    }

    // [MODIFIED] Override getPreferredSize to enforce our desired default dimensions.
    // This is more robust than setPreferredSize() as it cannot be overridden
    // by the panel's internal layout manager.
    @Override
    public Dimension getPreferredSize() {
        return defaultDashboardSize;
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
        // [MODIFIED] Load REPLAY session data using the replay-specific method
        Optional<ReplaySessionState> lastReplayStateOpt = SessionManager.getInstance().getLatestReplaySessionState();
        if (lastReplayStateOpt.isPresent()) {
            ReplaySessionState lastLoadedState = lastReplayStateOpt.get();
            if (lastLoadedState != null && lastLoadedState.symbolStates() != null && !lastLoadedState.symbolStates().isEmpty()) {
                List<Trade> allTradesInSession = new ArrayList<>();
                lastLoadedState.symbolStates().values().forEach(s -> {
                    if (s.tradeHistory() != null) allTradesInSession.addAll(s.tradeHistory());
                });

                if (!allTradesInSession.isEmpty()) {
                    logger.info("Auto-loading last replay session data into views.");
                    replayViewPanel.updateData(lastLoadedState);
                    GamificationService.getInstance().updateProgression(allTradesInSession);
                    firePropertyChange("gamificationUpdated", null, null);
                }
            }
        } else {
            logger.info("No last replay session found to auto-load.");
        }

        // Load LIVE session data
        try {
            ReplaySessionState liveState = SessionManager.getInstance().loadLiveSession();
            logger.info("Auto-loading last live session data into views.");
            liveViewPanel.getReportPanel().updateData(liveState);
        } catch (IOException e) {
            logger.info("No last live session found to auto-load.");
            liveViewPanel.getReportPanel().updateData(null); // Clear the panel
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