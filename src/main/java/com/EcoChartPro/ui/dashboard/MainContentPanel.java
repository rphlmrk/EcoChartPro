package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class MainContentPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MainContentPanel.class);
    private final CardLayout cardLayout = new CardLayout();
    
    private final ReplayViewPanel replayViewPanel;

    public MainContentPanel() {
        setOpaque(false);
        setLayout(cardLayout);
        
        this.replayViewPanel = new ReplayViewPanel();

        autoLoadLastSession();

        add(new DashboardViewPanel(), "DASHBOARD");
        add(this.replayViewPanel, "REPLAY");
        add(createPlaceholderPanel("Live Market - Not Implemented"), "LIVE");
    }
    
    public ReplayViewPanel getReplayViewPanel() {
        return replayViewPanel;
    }

    public void switchToView(String viewName) {
        cardLayout.show(this, viewName);
    }

    private void autoLoadLastSession() {
        Optional<Path> lastSessionPathOpt = SessionManager.getInstance().getLastSessionPath();
        if (lastSessionPathOpt.isPresent()) {
            try {
                File sessionFile = lastSessionPathOpt.get().toFile();
                ReplaySessionState lastLoadedState = SessionManager.getInstance().loadSession(sessionFile);

                if (lastLoadedState != null && !lastLoadedState.tradeHistory().isEmpty()) {
                    logger.info("Auto-loading last session data into views.");
                    replayViewPanel.updateReportWithSession(lastLoadedState);
                    GamificationService.getInstance().updateProgression(lastLoadedState.tradeHistory());
                    firePropertyChange("gamificationUpdated", null, null);
                }
            } catch (IOException e) {
                logger.error("Failed to auto-load last session file: {}", lastSessionPathOpt.get(), e);
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