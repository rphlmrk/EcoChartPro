package com.EcoChartPro.ui;

import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.ui.action.TitleBarManager;
import com.EcoChartPro.ui.dashboard.ComprehensiveReportPanel;
import com.EcoChartPro.ui.dashboard.DashboardViewPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.AboutDialog;
import com.EcoChartPro.ui.dialogs.SessionDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.List;

public class PrimaryFrame extends JFrame implements PropertyChangeListener {

    // --- Main Layout & Navigation ---
    private final CardLayout mainCardLayout = new CardLayout();
    private final JPanel mainContentPanel = new JPanel(mainCardLayout);
    private JToggleButton analysisNavButton, replayNavButton, liveNavButton;
    private ButtonGroup navGroup;

    // --- Workspaces & Contexts ---
    private final ChartWorkspacePanel replayWorkspacePanel;
    private final ChartWorkspacePanel liveWorkspacePanel;
    private final WorkspaceContext replayContext;
    private final WorkspaceContext liveContext;
    private final TitleBarManager titleBarManager;

    // --- Analysis Tab Components ---
    private final ComprehensiveReportPanel analysisReportPanel;
    private JRadioButton replayReportButton;
    private JRadioButton liveReportButton;
    private final JPanel analysisTabPanel;
    private final CardLayout analysisCardLayout;
    private boolean isReportViewActive = false;
    private ReplaySessionState lastReplayState;
    private ReplaySessionState lastLiveState;
    
    // --- Menu Items ---
    private JMenuItem undoMenuItem;
    private JMenuItem redoMenuItem;


    public PrimaryFrame() {
        setTitle("Eco Chart Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1600, 900);
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon(getClass().getResource(UITheme.Icons.APP_LOGO)).getImage());

        this.titleBarManager = new TitleBarManager(this);
        this.titleBarManager.start();

        replayContext = new WorkspaceContext();
        liveContext = new WorkspaceContext();

        // CONSTRUCTOR CALLS: Replay is TRUE, Live is FALSE
        replayWorkspacePanel = new ChartWorkspacePanel(this, true, replayContext);
        liveWorkspacePanel = new ChartWorkspacePanel(this, false, liveContext);
        
        boolean isConnected = InternetConnectivityService.getInstance().isConnected();
        liveWorkspacePanel.setOfflineMode(!isConnected);

        analysisTabPanel = new JPanel(analysisCardLayout = new CardLayout());
        analysisReportPanel = new ComprehensiveReportPanel();
        DashboardViewPanel splashPanel = new DashboardViewPanel();
        JPanel reportPanelContainer = createAnalysisReportPanel();
        analysisTabPanel.add(splashPanel, "SPLASH");
        analysisTabPanel.add(reportPanelContainer, "REPORT");
        analysisCardLayout.show(analysisTabPanel, "SPLASH");

        // [DEFINITIVE FIX] This is the corrected section.
        // The `replayWorkspacePanel` (with the blue border) is now correctly assigned to the "REPLAY" key.
        // The `liveWorkspacePanel` (with the red border) is assigned to the "LIVE" key.
        mainContentPanel.add(analysisTabPanel, "ANALYSIS");
        mainContentPanel.add(replayWorkspacePanel, "REPLAY");
        mainContentPanel.add(liveWorkspacePanel, "LIVE");

        JPanel navigationBar = createNavigationBar();
        setJMenuBar(createPrimaryMenuBar());
        
        add(navigationBar, BorderLayout.NORTH);
        add(mainContentPanel, BorderLayout.CENTER);
        
        replayContext.getSessionTracker().addPropertyChangeListener(this);
        liveContext.getSessionTracker().addPropertyChangeListener(this);
        replayContext.getUndoManager().addPropertyChangeListener(this);
        liveContext.getUndoManager().addPropertyChangeListener(this);
    }
    
    private JMenuBar createPrimaryMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createToolsMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    private JPanel createNavigationBar() {
        JPanel navigationBar = new JPanel(new BorderLayout());
        navigationBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 10));

        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        navGroup = new ButtonGroup();
        analysisNavButton = createNavButton("Analysis", "ANALYSIS", true);
        replayNavButton = createNavButton("Replay", "REPLAY", false);
        liveNavButton = createNavButton("Live", "LIVE", false);
        
        navGroup.add(analysisNavButton);
        navGroup.add(replayNavButton);
        navGroup.add(liveNavButton);
        
        navButtonPanel.add(analysisNavButton);
        navButtonPanel.add(replayNavButton);
        navButtonPanel.add(liveNavButton);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JLabel shortcutsLabel = new JLabel("", JLabel.CENTER);
        shortcutsLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        startShortcutRotation(shortcutsLabel);
        
        statusPanel.add(shortcutsLabel);

        navigationBar.add(navButtonPanel, BorderLayout.WEST);
        navigationBar.add(statusPanel, BorderLayout.EAST);
        
        return navigationBar;
    }

    private JToggleButton createNavButton(String text, String actionCommand, boolean selected) {
        JToggleButton button = new JToggleButton(text, selected);
        button.setActionCommand(actionCommand);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.addActionListener(e -> {
            mainCardLayout.show(mainContentPanel, actionCommand);
            if (!"REPLAY".equals(actionCommand)) {
                ReplaySessionManager.getInstance().pause();
            }
            updateUndoRedoState();
        
            // Explicitly manage toolbar visibility on tab switch to prevent bleed-through
            if ("LIVE".equals(actionCommand)) {
                replayWorkspacePanel.getDrawingToolbar().setVisible(false);
                replayWorkspacePanel.getPropertiesToolbar().setVisible(false);
                // If there's an active chart in the live panel, its toolbar should be visible.
                if (liveWorkspacePanel.getActiveChartPanel() != null) {
                    liveWorkspacePanel.getDrawingToolbar().setVisible(true);
                }
            } else if ("REPLAY".equals(actionCommand)) {
                liveWorkspacePanel.getDrawingToolbar().setVisible(false);
                liveWorkspacePanel.getPropertiesToolbar().setVisible(false);
                // If there's an active chart in the replay panel, its toolbar should be visible.
                if (replayWorkspacePanel.getActiveChartPanel() != null) {
                    replayWorkspacePanel.getDrawingToolbar().setVisible(true);
                }
            } else { // ANALYSIS tab
                liveWorkspacePanel.getDrawingToolbar().setVisible(false);
                liveWorkspacePanel.getPropertiesToolbar().setVisible(false);
                replayWorkspacePanel.getDrawingToolbar().setVisible(false);
                replayWorkspacePanel.getPropertiesToolbar().setVisible(false);
            }
        });
        return button;
    }

    private void startShortcutRotation(JLabel shortcutsLabel) {
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        String undoShortcut = isMac ? "Cmd+Z: Undo" : "Ctrl+Z: Undo";
        String redoShortcut = isMac ? "Cmd+Shift+Z: Redo" : "Ctrl+Y: Redo";
        final List<String> idleShortcuts = List.of(
            "Alt+T: Trendline", "Alt+R: Rectangle",
            "On Chart: Type Timeframe (e.g., 5m) + Enter", undoShortcut, redoShortcut
        );
        final int[] currentIndex = {0};
        shortcutsLabel.setText(idleShortcuts.get(0));
        new Timer(3000, e -> {
            currentIndex[0] = (currentIndex[0] + 1) % idleShortcuts.size();
            shortcutsLabel.setText(idleShortcuts.get(currentIndex[0]));
        }).start();
    }
    
    private JMenu createFileMenu() {
        JMenu fileMenu = new JMenu("File");
        SessionController sc = SessionController.getInstance();
    
        JMenuItem newReplayItem = new JMenuItem("New Replay Session...");
        newReplayItem.addActionListener(e -> {
            SessionDialog dialog = new SessionDialog(this, SessionDialog.SessionMode.REPLAY);
            dialog.setVisible(true);
            if (dialog.isLaunched()) {
                sc.startNewReplaySession(this, dialog.getSelectedDataSource(), dialog.getReplayStartIndex(),
                                          dialog.getStartingBalance(), dialog.getLeverage());
            }
        });
        fileMenu.add(newReplayItem);
    
        JMenuItem loadReplayItem = new JMenuItem("Load Replay Session...");
        loadReplayItem.addActionListener(e -> sc.loadReplaySessionFromFile(this));
        fileMenu.add(loadReplayItem);
        
        JMenuItem saveReplayItem = new JMenuItem("Save Replay Session As...");
        saveReplayItem.addActionListener(e -> sc.saveSessionWithUI(this, true, replayContext));
        fileMenu.add(saveReplayItem);
        
        fileMenu.addSeparator();
    
        JMenuItem newLiveItem = new JMenuItem("New Live Session...");
        newLiveItem.addActionListener(e -> {
            SessionDialog dialog = new SessionDialog(this, SessionDialog.SessionMode.LIVE_PAPER_TRADING);
            dialog.setVisible(true);
            if (dialog.isLaunched()) {
                sc.startNewLiveSession(this, dialog.getSelectedDataSource(),
                                       dialog.getStartingBalance(), dialog.getLeverage());
            }
        });
        fileMenu.add(newLiveItem);
    
        JMenuItem loadLiveItem = new JMenuItem("Load Live Session...");
        loadLiveItem.addActionListener(e -> sc.loadLiveSessionFromFile(this));
        fileMenu.add(loadLiveItem);
    
        JMenuItem saveLiveItem = new JMenuItem("Save Live Session As...");
        saveLiveItem.addActionListener(e -> sc.saveSessionWithUI(this, false, liveContext));
        fileMenu.add(saveLiveItem);
    
        return fileMenu;
    }
    
    private JMenu createEditMenu() {
        JMenu editMenu = new JMenu("Edit");
        undoMenuItem = new JMenuItem("Undo");
        undoMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undoMenuItem.addActionListener(e -> getActiveContext().getUndoManager().undo());
        
        redoMenuItem = new JMenuItem("Redo");
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        KeyStroke redoKeyStroke = isMac
            ? KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK)
            : KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        redoMenuItem.setAccelerator(redoKeyStroke);
        redoMenuItem.addActionListener(e -> getActiveContext().getUndoManager().redo());
        
        editMenu.add(undoMenuItem);
        editMenu.add(redoMenuItem);
        updateUndoRedoState();
        return editMenu;
    }
    
    private JMenu createToolsMenu() {
        JMenu toolsMenu = new JMenu("Tools");

        JMenuItem javaEditorItem = new JMenuItem("Java Editor...");
        javaEditorItem.addActionListener(e -> getActiveWorkspacePanel().getUiManager().openJavaEditor());
        toolsMenu.add(javaEditorItem);

        JMenuItem marketplaceItem = new JMenuItem("Community Marketplace...");
        marketplaceItem.addActionListener(e -> getActiveWorkspacePanel().getUiManager().openMarketplaceDialog());
        toolsMenu.add(marketplaceItem);
        
        toolsMenu.addSeparator();

        JMenuItem calculatorItem = new JMenuItem("Position Size Calculator...");
        calculatorItem.addActionListener(e -> getActiveWorkspacePanel().getUiManager().openPositionSizeCalculator());
        toolsMenu.add(calculatorItem);

        toolsMenu.addSeparator();

        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.addActionListener(e -> getActiveWorkspacePanel().getUiManager().openSettingsDialog());
        toolsMenu.add(settingsItem);

        return toolsMenu;
    }

    private JMenu createHelpMenu() { 
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About Eco Chart Pro");
        aboutItem.addActionListener(e -> new AboutDialog(this).setVisible(true));
        helpMenu.add(aboutItem);
        return helpMenu;
    }

    private void updateUndoRedoState() {
        if (undoMenuItem == null || redoMenuItem == null) return;
        WorkspaceContext activeContext = getActiveContext();
        undoMenuItem.setEnabled(activeContext.getUndoManager().canUndo());
        redoMenuItem.setEnabled(activeContext.getUndoManager().canRedo());
    }

    private WorkspaceContext getActiveContext() {
        if (replayNavButton != null && replayNavButton.isSelected()) {
            return replayContext;
        }
        return liveContext;
    }

    private ChartWorkspacePanel getActiveWorkspacePanel() {
        if (replayNavButton != null && replayNavButton.isSelected()) {
            return replayWorkspacePanel;
        }
        return liveWorkspacePanel;
    }
    
    private JPanel createAnalysisReportPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Show Report For:"));
        replayReportButton = new JRadioButton("Replay Session");
        liveReportButton = new JRadioButton("Live Session");
        ButtonGroup group = new ButtonGroup();
        group.add(replayReportButton);
        group.add(liveReportButton);
        topPanel.add(replayReportButton);
        topPanel.add(liveReportButton);
        ActionListener reportSwitcher = e -> updateAnalysisReport();
        replayReportButton.addActionListener(reportSwitcher);
        liveReportButton.addActionListener(reportSwitcher);
        replayReportButton.setSelected(true);
        JScrollPane scrollPane = new JScrollPane(analysisReportPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("sessionStatsUpdated".equals(evt.getPropertyName())) {
            Object source = evt.getSource();
            if (source == replayContext.getSessionTracker()) {
                lastReplayState = replayContext.getPaperTradingService().getCurrentSessionState();
            } else if (source == liveContext.getSessionTracker()) {
                lastLiveState = liveContext.getPaperTradingService().getCurrentSessionState();
            }
            updateAnalysisReport();
        } else if ("stateChanged".equals(evt.getPropertyName())) {
            updateUndoRedoState();
        }
    }

    private void updateAnalysisReport() {
        SwingUtilities.invokeLater(() -> {
            if (!isReportViewActive) {
                analysisCardLayout.show(analysisTabPanel, "REPORT");
                isReportViewActive = true;
            }
            if (replayReportButton.isSelected()) {
                analysisReportPanel.updateData(lastReplayState);
            } else if (liveReportButton.isSelected()) {
                analysisReportPanel.updateData(lastLiveState);
            }
        });
    }

    public WorkspaceContext getReplayContext() { return replayContext; }
    public WorkspaceContext getLiveContext() { return liveContext; }
    public JPanel getMainContentPanel() { return mainContentPanel; }
    public CardLayout getMainCardLayout() { return mainCardLayout; }
    public ButtonGroup getNavGroup() { return navGroup; }
    public JToggleButton getReplayNavButton() { return replayNavButton; }
    public JToggleButton getLiveNavButton() { return liveNavButton; }
    public TitleBarManager getTitleBarManager() { return this.titleBarManager; }
}