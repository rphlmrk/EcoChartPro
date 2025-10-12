package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dashboard.utils.ImageProvider;
import com.EcoChartPro.core.settings.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DashboardFrame extends JFrame implements PropertyChangeListener {

    private final MainContentPanel mainContentPanel;
    private final SidebarPanel floatingNavPanel;
    private final BackgroundLayeredPane backgroundPane;
    private final XPBarPanel xpBarPanel;
    private static final Logger logger = LoggerFactory.getLogger(DashboardFrame.class);

    public DashboardFrame() {
        setTitle("Eco Chart Pro - Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(new ImageIcon(getClass().getResource(UITheme.Icons.APP_LOGO)).getImage());

        backgroundPane = new BackgroundLayeredPane();
        setContentPane(backgroundPane);

        mainContentPanel = new MainContentPanel();
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
        topRightPanel.add(createSettingsButton());

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
        // Assuming the report panel is inside the ReplayViewPanel, which is inside MainContentPanel
        return mainContentPanel.getReplayViewPanel().getReportPanel();
    }

    private void preloadBackgroundImages() {
        int i = 0;
        for (String key : floatingNavPanel.getBackgroundKeys()) {
            String liveUrl = backgroundPane.getLiveUrlForKey(key);
            ImageProvider.fetchImage(liveUrl, img -> {});

            final int index = i;
            ImageProvider.getLocalImage(index).ifPresent(path -> {
                ImageProvider.fetchImage(path.toAbsolutePath().toString(), img -> {});
            });
            i++;
        }
    }
    
    private JButton createSettingsButton() {
        JButton button = new JButton(UITheme.getIcon(UITheme.Icons.SETTINGS, 20, 20));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addActionListener(e -> {
            JPopupMenu menu = createImageSourceMenu();
            menu.show(button, 0, button.getHeight());
        });
        return button;
    }
    
    private JPopupMenu createImageSourceMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        SettingsManager settings = SettingsManager.getInstance();

        JRadioButtonMenuItem liveItem = new JRadioButtonMenuItem("Live Images", settings.getImageSource() == SettingsManager.ImageSource.LIVE);
        liveItem.addActionListener(e -> settings.setImageSource(SettingsManager.ImageSource.LIVE));
        
        JRadioButtonMenuItem localItem = new JRadioButtonMenuItem("Local Images", settings.getImageSource() == SettingsManager.ImageSource.LOCAL);
        localItem.addActionListener(e -> settings.setImageSource(SettingsManager.ImageSource.LOCAL));

        group.add(liveItem);
        group.add(localItem);
        menu.add(liveItem);
        menu.add(localItem);
        
        return menu;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamificationUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(this::updateXpBar);
        } else if ("viewSwitched".equals(evt.getPropertyName()) && evt.getSource() == floatingNavPanel) {
            String viewName = (String) evt.getNewValue();
            ComprehensiveReportPanel reportPanel = getReportPanel();

            if ("LIVE".equals(viewName)) {
                // When switching to the LIVE view, activate live tracking
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.LIVE);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.LIVE);
                reportPanel.activateLiveMode(LiveSessionTrackerService.getInstance());
            } else { // For DASHBOARD or REPLAY views, show the replay context
                LiveSessionTrackerService.getInstance().setActiveSessionType(SessionType.REPLAY);
                PaperTradingService.getInstance().setActiveSessionType(SessionType.REPLAY);
                reportPanel.deactivateLiveMode();
                mainContentPanel.refreshWithLastSession();
            }
        }
    }
    
    private void updateXpBar() {
        com.EcoChartPro.core.gamification.GamificationService service = com.EcoChartPro.core.gamification.GamificationService.getInstance();
        com.EcoChartPro.core.gamification.GamificationService.XpProgress progress = service.getCurrentLevelXpProgress();
        xpBarPanel.updateProgress(service.getCurrentLevel(), progress.currentXpInLevel(), progress.requiredXpForLevel());
    }

    public class BackgroundLayeredPane extends JLayeredPane implements PropertyChangeListener {
        private Image backgroundImage;
        private final Map<String, String> liveUrls = new HashMap<>();

        public BackgroundLayeredPane() {
            SettingsManager.getInstance().addPropertyChangeListener(this);
            
            liveUrls.put("DASHBOARD", "https://picsum.photos/1090/640");
            liveUrls.put("REPLAY", "https://picsum.photos/1090/640?grayscale&blur=6");
            liveUrls.put("LIVE", "https://picsum.photos/1090/640");
        }

        public void updateBackgroundImage(String viewKey) {
            SettingsManager.ImageSource source = SettingsManager.getInstance().getImageSource();
            String resourceIdentifier = null;

            if (source == SettingsManager.ImageSource.LOCAL) {
                int index = java.util.Arrays.asList(floatingNavPanel.getBackgroundKeys()).indexOf(viewKey);
                Optional<Path> localPath = ImageProvider.getLocalImage(index);
                if (localPath.isPresent()) {
                    resourceIdentifier = localPath.get().toAbsolutePath().toString();
                } else {
                    logger.warn("Local image for {} not found, falling back to live URL.", viewKey);
                    resourceIdentifier = getLiveUrlForKey(viewKey);
                }
            } else { // LIVE
                resourceIdentifier = getLiveUrlForKey(viewKey);
            }
            
            ImageProvider.fetchImage(resourceIdentifier, (img) -> {
                this.backgroundImage = img;
                repaint();
            });
        }
        
        public String getLiveUrlForKey(String key) {
            return liveUrls.getOrDefault(key, liveUrls.get("DASHBOARD"));
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if ("imageSource".equals(evt.getPropertyName())) {
                updateBackgroundImage(floatingNavPanel.getSelectedViewKey());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (backgroundImage != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                g2d.dispose();
            }
        }
    }
}