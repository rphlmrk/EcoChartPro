package com.EcoChartPro.ui;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.controller.ReplayController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.ui.action.KeyboardShortcutManager;
import com.EcoChartPro.ui.action.MenuBarManager;
import com.EcoChartPro.ui.action.TitleBarManager;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.OnFireStreakWidget;
import com.EcoChartPro.ui.components.StopTradingNudgeWidget;
import com.EcoChartPro.ui.dialogs.DrawingToolSettingsDialog;
import com.EcoChartPro.ui.dialogs.FibonacciSettingsDialog;
import com.EcoChartPro.ui.dialogs.TextSettingsDialog;
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
import java.util.Map;
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

    // --- Controllers & Managers ---
    private ReplayController replayController;
    private final WorkspaceManager workspaceManager;
    private final UIManager uiManager;
    private final SessionController sessionController;
    private final TitleBarManager titleBarManager;
    private DatabaseManager activeDbManager;

    // --- State Holders ---
    private JMenuItem undoMenuItem;
    private JMenuItem redoMenuItem;
    private final boolean isReplayMode;

    public MainWindow(boolean isReplayMode) {
        super("Eco Chart Pro");
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
        this.topToolbarPanel = new ChartToolbarPanel(isReplayMode);
        this.drawingToolbar = new FloatingDrawingToolbar(this);
        this.propertiesToolbar = new FloatingPropertiesToolbar(this);
        this.onFireWidget = new OnFireStreakWidget();
        this.stopTradingNudgeWidget = new StopTradingNudgeWidget();

        MenuBarManager menuBarManager = new MenuBarManager(this, isReplayMode);
        MenuBarManager.MenuBarResult menuResult = menuBarManager.createMenuBar();
        setJMenuBar((JMenuBar) menuResult.menu());
        this.undoMenuItem = menuResult.undoMenuItem();
        this.redoMenuItem = menuResult.redoMenuItem();

        mainContainerPanel.add(this.topToolbarPanel, BorderLayout.NORTH);

        if (isReplayMode) {
            setupReplayMode();
        } else {
            setupStandardMode();
        }
        
        mainContainerPanel.add(workspaceManager.getChartAreaPanel(), BorderLayout.CENTER);
        
        rootPanel.add(mainContainerPanel, JLayeredPane.DEFAULT_LAYER);
        rootPanel.add(onFireWidget, JLayeredPane.PALETTE_LAYER);
        rootPanel.add(stopTradingNudgeWidget, JLayeredPane.PALETTE_LAYER);
        setContentPane(rootPanel);
        
        addWindowListeners(isReplayMode);
        addPropertyChangeListeners();
        
        setupPropertiesToolbarActions();
        updateUndoRedoState();
        new KeyboardShortcutManager(rootPanel, this).setup();
        titleBarManager.start();
    }

    private void repositionOverlayWidgets() {
        Dimension fireSize = onFireWidget.getPreferredSize();
        onFireWidget.setBounds((rootPanel.getWidth() - fireSize.width) / 2, 20, fireSize.width, fireSize.height);

        Dimension nudgeSize = stopTradingNudgeWidget.getPreferredSize();
        stopTradingNudgeWidget.setBounds((rootPanel.getWidth() - nudgeSize.width) / 2, 20, nudgeSize.width, nudgeSize.height);
    }
    
    private void updateComponentLayouts() {
        mainContainerPanel.setBounds(0, 0, rootPanel.getWidth(), rootPanel.getHeight());

        if (drawingToolbar.isVisible()) {
            drawingToolbar.updatePosition(SettingsManager.getInstance().getDrawingToolbarPosition() == SettingsManager.ToolbarPosition.LEFT
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
        SettingsManager.getInstance().addPropertyChangeListener(this);
        DrawingManager.getInstance().addPropertyChangeListener("selectedDrawingChanged", this);
        UndoManager.getInstance().addPropertyChangeListener(this);
        if (isReplayMode) {
            LiveSessionTrackerService.getInstance().addPropertyChangeListener(this);
            PaperTradingService.getInstance().addPropertyChangeListener(this);
        }
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
        SettingsManager.getInstance().removePropertyChangeListener(this);
        DrawingManager.getInstance().removePropertyChangeListener("selectedDrawingChanged", this);
        UndoManager.getInstance().removePropertyChangeListener(this);
        if (isReplayMode) {
            LiveSessionTrackerService.getInstance().removePropertyChangeListener(this);
            PaperTradingService.getInstance().removePropertyChangeListener(this);
        }
        uiManager.disposeDialogs();
        titleBarManager.dispose();
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
                        (SettingsManager.ToolbarPosition) evt.getNewValue() == SettingsManager.ToolbarPosition.LEFT
                            ? FloatingDrawingToolbar.DockSide.LEFT
                            : FloatingDrawingToolbar.DockSide.RIGHT;
                    drawingToolbar.forceUpdatePosition(newSide);
                    break;
                case "selectedDrawingChanged":
                    handleDrawingSelectionChange((UUID) evt.getNewValue());
                    break;
                case "stateChanged":
                    if (evt.getSource() == UndoManager.getInstance()) {
                        updateUndoRedoState();
                    }
                    break;
                case "sessionStreakUpdated":
                    if (SettingsManager.getInstance().isWinStreakNudgeEnabled() && evt.getNewValue() instanceof Integer count) {
                        if (count >= 3) {
                            onFireWidget.showStreak(count);
                            repositionOverlayWidgets();
                        } else {
                            onFireWidget.hideStreak();
                        }
                    }
                    break;
                case "sessionLossStreakUpdated":
                     if (SettingsManager.getInstance().isLossStreakNudgeEnabled() && evt.getNewValue() instanceof Integer count) {
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
            }
        });
    }

    private void launchJournalDialogForTrade(Trade trade) {
        JournalEntryDialog dialog = new JournalEntryDialog(this, trade);
        dialog.setVisible(true);
    }

    private void updateUndoRedoState() {
        boolean canUndo = UndoManager.getInstance().canUndo();
        boolean canRedo = UndoManager.getInstance().canRedo();

        if (undoMenuItem != null && redoMenuItem != null) {
            undoMenuItem.setEnabled(canUndo);
            redoMenuItem.setEnabled(canRedo);
        }
        if (topToolbarPanel != null) {
            topToolbarPanel.setUndoEnabled(canUndo);
            topToolbarPanel.setRedoEnabled(canRedo);
        }
    }
    
    private void setupPropertiesToolbarActions() {
        DrawingManager drawingManager = DrawingManager.getInstance();

        propertiesToolbar.getDeleteButton().addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId != null) {
                drawingManager.removeDrawing(selectedId);
                drawingManager.setSelectedDrawingId(null);
            }
        });

        propertiesToolbar.getThicknessSpinner().addChangeListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing instanceof TextObject || drawing.isLocked()) return;

            int newThickness = (int) propertiesToolbar.getThicknessSpinner().getValue();
            if ((int) drawing.stroke().getLineWidth() == newThickness) return;

            BasicStroke oldStroke = drawing.stroke();
            BasicStroke newStroke = new BasicStroke(newThickness, oldStroke.getEndCap(), oldStroke.getLineJoin(), oldStroke.getMiterLimit(), oldStroke.getDashArray(), oldStroke.getDashPhase());
            drawingManager.updateDrawing(drawing.withStroke(newStroke));
        });

        propertiesToolbar.getColorButton().addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing.isLocked()) return;

            Consumer<Color> onColorUpdate = newColor -> {
                propertiesToolbar.setCurrentColor(newColor);
                drawingManager.updateDrawing(drawing.withColor(newColor));
            };

            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(drawing.color(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            popupMenu.show(propertiesToolbar.getColorButton(), 0, propertiesToolbar.getColorButton().getHeight());
        });
        
        propertiesToolbar.getLockButton().addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null) return;

            boolean newLockedState = propertiesToolbar.getLockButton().isSelected();
            drawingManager.updateDrawing(drawing.withLocked(newLockedState));
        });

        propertiesToolbar.getMoreOptionsButton().addActionListener(e -> {
            UUID selectedId = drawingManager.getSelectedDrawingId();
            if (selectedId == null) return;
            DrawingObject drawing = drawingManager.getDrawingById(selectedId);
            if (drawing == null || drawing.isLocked()) return;

            drawing.showSettingsDialog(this, drawingManager);
        });
    }

    private void handleDrawingSelectionChange(UUID selectedId) {
        if (selectedId == null) {
            propertiesToolbar.setVisible(false);
            
            ChartPanel activePanel = workspaceManager.getActiveChartPanel();
            if (activePanel == null || activePanel.getDrawingController().getActiveTool() == null) {
                titleBarManager.restoreIdleTitle();
            }
            return;
        }

        DrawingObject drawing = DrawingManager.getInstance().getDrawingById(selectedId);
        if (drawing == null) {
            propertiesToolbar.setVisible(false);
            titleBarManager.restoreIdleTitle();
            return;
        }
        
        String lockedStatus = drawing.isLocked() ? " (Locked)" : "";
        titleBarManager.setStaticTitle("Object Selected" + lockedStatus + " | Press Delete to remove");
        
        propertiesToolbar.setLockedState(drawing.isLocked());
        propertiesToolbar.getThicknessSpinner().setEnabled(!drawing.isLocked() && !(drawing instanceof TextObject));

        propertiesToolbar.setCurrentColor(drawing.color());
        if (!(drawing instanceof TextObject)) {
            propertiesToolbar.getThicknessSpinner().setValue((int) drawing.stroke().getLineWidth());
        }

        ChartPanel activeChartPanel = workspaceManager.getActiveChartPanel();
        if (activeChartPanel != null && activeChartPanel.isShowing()) {
            Point chartLocation = activeChartPanel.getLocationOnScreen();
            int x = chartLocation.x + (activeChartPanel.getWidth() / 2) - (propertiesToolbar.getWidth() / 2);
            int y = chartLocation.y + 20;
            propertiesToolbar.setLocation(x, y);
            propertiesToolbar.setVisible(true);
        }
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
    
    public void openNewSyncedWindow() {
        if (ReplaySessionManager.getInstance().getCurrentSource() != null) {
            SwingUtilities.invokeLater(() -> {
                MainWindow newSyncedWindow = new MainWindow(true);
                newSyncedWindow.joinReplaySession();
            });
        } else {
            JOptionPane.showMessageDialog(this, "A replay session must be active to open a new synced window.", "No Active Session", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void joinReplaySession() {
        ChartDataSource source = ReplaySessionManager.getInstance().getCurrentSource();
        if (source == null) {
            JOptionPane.showMessageDialog(this, "No active replay session to join.", "Error", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }
        titleBarManager.setStaticTitle(getTitle() + " (Synced)");
        setDbManagerForSource(source);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
            workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        }
        setVisible(true);
    }

    private void setupStandardMode() {
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
                    drawingToolbar.updatePosition(SettingsManager.getInstance().getDrawingToolbarPosition() == SettingsManager.ToolbarPosition.LEFT
                            ? FloatingDrawingToolbar.DockSide.LEFT
                            : FloatingDrawingToolbar.DockSide.RIGHT);
                }
            });
        }
    }

    public void setDbManagerForSource(ChartDataSource source) {
        if (source == null || source.dbPath() == null) return;
        if (activeDbManager != null) activeDbManager.close();
        
        String jdbcUrl = "jdbc:sqlite:" + source.dbPath().toAbsolutePath();
        this.activeDbManager = new DatabaseManager(jdbcUrl);
        
        // When the source changes, update the DB manager for ALL existing chart panels.
        for (ChartPanel panel : workspaceManager.getChartPanels()) {
            panel.getDataModel().setDatabaseManager(this.activeDbManager, source);
        }
        
        topToolbarPanel.setCurrentSymbol(source);
    }

    public void loadInitialChart(DataSourceManager.ChartDataSource source, String timeframe) {
        setDbManagerForSource(source);
        loadChartForSource(source);
        topToolbarPanel.selectTimeframe(timeframe);
    }

    public void startReplaySession(DataSourceManager.ChartDataSource source, int startIndex) {
        setDbManagerForSource(source);
        ReplaySessionManager.getInstance().startSession(source, startIndex);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
        workspaceManager.setActiveChartPanel(workspaceManager.getChartPanels().get(0));
        setVisible(true);
    }

    public void loadReplaySession(ReplaySessionState state) {
        DrawingManager.getInstance().clearAllDrawings();

        Optional<ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.dataSourceSymbol())).findFirst();
        if (sourceOpt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Data source for symbol '" + state.dataSourceSymbol() + "' not found.", "Load Error", JOptionPane.ERROR_MESSAGE);
            dispose(); return;
        }
        setDbManagerForSource(sourceOpt.get());
        DrawingManager.getInstance().restoreDrawings(state.drawings());
        PaperTradingService.getInstance().restoreState(state);
        ReplaySessionManager.getInstance().startSessionFromState(state);
        workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE);
        if (!workspaceManager.getChartPanels().isEmpty()) {
            workspaceManager.getChartPanels().get(0).getDataModel().setDisplayTimeframe(Timeframe.M5);
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
                loadChartForSource(topToolbarPanel.getSelectedDataSource());
            } else if (command.startsWith("timeframeChanged:")) {
                String tfString = command.substring(17);
                if (activePanel != null) {
                    final Timeframe newTimeframe = Timeframe.fromString(tfString);
                    if (newTimeframe != null) {
                        activePanel.getDataModel().setDisplayTimeframe(newTimeframe);
                    }
                } else {
                    loadData(topToolbarPanel.getSelectedDataSource(), tfString);
                }
            } else if (command.startsWith("layoutChanged:")) {
                String layoutName = command.substring("layoutChanged:".length());
                switch (layoutName) {
                    case "1 View": workspaceManager.applyLayout(WorkspaceManager.LayoutType.ONE); break;
                    case "2 Views": workspaceManager.applyLayout(WorkspaceManager.LayoutType.TWO); break;
                    case "2 Views (Vertical)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.TWO_VERTICAL); break;
                    case "3 Views (Horizontal)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.THREE_HORIZONTAL); break;
                    case "3 Views (Right Stack)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.THREE_RIGHT); break;
                    case "3 Views (Left Stack)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.THREE_LEFT); break;
                    case "4 Views": workspaceManager.applyLayout(WorkspaceManager.LayoutType.FOUR); break;
                    case "3 Views (Vertical)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.THREE_VERTICAL); break;
                    case "4 Views (Vertical)": workspaceManager.applyLayout(WorkspaceManager.LayoutType.FOUR_VERTICAL); break;
                }
            } else if ("placeLongOrder".equals(command) || "placeShortOrder".equals(command)) {
                handleTradeAction(command);
            }
        });
    }

    private void loadChartForSource(DataSourceManager.ChartDataSource source) {
        if (source == null) return;
        setDbManagerForSource(source);
        topToolbarPanel.populateTimeframes(source.timeframes());
        if (!source.timeframes().isEmpty()) {
            topToolbarPanel.selectTimeframe(source.timeframes().get(0));
        } else {
             if (!workspaceManager.getChartPanels().isEmpty()) workspaceManager.getChartPanels().get(0).getDataModel().clearData();
             titleBarManager.setStaticTitle(source.displayName() + " (No data available)");
        }
    }

    private void loadData(DataSourceManager.ChartDataSource source, String timeframe) {
        if (source == null || timeframe == null || activeDbManager == null || workspaceManager.getChartPanels().isEmpty()) return;
        workspaceManager.getChartPanels().get(0).getDataModel().loadDataset(source, timeframe);
        titleBarManager.setStaticTitle(source.displayName() + " (" + timeframe + ")");
    }
}