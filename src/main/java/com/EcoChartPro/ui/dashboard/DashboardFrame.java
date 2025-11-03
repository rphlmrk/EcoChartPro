package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dashboard.utils.ImageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
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
    private final DashboardViewPanel dashboardViewPanel; // [NEW]
    private static final Logger logger = LoggerFactory.getLogger(DashboardFrame.class);

    public DashboardFrame() {
        setTitle("Eco Chart Pro - Dashboard");
        // [MODIFIED] Add a window listener to handle cleanup
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                // Perform cleanup before exiting
                if (dashboardViewPanel != null) {
                    dashboardViewPanel.cleanup();
                }
                System.exit(0);
            }
        });
        setIconImage(new ImageIcon(getClass().getResource(UITheme.Icons.APP_LOGO)).getImage());

        backgroundPane = new BackgroundLayeredPane();
        setContentPane(backgroundPane);

        // [MODIFIED] Store the panel in the new field and pass it to MainContentPanel
        this.dashboardViewPanel = new DashboardViewPanel();
        mainContentPanel = new MainContentPanel(this.dashboardViewPanel);

        floatingNavPanel = new SidebarPanel(mainContentPanel, backgroundPane);
        xpBarPanel = new XPBarPanel();

        // --- Layout main components ---
        backgroundPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        backgroundPane.add(mainContentPanel, gbc, JLayeredPane.DEFAULT_LAYER);

        gbc.weightx = 0; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(20, 0, 0, 0);
        backgroundPane.add(floatingNavPanel, gbc, JLayeredPane.PALETTE_LAYER);
        
        JPanel topRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topRightPanel.setOpaque(false);
        topRightPanel.add(xpBarPanel);

        gbc.anchor = GridBagConstraints.NORTHEAST;
        gbc.insets = new Insets(10, 0, 0, 10);
        backgroundPane.add(topRightPanel, gbc, JLayeredPane.PALETTE_LAYER);

        mainContentPanel.addPropertyChangeListener(this);
        floatingNavPanel.addPropertyChangeListener(this); // Listen to sidebar view switches
        mainContentPanel.switchToView("DASHBOARD");
        backgroundPane.updateBackgroundImage("DASHBOARD");

        preloadBackgroundImages();
        updateXpBar(); // Initial update

        // Automatically size the window to fit its components' preferred sizes.
        pack();
        
        // minimum width of 1080px, while keeping the packed height.
        Dimension packedSize = getSize();
        setMinimumSize(new Dimension(1080, packedSize.height));
        // If the packed width is less than 1080, resize the window to meet the minimum width.
        if (packedSize.width < 1080) {
            setSize(1080, packedSize.height);
        }

        // Center the window on screen AFTER its size has been determined.
        setLocationRelativeTo(null);
    }
    
    public ComprehensiveReportPanel getReportPanel() {
        // This method now correctly returns the report panel from the Replay view,
        // which holds the overall/historical session data.
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

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamificationUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::updateXpBar);
        } else if ("viewSwitched".equals(evt.getPropertyName()) && evt.getSource() == floatingNavPanel) {
            String viewName = (String) evt.getNewValue();

            // Get references to both report panels.
            ComprehensiveReportPanel replayReportPanel = mainContentPanel.getReplayViewPanel().getReportPanel();
            ComprehensiveReportPanel liveReportPanel = mainContentPanel.getLiveViewPanel().getReportPanel();

            if ("LIVE".equals(viewName)) {
                // Configure services for a LIVE session context.
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.LIVE);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.LIVE);
                
                // Activate the widgets on the live panel to start listening for live data.
                liveReportPanel.activateLiveMode(LiveSessionTrackerService.getInstance());
            } else { // For DASHBOARD or REPLAY views
                // Configure services for a REPLAY session context.
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.REPLAY);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.REPLAY);
                
                // Deactivate the widgets on the live panel to stop them listening and reset their view.
                liveReportPanel.deactivateLiveMode();
            }
        }
    }
    
    private void updateXpBar() {
        com.EcoChartPro.core.gamification.GamificationService service = com.EcoChartPro.core.gamification.GamificationService.getInstance();
        com.EcoChartPro.core.gamification.GamificationService.XpProgress progress = service.getCurrentLevelXpProgress();
        xpBarPanel.updateProgress(service.getCurrentLevel(), progress.currentXpInLevel(), progress.requiredXpForLevel());
    }

    public class BackgroundLayeredPane extends JLayeredPane {
        private Image backgroundImage;
        private static final float BACKGROUND_IMAGE_OPACITY = 0.4f; // Value between 0.0f (transparent) and 1.0f (opaque)

        public void updateBackgroundImage(String viewKey) {
            String resourceIdentifier;

            int index = new ArrayList<>(floatingNavPanel.getBackgroundKeys()).indexOf(viewKey);
            Optional<Path> localPath = ImageProvider.getLocalImage(index);
            if (localPath.isPresent()) {
                resourceIdentifier = localPath.get().toAbsolutePath().toString();
            } else {
                logger.warn("No local image found for view {}. Background will be blank.", viewKey);
                // Set background to null and repaint to clear it
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

                // --- MODIFICATION START ---
                // Save the original composite
                Composite oldComposite = g2d.getComposite();
                // Set the new composite with the desired opacity
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_IMAGE_OPACITY));

                g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);

                // Restore the original composite so other components (like buttons) are not affected
                g2d.setComposite(oldComposite);
                // --- MODIFICATION END ---

                g2d.dispose();
            }
        }
    }
}