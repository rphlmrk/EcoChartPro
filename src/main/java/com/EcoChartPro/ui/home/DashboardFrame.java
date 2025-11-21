package com.EcoChartPro.ui.home;

import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.ui.components.ConnectionStatusWidget;
import com.EcoChartPro.ui.home.theme.UITheme;
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

public class DashboardFrame extends JFrame implements PropertyChangeListener {

    private final MainContentPanel mainContentPanel;
    private final SidebarPanel floatingNavPanel;
    private final JLayeredPane backgroundPane; // Changed from custom inner class to standard JLayeredPane
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

        // [FIX] Use standard JLayeredPane instead of custom BackgroundLayeredPane
        backgroundPane = new JLayeredPane() {
            @Override
            public Dimension getPreferredSize() {
                if (mainContentPanel != null) {
                    return mainContentPanel.getPreferredSize();
                }
                return super.getPreferredSize();
            }

            // Optional: Draw a solid background color instead of an image
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UIManager.getColor("Panel.background")); // Or a specific dark gray
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        backgroundPane.setOpaque(true); // Optimization
        setContentPane(backgroundPane);

        this.dashboardViewPanel = new DashboardViewPanel();
        mainContentPanel = new MainContentPanel(this.dashboardViewPanel);

        // [FIX] Remove backgroundPane argument from SidebarPanel constructor
        floatingNavPanel = new SidebarPanel(mainContentPanel);
        xpBarPanel = new XPBarPanel();
        this.connectionStatusWidget = new ConnectionStatusWidget();

        this.topRightContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topRightContainer.setOpaque(false);
        topRightContainer.add(xpBarPanel);

        backgroundPane.add(mainContentPanel, JLayeredPane.DEFAULT_LAYER);
        backgroundPane.add(floatingNavPanel, JLayeredPane.PALETTE_LAYER);
        backgroundPane.add(topRightContainer, JLayeredPane.PALETTE_LAYER);
        backgroundPane.add(connectionStatusWidget, JLayeredPane.MODAL_LAYER);

        mainContentPanel.addPropertyChangeListener(this);
        floatingNavPanel.addPropertyChangeListener(this);
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);

        mainContentPanel.switchToView("DASHBOARD");
        // [FIX] Removed backgroundPane.updateBackgroundImage call

        updateXpBar();

        pack();
        setMinimumSize(new Dimension(1080, 600));
        setLocationRelativeTo(null);

        SwingUtilities
                .invokeLater(() -> updateConnectionStatus(InternetConnectivityService.getInstance().isConnected()));
    }

    public ComprehensiveReportPanel getReportPanel() {
        return mainContentPanel.getReplayViewPanel().getReportPanel();
    }

    // [FIX] Removed preloadBackgroundImages()

    private void repositionWidgets() {
        if (backgroundPane == null)
            return;

        if (mainContentPanel != null) {
            mainContentPanel.setBounds(0, 0, backgroundPane.getWidth(), backgroundPane.getHeight());
        }

        if (floatingNavPanel != null) {
            Dimension navSize = floatingNavPanel.getPreferredSize();
            floatingNavPanel.setBounds((backgroundPane.getWidth() - navSize.width) / 2, 20, navSize.width,
                    navSize.height);
        }

        if (topRightContainer != null) {
            Dimension trcSize = topRightContainer.getPreferredSize();
            topRightContainer.setBounds(backgroundPane.getWidth() - trcSize.width - 10, 10, trcSize.width,
                    trcSize.height);
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
                    UIManager.getColor("app.trading.pending"));
            repositionWidgets();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamificationUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::updateXpBar);
        } else if ("connectivityChanged".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                if (evt.getNewValue() instanceof Boolean) {
                    updateConnectionStatus((Boolean) evt.getNewValue());
                }
            });
        }
    }

    private void updateXpBar() {
        com.EcoChartPro.core.gamification.GamificationService service = com.EcoChartPro.core.gamification.GamificationService
                .getInstance();
        com.EcoChartPro.core.gamification.GamificationService.XpProgress progress = service.getCurrentLevelXpProgress();
        xpBarPanel.updateProgress(service.getCurrentLevel(), progress.currentXpInLevel(),
                progress.requiredXpForLevel());
    }
}