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

    // --- Workspaces & Contexts ---
    private final ChartWorkspacePanel replayWorkspacePanel;
    private final ChartWorkspacePanel liveWorkspacePanel;
    private final WorkspaceContext replayContext;
    private final WorkspaceContext liveContext;
    private final TitleBarManager titleBarManager;

    // --- Home Tab Components ---
    private final ComprehensiveReportPanel analysisReportPanel;
    private JRadioButton replayReportButton;
    private JRadioButton liveReportButton;
    private final JPanel reportPanelContainer;
    private ReplaySessionState lastReplayState;
    private ReplaySessionState lastLiveState;

    // --- Menu Items ---
    private JMenuItem undoMenuItem;
    private JMenuItem redoMenuItem;

    public PrimaryFrame() {
        setUndecorated(true);

        setTitle("Eco Chart Pro");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1220, 720);
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon(getClass().getResource(UITheme.Icons.APP_LOGO)).getImage());

        // Initialize contexts first, as they are needed by menu setup
        replayContext = new WorkspaceContext();
        liveContext = new WorkspaceContext();

        this.titleBarManager = new TitleBarManager(this);
        this.titleBarManager.setMenuBar(createHomeMenuBar());

        replayWorkspacePanel = new ChartWorkspacePanel(this, true, replayContext);
        liveWorkspacePanel = new ChartWorkspacePanel(this, false, liveContext);

        boolean isConnected = InternetConnectivityService.getInstance().isConnected();
        liveWorkspacePanel.setOfflineMode(!isConnected);

        // --- Rearchitect the Home Tab ---
        analysisReportPanel = new ComprehensiveReportPanel();
        DashboardViewPanel splashPanel = new DashboardViewPanel();
        this.reportPanelContainer = createAnalysisReportPanel();
        this.reportPanelContainer.setVisible(false); // Hide until data is loaded

        // Use a ScrollablePanel to force width tracking
        JPanel homeContainerPanel = new ScrollablePanel(new BorderLayout());
        homeContainerPanel.add(splashPanel, BorderLayout.NORTH);
        homeContainerPanel.add(this.reportPanelContainer, BorderLayout.CENTER);

        JScrollPane homeScrollPane = new JScrollPane(homeContainerPanel);
        homeScrollPane.setBorder(null);
        // [FIX] Increase scroll speed here
        homeScrollPane.getVerticalScrollBar().setUnitIncrement(40);
        homeScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        mainContentPanel.add(homeScrollPane, "HOME");
        mainContentPanel.add(replayWorkspacePanel, "REPLAY");
        mainContentPanel.add(liveWorkspacePanel, "LIVE");

        add(titleBarManager, BorderLayout.NORTH);
        add(mainContentPanel, BorderLayout.CENTER);

        replayContext.getSessionTracker().addPropertyChangeListener(this);
        liveContext.getSessionTracker().addPropertyChangeListener(this);
        replayContext.getUndoManager().addPropertyChangeListener(this);
        liveContext.getUndoManager().addPropertyChangeListener(this);
    }

    // [FIX] Updated ScrollablePanel to return a larger unit increment
    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 40; // Increased from 16 for faster scrolling
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public JMenuBar createHomeMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    public JMenuBar createReplayMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createReplayFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createToolsMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    public JMenuBar createLiveMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(createLiveFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createToolsMenu());
        menuBar.add(createHelpMenu());
        return menuBar;
    }

    private JMenu createReplayFileMenu() {
        JMenu fileMenu = new JMenu("File");
        SessionController sc = SessionController.getInstance();

        JMenuItem newReplayItem = new JMenuItem("New Replay Session...");
        newReplayItem.addActionListener(e -> sc.showNewReplaySessionDialog(this));
        fileMenu.add(newReplayItem);

        JMenuItem loadReplayItem = new JMenuItem("Load Replay Session...");
        loadReplayItem.addActionListener(e -> sc.loadReplaySessionFromFile(this));
        fileMenu.add(loadReplayItem);

        JMenuItem saveReplayItem = new JMenuItem("Save Replay Session As...");
        saveReplayItem.addActionListener(e -> sc.saveSessionWithUI(this, true, replayContext));
        fileMenu.add(saveReplayItem);

        return fileMenu;
    }

    private JMenu createLiveFileMenu() {
        JMenu fileMenu = new JMenu("File");
        SessionController sc = SessionController.getInstance();

        JMenuItem newLiveItem = new JMenuItem("New Live Session...");
        newLiveItem.addActionListener(e -> sc.showNewLiveSessionDialog(this));
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
        undoMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undoMenuItem.addActionListener(e -> getActiveContext().getUndoManager().undo());

        redoMenuItem = new JMenuItem("Redo");
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
        KeyStroke redoKeyStroke = isMac
                ? KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                        Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK)
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
        if (undoMenuItem == null || redoMenuItem == null)
            return;
        WorkspaceContext activeContext = getActiveContext();
        undoMenuItem.setEnabled(activeContext.getUndoManager().canUndo());
        redoMenuItem.setEnabled(activeContext.getUndoManager().canRedo());
    }

    private WorkspaceContext getActiveContext() {
        if (titleBarManager == null || titleBarManager.getReplayNavButton() == null) {
            return liveContext;
        }

        if (titleBarManager.getReplayNavButton().isSelected()) {
            return replayContext;
        } else if (titleBarManager.getLiveNavButton().isSelected()) {
            return liveContext;
        }
        return liveContext;
    }

    private ChartWorkspacePanel getActiveWorkspacePanel() {
        if (titleBarManager == null || titleBarManager.getReplayNavButton() == null) {
            return liveWorkspacePanel;
        }

        if (titleBarManager.getReplayNavButton().isSelected()) {
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

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(analysisReportPanel, BorderLayout.CENTER);
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
            if (evt.getSource() == getActiveContext().getUndoManager()) {
                updateUndoRedoState();
            }
        }
    }

    private void updateAnalysisReport() {
        SwingUtilities.invokeLater(() -> {
            if (!reportPanelContainer.isVisible()) {
                reportPanelContainer.setVisible(true);
            }
            if (replayReportButton.isSelected()) {
                analysisReportPanel.updateData(lastReplayState);
            } else if (liveReportButton.isSelected()) {
                analysisReportPanel.updateData(lastLiveState);
            }
        });
    }

    public WorkspaceContext getReplayContext() {
        return replayContext;
    }

    public WorkspaceContext getLiveContext() {
        return liveContext;
    }

    public JPanel getMainContentPanel() {
        return mainContentPanel;
    }

    public CardLayout getMainCardLayout() {
        return mainCardLayout;
    }

    public TitleBarManager getTitleBarManager() {
        return this.titleBarManager;
    }

    public ChartWorkspacePanel getReplayWorkspacePanel() {
        return replayWorkspacePanel;
    }

    public ChartWorkspacePanel getLiveWorkspacePanel() {
        return liveWorkspacePanel;
    }
}