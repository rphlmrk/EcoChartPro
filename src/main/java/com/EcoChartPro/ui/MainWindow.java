package com.EcoChartPro.ui;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.controller.LiveWindowManager;
import com.EcoChartPro.core.controller.ReplayController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.settings.SettingsService; // MODIFIED
import com.EcoChartPro.core.settings.config.DrawingConfig; // MODIFIED
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.ui.action.KeyboardShortcutManager;
import com.EcoChartPro.ui.action.MenuBarManager;
import com.EcoChartPro.ui.action.TitleBarManager;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.OnFireStreakWidget;
import com.EcoChartPro.ui.components.ConnectionStatusWidget;
import com.EcoChartPro.ui.components.StopTradingNudgeWidget;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.sidebar.TradingSidebarPanel;
import com.EcoChartPro.ui.toolbar.ChartToolbarPanel;
import com.EcoChartPro.ui.toolbar.FloatingDrawingToolbar;
import com.EcoChartPro.ui.toolbar.FloatingPropertiesToolbar;
import com.EcoChartPro.ui.toolbar.ReplayControlPanel;
import com.EcoChartPro.ui.trading.JournalEntryDialog;
import com.EcoChartPro.ui.trading.OrderDialog;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class MainWindow extends JFrame implements PropertyChangeListener {

    // --- Core UI Components ---
    private final JLayeredPane rootPanel;
    private final JPanel mainContainerPanel;
    private final ChartToolbarPanel topToolbarPanel;
    private final FloatingDrawingToolbar drawingToolbar;
    private final FloatingPropertiesToolbar propertiesToolbar;
    private TradingSidebarPanel tradingSidebar;
    private final OnFireStreakWidget onFireWidget;
    private final StopTradingNudgeWidget stopTradingNudgeWidget;
    private final ConnectionStatusWidget connectionStatusWidget;

    // --- Controllers & Managers ---
    private ReplayController replayController;
    private final WorkspaceManager workspaceManager;
    private final UIManager uiManager;
    private final SessionController sessionController;
    private final TitleBarManager titleBarManager;
    private final KeyboardShortcutManager keyboardShortcutManager;
    private final MenuBarManager menuBarManager;
    private DatabaseManager activeDbManager;

    // --- State Holders ---
    private final boolean isReplayMode;
    // Menu bar status labels
    private final JLabel connectivityStatusLabel;
    private final JLabel latencyLabel;

    public MainWindow(boolean isReplayMode) {
        super();
        this.isReplayMode = isReplayMode;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 720);
        setLocationRelativeTo(null);

        this.titleBarManager = new TitleBarManager(this);
        this.sessionController = SessionController.getInstance();
        this.uiManager = new UIManager(this);
        this.workspaceManager = new WorkspaceManager(this);

        rootPanel = new JLayeredPane();
        mainContainerPanel = new JPanel(new BorderLayout());
        this.topToolbarPanel = new ChartToolbarPanel(isReplayMode, this.workspaceManager);
        this.drawingToolbar = new FloatingDrawingToolbar(this);
        this.propertiesToolbar = new FloatingPropertiesToolbar(this);
        this.onFireWidget = new OnFireStreakWidget();
        this.stopTradingNudgeWidget = new StopTradingNudgeWidget();
        this.connectionStatusWidget = new ConnectionStatusWidget();

        this.menuBarManager = new MenuBarManager(this, isReplayMode);
        MenuBarManager.MenuBarResult menuResult = this.menuBarManager.createMenuBar();
        setJMenuBar((JMenuBar) menuResult.menu());
        this.connectivityStatusLabel = menuResult.connectivityStatusLabel();
        this.latencyLabel = menuResult.latencyLabel();

        // --- Add trading buttons to the top panel for Live Mode ---
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(this.topToolbarPanel, BorderLayout.CENTER);

        // In live mode, the toolbar may not have trading buttons, so we add them here to ensure they are available.
        if (!isReplayMode) {
            JPanel tradingButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            tradingButtonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10)); // Add some padding

            JButton buyButton = new JButton("Buy");
            buyButton.setActionCommand("placeLongOrder");
            buyButton.setBackground(new Color(0x4CAF50)); // Green for buy
            buyButton.setForeground(Color.WHITE);
            buyButton.setFocusPainted(false);
            buyButton.addActionListener(e -> handleTradeAction("placeLongOrder"));

            JButton sellButton = new JButton("Sell");
            sellButton.setActionCommand("placeShortOrder");
            sellButton.setBackground(new Color(0xF44336)); // Red for sell
            sellButton.setForeground(Color.WHITE);
            sellButton.setFocusPainted(false);
            sellButton.addActionListener(e -> handleTradeAction("placeShortOrder"));

            tradingButtonsPanel.add(buyButton);
            tradingButtonsPanel.add(sellButton);
            
            northPanel.add(tradingButtonsPanel, BorderLayout.EAST);
        }
        mainContainerPanel.add(northPanel, BorderLayout.NORTH);

        if (isReplayMode) {
            setupReplayMode();
        } else {
            setupLiveMode();
        }
        
        mainContainerPanel.add(workspaceManager.getChartAreaPanel(), BorderLayout.CENTER);
        
        rootPanel.add(mainContainerPanel, JLayeredPane.DEFAULT_LAYER);
        rootPanel.add(onFireWidget, JLayeredPane.PALETTE_LAYER);
        rootPanel.add(stopTradingNudgeWidget, JLayeredPane.PALETTE_LAYER);
        rootPanel.add(connectionStatusWidget, JLayeredPane.PALETTE_LAYER);
        setContentPane(rootPanel);
        
        addWindowListeners(isReplayMode);
        addPropertyChangeListeners();
        
        updateMenuBarConnectivityStatus(InternetConnectivityService.getInstance().isConnected());
        
        this.keyboardShortcutManager = new KeyboardShortcutManager(rootPanel, this);
        this.keyboardShortcutManager.setup();
        
        titleBarManager.start();
    }

    private void repositionOverlayWidgets() {
        // Center-top for streak/nudge widgets
        if (onFireWidget.isVisible()) {
            Dimension fireSize = onFireWidget.getPreferredSize();
            onFireWidget.setBounds((rootPanel.getWidth() - fireSize.width) / 2, 20, fireSize.width, fireSize.height);
        }
        if (stopTradingNudgeWidget.isVisible()) {
            Dimension nudgeSize = stopTradingNudgeWidget.getPreferredSize();
            stopTradingNudgeWidget.setBounds((rootPanel.getWidth() - nudgeSize.width) / 2, 20, nudgeSize.width, nudgeSize.height);
        }

        // Top-right for connection status widget
        if (connectionStatusWidget.isVisible()) {
            ChartPanel activeChart = getActiveChartPanel();
            if (activeChart != null) {
                Point chartPos = SwingUtilities.convertPoint(activeChart, 0, 0, rootPanel);
                Dimension widgetSize = connectionStatusWidget.getPreferredSize();
                int x = chartPos.x + activeChart.getWidth() - widgetSize.width - 20;
                int y = chartPos.y + 20;
                connectionStatusWidget.setBounds(x, y, widgetSize.width, widgetSize.height);
            }
        }
    }
    
    private void updateComponentLayouts() {
        mainContainerPanel.setBounds(0, 0, rootPanel.getWidth(), rootPanel.getHeight());

        if (drawingToolbar.isVisible()) {
            drawingToolbar.updatePosition(SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                    ? FloatingDrawingToolbar.DockSide.LEFT
                    : FloatingDrawingToolbar.DockSide.RIGHT);
        }
        repositionOverlayWidgets();
    }

    private void addWindowListeners(boolean isReplayMode) {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sessionController.handleWindowClose(MainWindow.this, isReplayMode);
            }
            @Override
            public void windowClosed(WindowEvent e) {
                cleanupResources();
            }
        });
        
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateComponentLayouts();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                updateComponentLayouts();
            }
        });
    }

    private void addPropertyChangeListeners() {
        SettingsService.getInstance().addPropertyChangeListener(this);
        DrawingManager.getInstance().addPropertyChangeListener("activeSymbolChanged", this);
        
        LiveSessionTrackerService.getInstance().addPropertyChangeListener(this);
        PaperTradingService.getInstance().addPropertyChangeListener(this);
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);
        LiveDataManager.getInstance().addPropertyChangeListener("realLatencyUpdated", this);
        LiveDataManager.getInstance().addPropertyChangeListener("liveDataSystemStateChanged", this);
    }

    private void cleanupResources() {
        if (activeDbManager != null) {
            activeDbManager.close();
            activeDbManager = null;
        }
        workspaceManager.getChartPanels().forEach(ChartPanel::cleanup);
        if (replayController != null) {
            ReplaySessionManager.getInstance().removeListener(replayController);
        }
        SettingsService.getInstance().removePropertyChangeListener(this);
        DrawingManager.getInstance().removePropertyChangeListener(this);
        
        LiveSessionTrackerService.getInstance().removePropertyChangeListener(this);
        PaperTradingService.getInstance().removePropertyChangeListener(this);
        InternetConnectivityService.getInstance().removePropertyChangeListener(this);
        LiveDataManager.getInstance().removePropertyChangeListener("realLatencyUpdated", this);
        LiveDataManager.getInstance().removePropertyChangeListener("liveDataSystemStateChanged", this);

        uiManager.disposeDialogs();
        titleBarManager.dispose();
        keyboardShortcutManager.dispose();
        drawingToolbar.dispose();
        propertiesToolbar.dispose();
        topToolbarPanel.dispose();
        menuBarManager.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(() -> {
            switch (evt.getPropertyName()) {
                case "indicatorAdded":
                    Indicator indicator = (Indicator) evt.getNewValue();
                    if (indicator.getType() == IndicatorType.PANE) {
                        workspaceManager.addIndicatorPane(indicator);
                    }
                    break;
                case "indicatorRemoved":
                    workspaceManager.removeIndicatorPane((UUID) evt.getNewValue());
                    break;
                case "drawingToolbarPositionChanged":
                    FloatingDrawingToolbar.DockSide newSide = 
                        (DrawingConfig.ToolbarPosition) evt.getNewValue() == DrawingConfig.ToolbarPosition.LEFT
                            ? FloatingDrawingToolbar.DockSide.LEFT
                            : FloatingDrawingToolbar.DockSide.RIGHT;
                    drawingToolbar.forceUpdatePosition(newSide);
                    break;
                case "activeSymbolChanged":
                    workspaceManager.getChartPanels().forEach(ChartPanel::repaint);
                    break;
                case "sessionStreakUpdated":
                    if (SettingsService.getInstance().isWinStreakNudgeEnabled() && evt.getNewValue() instanceof Integer count) {
                        if (count >= 3) {
                            onFireWidget.showStreak(count);
                            repositionOverlayWidgets();
                        } else {
                            onFireWidget.hideStreak();
                        }
                    }
                    break;
                case "sessionLossStreakUpdated":
                     if (SettingsService.getInstance().isLossStreakNudgeEnabled() && evt.getNewValue() instanceof Integer count) {
                        if (count >= 3) {
                            stopTradingNudgeWidget.showNudge(count);
                        } else {
                            stopTradingNudgeWidget.hideNudge();
                        }
                    }
                    break;
                case "tradeClosedForJournaling":
                    if (evt.getNewValue() instanceof Trade closedTrade) {
                        launchJournalDialogForTrade(closedTrade);
                    }
                    break;
                case "connectivityChanged":
                    updateMenuBarConnectivityStatus((boolean) evt.getNewValue());
                    if (isReplayMode) {
                        updateForGeneralConnectivity((boolean) evt.getNewValue());
                    }
                    break;
                case "liveDataSystemStateChanged":
                    if (evt.getNewValue() instanceof LiveDataManager.LiveDataSystemState state) {
                        updateConnectionWidget(state);
                    }
                    break;
                case "realLatencyUpdated":
                    if (evt.getNewValue() instanceof Long newLatency) {
                        latencyLabel.setText(newLatency + " ms");
                        if (newLatency < 100) {
                            latencyLabel.setForeground(javax.swing.UIManager.getColor("app.color.positive"));
                        } else if (newLatency < 300) {
                            latencyLabel.setForeground(javax.swing.UIManager.getColor("app.trading.pending")); // Amber/Orange
                        } else {
                            latencyLabel.setForeground(javax.swing.UIManager.getColor("app.color.negative"));
                        }
                    }
                    break;
            }
        });
    }

    private void updateMenuBarConnectivityStatus(boolean isConnected) {
        if (isConnected) {
            connectivityStatusLabel.setIcon(UITheme.getIcon(UITheme.Icons.WIFI_ON, 16, 16, javax.swing.UIManager.getColor("app.color.positive")));
        } else {
            connectivityStatusLabel.setIcon(UITheme.getIcon(UITheme.Icons.WIFI_OFF, 16, 16, javax.swing.UIManager.getColor("app.color.negative")));
            latencyLabel.setText("-- ms");
            latencyLabel.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        }
    }

    private void updateConnectionWidget(LiveDataManager.LiveDataSystemState state) {
        if (isReplayMode) {
            return;
        }

        switch (state) {
            case INTERRUPTED:
                connectionStatusWidget.showStatus(
                    "Connection Lost... Reconnecting",
                    UITheme.Icons.WIFI_OFF,
                    javax.swing.UIManager.getColor("app.color.negative")
                );
                break;
            case SYNCING:
                connectionStatusWidget.showStatus(
                    "Connection Restored. Syncing Data...",
                    UITheme.Icons.REFRESH,
                    javax.swing.UIManager.getColor("Component.focusedBorderColor")
                );
                break;
            case CONNECTED:
                connectionStatusWidget.hideStatus();
                break;
        }
        repositionOverlayWidgets();
    }

    private void updateForGeneralConnectivity(boolean isConnected) {
        if (isConnected) {
            connectionStatusWidget.hideStatus();
        } else {
            connectionStatusWidget.showStatus(
                "Internet Connection Lost",
                UITheme.Icons.WIFI_OFF,
                javax.swing.UIManager.getColor("app.trading.pending") // Amber/yellow warning
            );
        }
        repositionOverlayWidgets();
    }


    private void launchJournalDialogForTrade(Trade trade) {
        JournalEntryDialog dialog = new JournalEntryDialog(this, trade);
        dialog.setVisible(true);
    }
    
    public void applyLiveIndicator(CustomIndicator plugin) {
        if (workspaceManager.getActiveChartPanel() != null) {
            ChartDataModel model = workspaceManager.getActiveChartPanel().getDataModel();
            model.getIndicatorManager().addOrUpdateFromLiveCode(plugin, model);
        } else {
            JOptionPane.showMessageDialog(this, "No active chart to apply indicator to.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // --- Public Getters for Managers ---
    public ChartPanel getActiveChartPanel() { return workspaceManager.getActiveChartPanel(); }
    public FloatingDrawingToolbar getDrawingToolbar() { return drawingToolbar; }
    public FloatingPropertiesToolbar getPropertiesToolbar() { return propertiesToolbar; }
    public ChartToolbarPanel getTopToolbarPanel() { return topToolbarPanel; }
    public Optional<TradingSidebarPanel> getTradingSidebar() { return Optional.ofNullable(tradingSidebar); }
    public Optional<ReplayController> getReplayController() { return Optional.ofNullable(replayController); }
    public WorkspaceManager getWorkspaceManager() { return workspaceManager; }
    public UIManager getUiManager() { return uiManager; }
    public SessionController getSessionController() { return sessionController; }
    public TitleBarManager getTitleBarManager() { return titleBarManager; }
    public DatabaseManager getActiveDbManager() { return activeDbManager; }

    public ChartDataSource getCurrentSource() {
        return topToolbarPanel.getSelectedDataSource();
    }
    
    public void openNewSyncedWindow() {
        if (isReplayMode) {
            if (ReplaySessionManager.getInstance().getCurrentSource() != null) {
                SwingUtilities.invokeLater(() -> {
                    MainWindow newSyncedWindow = new MainWindow(true);
                    newSyncedWindow.joinReplaySession();
                });
            } else {
                JOptionPane.showMessageDialog(this, "A replay session must be active to open a new synced window.", "No Active Session", JOptionPane.INFORMATION_MESSAGE);
            }
        } else { // Live mode
            if (LiveWindowManager.getInstance().isActive()) {
                SwingUtilities.invokeLater(() -> {
                    MainWindow newSyncedWindow = new MainWindow(false);
                    newSyncedWindow.joinLiveSession();
                });
            } else {
                 JOptionPane.showMessageDialog(this, "A live session must be active to open a new synced window.", "No Active Session", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    public void joinReplaySession() {
        ChartDataSource source = ReplaySessionManager.getInstance().getCurrentSource();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No active replay session to join.", "Error", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }
        DrawingManager.getInstance().setActiveSymbol(source.symbol());
        titleBarManager.setStaticTitle("(Synced)");
        setDbManagerForSource(source);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
            workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        }
        setVisible(true);
    }

    public void joinLiveSession() {
        ChartDataSource source = LiveWindowManager.getInstance().getActiveDataSource();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No active live session to join.", "Error", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        this.titleBarManager.setStaticTitle("(Synced Live)");
        this.loadChartForSource(source);
        this.setVisible(true);
    }

    private void setupLiveMode() {
        this.tradingSidebar = new TradingSidebarPanel();
        this.tradingSidebar.addPropertyChangeListener(this::handleSidebarEvents);
        mainContainerPanel.add(this.tradingSidebar, BorderLayout.WEST);
        
        workspaceManager.initializeStandardMode();
        addTopToolbarListeners();
    }

    private void setupReplayMode() {
        this.tradingSidebar = new TradingSidebarPanel();
        this.tradingSidebar.addPropertyChangeListener(this::handleSidebarEvents);
        mainContainerPanel.add(this.tradingSidebar, BorderLayout.WEST);
        this.replayController = new ReplayController();
        ReplayControlPanel replayControlPanel = new ReplayControlPanel(this.replayController);
        mainContainerPanel.add(replayControlPanel, BorderLayout.SOUTH);
        workspaceManager.initializeReplayMode();
        addTopToolbarListeners();
    }
    
    private void handleSidebarEvents(PropertyChangeEvent evt) {
        if ("jumpToTrade".equals(evt.getPropertyName()) && evt.getNewValue() instanceof Trade) {
            Trade trade = (Trade) evt.getNewValue();
            if (workspaceManager.getActiveChartPanel() != null) {
                workspaceManager.getActiveChartPanel().getDataModel().centerOnTrade(trade);
            }
        }
        if ("sidebarToggled".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                if (drawingToolbar.isVisible()) {
                    drawingToolbar.updatePosition(SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                            ? FloatingDrawingToolbar.DockSide.LEFT
                            : FloatingDrawingToolbar.DockSide.RIGHT);
                }
            });
        }
    }

    public void setDbManagerForSource(ChartDataSource source) {
        DatabaseManager oldDbManager = this.activeDbManager;
    
        if (source == null || source.dbPath() == null) {
            this.activeDbManager = null;
        } else {
            String jdbcUrl = "jdbc:sqlite:" + source.dbPath().toAbsolutePath();
            this.activeDbManager = new DatabaseManager(jdbcUrl);
        }
    
        for (ChartPanel panel : workspaceManager.getChartPanels()) {
            panel.getDataModel().setDatabaseManager(this.activeDbManager, source);
        }
        
        topToolbarPanel.setCurrentSymbol(source);
    
        if (oldDbManager != null) {
            oldDbManager.close();
        }
    }

    public void startLiveSession(DataSourceManager.ChartDataSource source) {
        PaperTradingService.getInstance().switchActiveSymbol(source.symbol());
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        loadChartForSource(source);
        setVisible(true);
    }

    public void startReplaySession(DataSourceManager.ChartDataSource source, int startIndex) {
        ReplaySessionManager.getInstance().startSession(source, startIndex);
        setDbManagerForSource(source); 
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
        workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        setVisible(true);
    }

    public void loadSessionState(ReplaySessionState state) {
        DrawingManager.getInstance().clearAllDrawingsForAllSymbols();
        
        if (isReplayMode) {
            ReplaySessionManager.getInstance().startSessionFromState(state);
        }

        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();
        if (sourceOpt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Data source for symbol '" + state.lastActiveSymbol() + "' not found.", "Load Error", JOptionPane.ERROR_MESSAGE);
            dispose(); return;
        }
        
        setDbManagerForSource(sourceOpt.get());
        
        if (state.symbolStates() != null) {
            state.symbolStates().forEach((symbol, symbolState) -> 
                DrawingManager.getInstance().restoreDrawingsForSymbol(symbol, symbolState.drawings())
            );
        }
        
        PaperTradingService.getInstance().restoreState(state);
        
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            if (!isReplayMode) {
                loadChartForSource(sourceOpt.get());
            } else {
                workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
            }
            workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        }
        setVisible(true);
    }

    private void handleTradeAction(String command) {
        if (workspaceManager.getActiveChartPanel() == null || workspaceManager.getActiveChartPanel().getDataModel().getCurrentSymbol() == null) {
            JOptionPane.showMessageDialog(this, "Please select an active chart.", "No Chart Active", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TradeDirection direction = "placeLongOrder".equals(command) ? TradeDirection.LONG : TradeDirection.SHORT;
        new OrderDialog(this, workspaceManager.getActiveChartPanel(), direction).setVisible(true);
    }

    private void addTopToolbarListeners() {
        topToolbarPanel.addActionListener(e -> {
            String command = e.getActionCommand();
            ChartPanel activePanel = workspaceManager.getActiveChartPanel();

            if ("selectionChanged".equals(command)) {
                DataSourceManager.ChartDataSource newSource = topToolbarPanel.getSelectedDataSource();
                if (isReplayMode) {
                    handleReplaySymbolChange();
                } else {
                    loadChartForSource(newSource);
                }
            } 
            else if (command.startsWith("timeframeChanged")) {
                Timeframe newTimeframe = null;
                if (e.getSource() instanceof Timeframe) {
                    newTimeframe = (Timeframe) e.getSource();
                } else {
                    String tfString = command.substring("timeframeChanged:".length());
                    newTimeframe = Timeframe.fromString(tfString);
                }

                if (newTimeframe != null) {
                    // This is now handled by the toolbar's internal listener
                    // topToolbarPanel.selectTimeframe(newTimeframe.displayName());
                    
                    if (activePanel != null) {
                        activePanel.getDataModel().setDisplayTimeframe(newTimeframe);
                    } else if (!isReplayMode && !workspaceManager.getChartPanels().isEmpty()) {
                        workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(newTimeframe);
                    }
                }
            } 
            else if (command.startsWith("layoutChanged:")) {
                try {
                    String layoutName = command.substring("layoutChanged:".length());
                    WorkspaceManager.LayoutType type = WorkspaceManager.LayoutType.valueOf(layoutName);
                    workspaceManager.applyLayout(type);
                } catch (IllegalArgumentException ex) {
                    System.err.println("Invalid layout type received: " + command);
                }
            } 
            else if (command.startsWith("chartTypeChanged:")) {
                if (activePanel != null) {
                    try {
                        String typeName = command.substring("chartTypeChanged:".length());
                        ChartType type = ChartType.valueOf(typeName);
                        activePanel.setChartType(type);
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid chart type received: " + command);
                    }
                }
            }
            else if ("placeLongOrder".equals(command) || "placeShortOrder".equals(command)) {
                handleTradeAction(command);
            }
        });
    }

    private void handleReplaySymbolChange() {
        DataSourceManager.ChartDataSource newSource = topToolbarPanel.getSelectedDataSource();
        DataSourceManager.ChartDataSource currentSource = ReplaySessionManager.getInstance().getCurrentSource();

        if (newSource == null || (currentSource != null && newSource.symbol().equals(currentSource.symbol()))) {
            return;
        }

        ReplaySessionManager.getInstance().switchActiveSymbol(newSource.symbol());
        setDbManagerForSource(newSource);
        
        Timeframe newTimeframe = workspaceManager.getActiveChartPanel() != null
                ? workspaceManager.getActiveChartPanel().getDataModel().getCurrentDisplayTimeframe()
                : Timeframe.M5;

        topToolbarPanel.populateTimeframes(newSource.timeframes());
        topToolbarPanel.selectTimeframe(newTimeframe.displayName());
        
        for (ChartPanel panel : workspaceManager.getChartPanels()) {
            panel.getDataModel().configureForReplay(newTimeframe, newSource);
            panel.getDataModel().setDisplayTimeframe(newTimeframe, true);
        }
        
    }

    private void loadChartForSource(DataSourceManager.ChartDataSource source) {
        if (source == null) return;
        if (source.dbPath() != null) {
            setDbManagerForSource(source);
        } else {
            setDbManagerForSource(null);
        }
        PaperTradingService.getInstance().switchActiveSymbol(source.symbol());
        DrawingManager.getInstance().setActiveSymbol(source.symbol());
        topToolbarPanel.populateTimeframes(source.timeframes());

        if (!source.timeframes().isEmpty()) {
            String initialTimeframeStr = source.timeframes().get(0);
            Timeframe initialTimeframe = Timeframe.fromString(initialTimeframeStr);
            if (initialTimeframe == null) initialTimeframe = Timeframe.H1;

            topToolbarPanel.selectTimeframe(initialTimeframe.displayName());
            for (ChartPanel panel : workspaceManager.getChartPanels()) {
                 panel.getDataModel().loadDataset(source, initialTimeframe);
            }
        } else {
             if (!workspaceManager.getChartPanels().isEmpty()) {
                workspaceManager.getChartPanels().get(0).getDataModel().clearData();
             }
        }
    }

    public void changeActiveSymbol(DataSourceManager.ChartDataSource newSource) {
        if (newSource == null) return;

        topToolbarPanel.setCurrentSymbol(newSource);

        if (isReplayMode) {
            handleReplaySymbolChange();
        } else {
            loadChartForSource(newSource);
        }
    }
}