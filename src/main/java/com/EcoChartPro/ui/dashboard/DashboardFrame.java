package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.ui.components.ConnectionStatusWidget;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dashboard.utils.ImageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

public class DashboardFrame extends JFrame implements PropertyChangeListener {

    private final MainContentPanel mainContentPanel;
    private final SidebarPanel floatingNavPanel;
    private final BackgroundLayeredPane backgroundPane;
    private final XPBarPanel xpBarPanel;
    private final DashboardViewPanel dashboardViewPanel;
    private static final Logger logger = LoggerFactory.getLogger(DashboardFrame.class);
    private final ConnectionStatusWidget connectionStatusWidget;
    private final JPanel topRightContainer; 

    public DashboardFrame() {
        setTitle("Eco Chart Pro - Dashboard");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                // Perform cleanup before exiting
                if (dashboardViewPanel != null) {
                    dashboardViewPanel.cleanup();
                }
                InternetConnectivityService.getInstance().removePropertyChangeListener(DashboardFrame.this);
                System.exit(0);
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                repositionWidgets();
            }
        });
        setIconImage(new ImageIcon(getClass().getResource(UITheme.Icons.APP_LOGO)).getImage());

        backgroundPane = new BackgroundLayeredPane();
        setContentPane(backgroundPane);


        this.dashboardViewPanel = new DashboardViewPanel();
        mainContentPanel = new MainContentPanel(this.dashboardViewPanel);

        floatingNavPanel = new SidebarPanel(mainContentPanel, backgroundPane);
        xpBarPanel = new XPBarPanel();
        this.connectionStatusWidget = new ConnectionStatusWidget();

        this.topRightContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topRightContainer.setOpaque(false);
        topRightContainer.add(xpBarPanel);

        // --- Layout main components ---
        backgroundPane.add(mainContentPanel, JLayeredPane.DEFAULT_LAYER);
        backgroundPane.add(floatingNavPanel, JLayeredPane.PALETTE_LAYER);
        backgroundPane.add(topRightContainer, JLayeredPane.PALETTE_LAYER);
        backgroundPane.add(connectionStatusWidget, JLayeredPane.MODAL_LAYER);

        mainContentPanel.addPropertyChangeListener(this);
        floatingNavPanel.addPropertyChangeListener(this); 
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);

        mainContentPanel.switchToView("DASHBOARD");
        backgroundPane.updateBackgroundImage("DASHBOARD");

        preloadBackgroundImages();
        updateXpBar();

        pack();
        setMinimumSize(new Dimension(1080, 600));
        setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> updateConnectionStatus(InternetConnectivityService.getInstance().isConnected()));
    }
    
    public ComprehensiveReportPanel getReportPanel() {
        return mainContentPanel.getReplayViewPanel().getReportPanel();
    }

    private void preloadBackgroundImages() {
        int i = 0;
        for (String key : floatingNavPanel.getBackgroundKeys()) {
            final int index = i;
            ImageProvider.getLocalImage(index).ifPresent(path -> {
                ImageProvider.fetchImage(path.toAbsolutePath().toString(), img -> {});
            });
            i++;
        }
    }

    private void repositionWidgets() {
        if (backgroundPane == null) return;

        if (mainContentPanel != null) {
            mainContentPanel.setBounds(0, 0, backgroundPane.getWidth(), backgroundPane.getHeight());
        }

        if (floatingNavPanel != null) {
            Dimension navSize = floatingNavPanel.getPreferredSize();
            floatingNavPanel.setBounds((backgroundPane.getWidth() - navSize.width) / 2, 20, navSize.width, navSize.height);
        }

        if (topRightContainer != null) {
            Dimension trcSize = topRightContainer.getPreferredSize();
            topRightContainer.setBounds(backgroundPane.getWidth() - trcSize.width - 10, 10, trcSize.width, trcSize.height);
        }

        if (connectionStatusWidget != null && connectionStatusWidget.isVisible()) {
            Dimension widgetSize = connectionStatusWidget.getPreferredSize();
            int y = (floatingNavPanel != null) ? floatingNavPanel.getY() + floatingNavPanel.getHeight() + 10 : 20;
            int x = (backgroundPane.getWidth() - widgetSize.width) / 2;
            connectionStatusWidget.setBounds(x, y, widgetSize.width, widgetSize.height);
        }
    }

    private void updateConnectionStatus(boolean isConnected) {
        if (isConnected) {
            connectionStatusWidget.hideStatus();
        } else {
            connectionStatusWidget.showStatus(
                "Internet Connection Lost. Live features are disabled.",
                UITheme.Icons.WIFI_OFF,
                UIManager.getColor("app.trading.pending") 
            );
            repositionWidgets();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamificationUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::updateXpBar);
        } else if ("viewSwitched".equals(evt.getPropertyName()) && evt.getSource() == floatingNavPanel) {
            String viewName = (String) evt.getNewValue();

            ComprehensiveReportPanel liveReportPanel = mainContentPanel.getLiveViewPanel().getReportPanel();

            if ("LIVE".equals(viewName)) {
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.LIVE);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.LIVE);
                liveReportPanel.activateLiveMode(LiveSessionTrackerService.getInstance());
            } else { 
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.REPLAY);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.REPLAY);
                liveReportPanel.deactivateLiveMode();
            }
        } else if ("connectivityChanged".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                if (evt.getNewValue() instanceof Boolean) {
                    updateConnectionStatus((Boolean) evt.getNewValue());
                }
            });
        }
    }
    
    private void updateXpBar() {
        com.EcoChartPro.core.gamification.GamificationService service = com.EcoChartPro.core.gamification.GamificationService.getInstance();
        com.EcoChartPro.core.gamification.GamificationService.XpProgress progress = service.getCurrentLevelXpProgress();
        xpBarPanel.updateProgress(service.getCurrentLevel(), progress.currentXpInLevel(), progress.requiredXpForLevel());
    }

    public class BackgroundLayeredPane extends JLayeredPane {
        private Image backgroundImage;
        private static final float BACKGROUND_IMAGE_OPACITY = 0.4f;

        // [MODIFIED] This is the key fix. We force the layered pane to report
        // the preferred size of its main content, which allows pack() to work correctly.
        @Override
        public Dimension getPreferredSize() {
            if (mainContentPanel != null) {
                return mainContentPanel.getPreferredSize();
            }
            return super.getPreferredSize(); // Fallback
        }

        public void updateBackgroundImage(String viewKey) {
            String resourceIdentifier;

            int index = new ArrayList<>(floatingNavPanel.getBackgroundKeys()).indexOf(viewKey);
            Optional<Path> localPath = ImageProvider.getLocalImage(index);
            if (localPath.isPresent()) {
                resourceIdentifier = localPath.get().toAbsolutePath().toString();
            } else {
                logger.warn("No local image found for view {}. Background will be blank.", viewKey);
                this.backgroundImage = null;
                repaint();
                return;
            }
            
            ImageProvider.fetchImage(resourceIdentifier, (img) -> {
                this.backgroundImage = img;
                repaint();
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                Composite oldComposite = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_IMAGE_OPACITY));
                g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                g2d.setComposite(oldComposite);
                g2d.dispose();
            }
        }
    }
}