package com.EcoChartPro.ui;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.controller.LiveWindowManager;
import com.EcoChartPro.core.controller.ReplayController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig;
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
import com.EcoChartPro.ui.action.TitleBarManager;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.LiveStatusBar; // [NEW]
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
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ChartWorkspacePanel extends JPanel implements PropertyChangeListener {

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

    // [NEW] Bottom status bar for Live Mode
    private LiveStatusBar liveStatusBar;

    // --- Controllers & Managers ---
    private ReplayController replayController;
    private ReplayControlPanel replayControlPanel;
    private final WorkspaceManager workspaceManager;
    private final UIManager uiManager;
    private final SessionController sessionController;
    private final KeyboardShortcutManager keyboardShortcutManager;
    private final WorkspaceContext workspaceContext;
    private DatabaseManager activeDbManager;

    // --- State Holders ---
    private final boolean isReplayMode;
    private final Frame owner;

    public ChartWorkspacePanel(Frame owner, boolean isReplayMode, WorkspaceContext context) {
        super(new BorderLayout());
        this.owner = owner;
        this.isReplayMode = isReplayMode;
        this.workspaceContext = context;

        this.sessionController = SessionController.getInstance();
        this.uiManager = new UIManager(this);
        this.workspaceManager = new WorkspaceManager(this, this.workspaceContext);

        rootPanel = new JLayeredPane();
        mainContainerPanel = new JPanel(new BorderLayout());
        this.topToolbarPanel = new ChartToolbarPanel(this, this.workspaceManager);
        this.drawingToolbar = new FloatingDrawingToolbar(this);
        this.propertiesToolbar = new FloatingPropertiesToolbar(this);
        this.onFireWidget = new OnFireStreakWidget();
        this.stopTradingNudgeWidget = new StopTradingNudgeWidget();
        this.connectionStatusWidget = new ConnectionStatusWidget();

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(this.topToolbarPanel, BorderLayout.CENTER);

        if (!isReplayMode) {
            JPanel tradingButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            tradingButtonsPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 10));
            JButton buyButton = new JButton("Buy");
            buyButton.setActionCommand("placeLongOrder");
            buyButton.setBackground(new Color(0x4CAF50));
            buyButton.setForeground(Color.WHITE);
            buyButton.setFocusPainted(false);
            buyButton.addActionListener(e -> handleTradeAction("placeLongOrder"));
            JButton sellButton = new JButton("Sell");
            sellButton.setActionCommand("placeShortOrder");
            sellButton.setBackground(new Color(0xF44336));
            sellButton.setForeground(Color.WHITE);
            sellButton.setFocusPainted(false);
            sellButton.addActionListener(e -> handleTradeAction("placeShortOrder"));
            tradingButtonsPanel.add(buyButton);
            tradingButtonsPanel.add(sellButton);
            northPanel.add(tradingButtonsPanel, BorderLayout.EAST);

            // [NEW] Add Live Status Bar at the bottom for Live Mode
            this.liveStatusBar = new LiveStatusBar();
            mainContainerPanel.add(this.liveStatusBar, BorderLayout.SOUTH);
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
        // Only add connection status widget (the large overlay) in Replay Mode or as a
        // fallback
        // In Live Mode, the status bar handles connection info.
        if (isReplayMode) {
            rootPanel.add(connectionStatusWidget, JLayeredPane.PALETTE_LAYER);
        }

        add(rootPanel, BorderLayout.CENTER);

        addPropertyChangeListeners();

        this.keyboardShortcutManager = new KeyboardShortcutManager(rootPanel, this, this.workspaceContext);
        this.keyboardShortcutManager.setup();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateComponentLayouts();
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                // [FIX] When this workspace is hidden by CardLayout, hide its floating
                // toolbars.
                drawingToolbar.setVisible(false);
                propertiesToolbar.setVisible(false);
            }
        });

        if (!isReplayMode) {
            this.workspaceContext.getSessionTracker().start();
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateComponentLayouts();
    }

    private void repositionOverlayWidgets() {
        if (onFireWidget.isVisible()) {
            Dimension fireSize = onFireWidget.getPreferredSize();
            onFireWidget.setBounds((rootPanel.getWidth() - fireSize.width) / 2, 20, fireSize.width, fireSize.height);
        }
        if (stopTradingNudgeWidget.isVisible()) {
            Dimension nudgeSize = stopTradingNudgeWidget.getPreferredSize();
            stopTradingNudgeWidget.setBounds((rootPanel.getWidth() - nudgeSize.width) / 2, 20, nudgeSize.width,
                    nudgeSize.height);
        }
        // Only position the large overlay widget if it's actually added (Replay mode)
        if (connectionStatusWidget.isVisible() && connectionStatusWidget.getParent() == rootPanel) {
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
        if (rootPanel != null && mainContainerPanel != null) {
            mainContainerPanel.setBounds(0, 0, rootPanel.getWidth(), rootPanel.getHeight());
            if (drawingToolbar.isVisible()) {
                drawingToolbar.updatePosition(
                        SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                                ? FloatingDrawingToolbar.DockSide.LEFT
                                : FloatingDrawingToolbar.DockSide.RIGHT);
            }
            repositionOverlayWidgets();
        }
    }

    public void setOfflineMode(boolean isOffline) {
        if (isReplayMode) {
            return;
        }

        // In Live Mode, the LiveStatusBar handles visual feedback.
        // The large overlay widget is disabled for Live Mode to avoid redundancy.
        if (isOffline) {
            // Optional: You could trigger a non-intrusive notification here if desired
        }

        // The status bar listens to connectivity events directly, so no manual update
        // needed here.
    }

    public void handleCloseRequest() {
        sessionController.handleWindowClose(this.owner, isReplayMode, this.workspaceContext);
    }

    private void addPropertyChangeListeners() {
        SettingsService.getInstance().addPropertyChangeListener(this);
        workspaceContext.getDrawingManager().addPropertyChangeListener("activeSymbolChanged", this);

        workspaceContext.getSessionTracker().addPropertyChangeListener(this);
        workspaceContext.getPaperTradingService().addPropertyChangeListener(this);

        // We still listen to connectivity here for other logic, even if the widget is
        // gone
        InternetConnectivityService.getInstance().addPropertyChangeListener(this);
        LiveDataManager.getInstance().addPropertyChangeListener("liveDataSystemStateChanged", this);
    }

    public void dispose() {
        if (activeDbManager != null) {
            activeDbManager.close();
            activeDbManager = null;
        }
        workspaceManager.getChartPanels().forEach(ChartPanel::cleanup);
        if (replayController != null) {
            ReplaySessionManager.getInstance().removeListener(replayController);
        }
        SettingsService.getInstance().removePropertyChangeListener(this);
        workspaceContext.getDrawingManager().removePropertyChangeListener(this);

        workspaceContext.getSessionTracker().removePropertyChangeListener(this);
        if (!isReplayMode) {
            this.workspaceContext.getSessionTracker().stop();
            // [NEW] Clean up status bar
            if (liveStatusBar != null) {
                liveStatusBar.dispose();
            }
        }

        workspaceContext.getPaperTradingService().removePropertyChangeListener(this);
        InternetConnectivityService.getInstance().removePropertyChangeListener(this);
        LiveDataManager.getInstance().removePropertyChangeListener("liveDataSystemStateChanged", this);

        uiManager.disposeDialogs();
        keyboardShortcutManager.dispose();
        drawingToolbar.dispose();
        propertiesToolbar.dispose();
        topToolbarPanel.dispose();
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
                    FloatingDrawingToolbar.DockSide newSide = (DrawingConfig.ToolbarPosition) evt
                            .getNewValue() == DrawingConfig.ToolbarPosition.LEFT
                                    ? FloatingDrawingToolbar.DockSide.LEFT
                                    : FloatingDrawingToolbar.DockSide.RIGHT;
                    drawingToolbar.forceUpdatePosition(newSide);
                    break;
                case "activeSymbolChanged":
                    workspaceManager.getChartPanels().forEach(ChartPanel::repaint);
                    break;
                case "sessionStreakUpdated":
                    if (SettingsService.getInstance().isWinStreakNudgeEnabled()
                            && evt.getNewValue() instanceof Integer count) {
                        if (count >= 3) {
                            onFireWidget.showStreak(count);
                            repositionOverlayWidgets();
                        } else {
                            onFireWidget.hideStreak();
                        }
                    }
                    break;
                case "sessionLossStreakUpdated":
                    if (SettingsService.getInstance().isLossStreakNudgeEnabled()
                            && evt.getNewValue() instanceof Integer count) {
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
                    boolean isConnected = (boolean) evt.getNewValue();
                    if (!isReplayMode) {
                        setOfflineMode(!isConnected);
                    }
                    break;
                case "liveDataSystemStateChanged":
                    if (evt.getNewValue() instanceof LiveDataManager.LiveDataSystemState state) {
                        updateConnectionWidget(state);
                    }
                    break;
            }
        });
    }

    private void updateConnectionWidget(LiveDataManager.LiveDataSystemState state) {
        if (isReplayMode) {
            // In replay mode, we might use the large overlay widget if we wanted to
            // simulate connection loss,
            // but typically replay is offline.
            return;
        }
        // In Live mode, the status bar handles this.
    }

    private void launchJournalDialogForTrade(Trade trade) {
        JournalEntryDialog dialog = new JournalEntryDialog(this.owner, trade, this.workspaceContext);
        dialog.setVisible(true);
    }

    public void applyLiveIndicator(CustomIndicator plugin) {
        if (workspaceManager.getActiveChartPanel() != null) {
            ChartDataModel model = workspaceManager.getActiveChartPanel().getDataModel();
            model.getIndicatorManager().addOrUpdateFromLiveCode(plugin, model);
        } else {
            JOptionPane.showMessageDialog(this, "No active chart to apply indicator to.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public ChartPanel getActiveChartPanel() {
        return workspaceManager.getActiveChartPanel();
    }

    public FloatingDrawingToolbar getDrawingToolbar() {
        return drawingToolbar;
    }

    public FloatingPropertiesToolbar getPropertiesToolbar() {
        return propertiesToolbar;
    }

    public ChartToolbarPanel getTopToolbarPanel() {
        return topToolbarPanel;
    }

    public Optional<TradingSidebarPanel> getTradingSidebar() {
        return Optional.ofNullable(tradingSidebar);
    }

    public Optional<ReplayController> getReplayController() {
        return Optional.ofNullable(replayController);
    }

    public WorkspaceManager getWorkspaceManager() {
        return workspaceManager;
    }

    public UIManager getUiManager() {
        return uiManager;
    }

    public SessionController getSessionController() {
        return sessionController;
    }

    public DatabaseManager getActiveDbManager() {
        return activeDbManager;
    }

    public boolean isReplayMode() {
        return this.isReplayMode;
    }

    public WorkspaceContext getWorkspaceContext() {
        return this.workspaceContext;
    }

    public TitleBarManager getTitleBarManager() {
        if (getFrameOwner() instanceof PrimaryFrame) {
            return ((PrimaryFrame) getFrameOwner()).getTitleBarManager();
        }
        return null;
    }

    public ChartDataSource getCurrentSource() {
        return topToolbarPanel.getSelectedDataSource();
    }

    public void openNewSyncedWindow() {
    }

    public void joinReplaySession() {
        ChartDataSource source = ReplaySessionManager.getInstance().getCurrentSource();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No active replay session to join.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        workspaceContext.getDrawingManager().setActiveSymbol(source.symbol());
        setDbManagerForSource(source);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
            workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        }
    }

    public void joinLiveSession() {
        ChartDataSource source = LiveWindowManager.getInstance().getActiveDataSource();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No active live session to join.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.loadChartForSource(source);
    }

    private void setupLiveMode() {
        this.tradingSidebar = new TradingSidebarPanel(this.workspaceContext);
        this.tradingSidebar.addPropertyChangeListener(this::handleSidebarEvents);
        this.tradingSidebar.setVisible(false);
        mainContainerPanel.add(this.tradingSidebar, BorderLayout.WEST);

        workspaceManager.initializeStandardMode();
        addTopToolbarListeners();
    }

    private void setupReplayMode() {
        this.replayController = new ReplayController(workspaceContext);
        ReplaySessionManager.getInstance().addListener(this.replayController);
        workspaceManager.initializeReplayMode();
        addTopToolbarListeners();
    }

    private void ensureReplayUIInitialized() {
        if (isReplayMode && tradingSidebar == null) {
            this.tradingSidebar = new TradingSidebarPanel(this.workspaceContext);
            this.tradingSidebar.addPropertyChangeListener(this::handleSidebarEvents);
            this.replayController.addPropertyChangeListener(this.tradingSidebar);
            mainContainerPanel.add(this.tradingSidebar, BorderLayout.WEST);
        } else if (isReplayMode && tradingSidebar != null) {
            tradingSidebar.setVisible(true);
        }

        if (isReplayMode && replayControlPanel == null) {
            this.replayControlPanel = new ReplayControlPanel(this.replayController);
            mainContainerPanel.add(this.replayControlPanel, BorderLayout.SOUTH);
        } else if (isReplayMode && replayControlPanel != null) {
            replayControlPanel.setVisible(true);
        }
        mainContainerPanel.revalidate();
        mainContainerPanel.repaint();
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
                    drawingToolbar.updatePosition(SettingsService.getInstance()
                            .getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
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
        if (tradingSidebar != null)
            tradingSidebar.setVisible(true);

        workspaceContext.getPaperTradingService().switchActiveSymbol(source.symbol());
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        loadChartForSource(source);
    }

    public void startReplaySession(DataSourceManager.ChartDataSource source, int startIndex) {
        ensureReplayUIInitialized();
        ReplaySessionManager.getInstance().startSession(source, startIndex);
        // [FIX] Set the active symbol for the paper trading service context.
        workspaceContext.getPaperTradingService().switchActiveSymbol(source.symbol());
        workspaceContext.getDrawingManager().setActiveSymbol(source.symbol());
        setDbManagerForSource(source);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
        workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
    }

    public void loadSessionState(ReplaySessionState state) {
        ensureReplayUIInitialized();
        workspaceContext.getDrawingManager().clearAllDrawingsForAllSymbols();
        if (isReplayMode) {
            ReplaySessionManager.getInstance().startSessionFromState(state);
        }
        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();
        if (sourceOpt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Data source for symbol '" + state.lastActiveSymbol() + "' not found.",
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setDbManagerForSource(sourceOpt.get());
        workspaceContext.getDrawingManager().setActiveSymbol(state.lastActiveSymbol());
        if (state.symbolStates() != null) {
            state.symbolStates().forEach((symbol, symbolState) -> workspaceContext.getDrawingManager()
                    .restoreDrawingsForSymbol(symbol, symbolState.drawings()));
        }
        workspaceContext.getPaperTradingService().restoreState(state);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            if (!isReplayMode) {
                if (tradingSidebar != null)
                    tradingSidebar.setVisible(true);
                loadChartForSource(sourceOpt.get());
            } else {
                workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
            }
            workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        }
    }

    private void handleTradeAction(String command) {
        if (workspaceManager.getActiveChartPanel() == null
                || workspaceManager.getActiveChartPanel().getDataModel().getCurrentSymbol() == null) {
            JOptionPane.showMessageDialog(this, "Please select an active chart.", "No Chart Active",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        TradeDirection direction = "placeLongOrder".equals(command) ? TradeDirection.LONG : TradeDirection.SHORT;
        new OrderDialog(this.owner, workspaceManager.getActiveChartPanel(), direction, this.workspaceContext)
                .setVisible(true);
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
            } else if (command.startsWith("timeframeChanged")) {
                Timeframe newTimeframe = null;
                if (e.getSource() instanceof Timeframe) {
                    newTimeframe = (Timeframe) e.getSource();
                } else {
                    String tfString = command.substring("timeframeChanged:".length());
                    newTimeframe = Timeframe.fromString(tfString);
                }

                if (newTimeframe != null) {
                    if (activePanel != null) {
                        activePanel.getDataModel().setDisplayTimeframe(newTimeframe);
                    } else if (!isReplayMode && !workspaceManager.getChartPanels().isEmpty()) {
                        workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(newTimeframe);
                    }
                }
            } else if (command.startsWith("layoutChanged:")) {
                try {
                    String layoutName = command.substring("layoutChanged:".length());
                    WorkspaceManager.LayoutType type = WorkspaceManager.LayoutType.valueOf(layoutName);
                    workspaceManager.applyLayout(type);
                } catch (IllegalArgumentException ex) {
                    System.err.println("Invalid layout type received: " + command);
                }
            } else if (command.startsWith("chartTypeChanged:")) {
                if (activePanel != null) {
                    try {
                        String typeName = command.substring("chartTypeChanged:".length());
                        ChartType type = ChartType.valueOf(typeName);
                        activePanel.setChartType(type);
                    } catch (IllegalArgumentException ex) {
                        System.err.println("Invalid chart type received: " + command);
                    }
                }
            } else if ("placeLongOrder".equals(command) || "placeShortOrder".equals(command)) {
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
        if (source == null)
            return;
        if (source.dbPath() != null) {
            setDbManagerForSource(source);
        } else {
            setDbManagerForSource(null);
        }
        workspaceContext.getPaperTradingService().switchActiveSymbol(source.symbol());
        workspaceContext.getDrawingManager().setActiveSymbol(source.symbol());
        topToolbarPanel.populateTimeframes(source.timeframes());

        if (!source.timeframes().isEmpty()) {
            String initialTimeframeStr = source.timeframes().get(0);
            Timeframe initialTimeframe = Timeframe.fromString(initialTimeframeStr);
            if (initialTimeframe == null)
                initialTimeframe = Timeframe.H1;
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
        if (newSource == null)
            return;
        topToolbarPanel.setCurrentSymbol(newSource);
        if (isReplayMode) {
            handleReplaySymbolChange();
        } else {
            loadChartForSource(newSource);
        }
    }

    public Frame getFrameOwner() {
        return this.owner;
    }
}