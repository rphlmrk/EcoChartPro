package com.EcoChartPro.ui;

import com.EcoChartPro.core.controller.ChartController;
import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig;
import com.EcoChartPro.core.tool.DrawingTool;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.chart.IndicatorPanel;
import com.EcoChartPro.ui.chart.PriceAxisPanel;
import com.EcoChartPro.ui.chart.TimeAxisPanel;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import com.EcoChartPro.ui.toolbar.FloatingDrawingToolbar;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DatabaseManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WorkspaceManager {

    public enum LayoutType { ONE, TWO, THREE_RIGHT, THREE_LEFT, FOUR, THREE_VERTICAL, FOUR_VERTICAL, TWO_VERTICAL, THREE_HORIZONTAL }

    private final MainWindow owner;
    private final JPanel chartAreaPanel;
    private final java.util.List<ChartPanel> chartPanels = new ArrayList<>();
    private ChartPanel activeChartPanel;
    private final Map<UUID, Component> indicatorPaneMap = new HashMap<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private final ComponentListener activeChartListener = new ComponentAdapter() {
        private void updateToolbarPosition() {
            SwingUtilities.invokeLater(() -> {
                if (owner.getDrawingToolbar().isVisible() && activeChartPanel != null && activeChartPanel.isShowing()) {
                    owner.getDrawingToolbar().updatePosition(
                        SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                            ? FloatingDrawingToolbar.DockSide.LEFT
                            : FloatingDrawingToolbar.DockSide.RIGHT
                    );
                }
            });
        }

        @Override
        public void componentResized(ComponentEvent e) {
            updateToolbarPosition();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            updateToolbarPosition();
        }
    };

    public WorkspaceManager(MainWindow owner) {
        this.owner = owner;
        this.chartAreaPanel = new JPanel();
        this.chartAreaPanel.setLayout(new BoxLayout(this.chartAreaPanel, BoxLayout.Y_AXIS));
    }

    public void initializeStandardMode() {
        // Create the first chart view, but don't add it directly. applyLayout will handle it.
        createNewChartView(null, true);
        applyLayout(LayoutType.ONE); // Apply default layout
        if (!chartPanels.isEmpty()) {
            setActiveChartPanel(chartPanels.get(0));
        }
    }

    public void initializeReplayMode() {
        // In replay mode, the initial chart is added when the session starts.
    }

    public JPanel getChartAreaPanel() {
        return chartAreaPanel;
    }

    public ChartPanel getActiveChartPanel() {
        return activeChartPanel;
    }

    public java.util.List<ChartPanel> getChartPanels() {
        return chartPanels;
    }

    public void setActiveChartPanel(ChartPanel panel) {
        if (activeChartPanel == panel) return;

        ChartPanel oldPanel = this.activeChartPanel;

        if (activeChartPanel != null) {
            activeChartPanel.removeComponentListener(activeChartListener);
            activeChartPanel.getDataModel().getIndicatorManager().removePropertyChangeListener(owner);
        }

        activeChartPanel = panel;

        if (activeChartPanel != null) {
            activeChartPanel.addComponentListener(activeChartListener);
            owner.getTradingSidebar().ifPresent(sidebar -> sidebar.setActiveChartPanel(activeChartPanel));
            activeChartPanel.getDataModel().getIndicatorManager().addPropertyChangeListener(owner);
            owner.getReplayController().ifPresent(controller -> controller.setActiveChartPanel(activeChartPanel));
            
            activeChartPanel.requestFocusInWindow();
            
            owner.getDrawingToolbar().setVisible(true);
            SwingUtilities.invokeLater(() -> {
                if (activeChartPanel.getDrawingController().getActiveTool() == null) {
                    owner.getTitleBarManager().restoreIdleTitle();
                }
                owner.getDrawingToolbar().updatePosition(SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                        ? FloatingDrawingToolbar.DockSide.LEFT
                        : FloatingDrawingToolbar.DockSide.RIGHT);
            });

        } else {
            owner.getDrawingToolbar().setVisible(false);
            owner.getPropertiesToolbar().setVisible(false);
            owner.getTitleBarManager().setStaticTitle("No Active Chart");
        }
        
        for (ChartPanel p : chartPanels) p.setActive(p == activeChartPanel);

        pcs.firePropertyChange("activePanelChanged", oldPanel, activeChartPanel);
    }

    public Component createNewChartView(Timeframe tf, boolean activateOnClick) {
        ChartDataModel model = new ChartDataModel();
        ChartInteractionManager interactionManager = new ChartInteractionManager(model);
        model.setInteractionManager(interactionManager);

        // --- Create UI components first ---
        ChartAxis chartAxis = new ChartAxis();
        PriceAxisPanel priceAxisPanel = new PriceAxisPanel(model, chartAxis, interactionManager);
        TimeAxisPanel timeAxisPanel = new TimeAxisPanel(model, chartAxis, interactionManager);

        Consumer<DrawingTool> onToolStateChange = tool -> {
            if (tool != null) {
                String toolName = tool.getClass().getSimpleName().replace("Tool", "");
                owner.getTitleBarManager().setToolActiveTitle(toolName);
            } else {
                owner.getTitleBarManager().restoreIdleTitle();
            }
        };

        ChartPanel chartPanel = new ChartPanel(model, interactionManager, chartAxis, priceAxisPanel, timeAxisPanel, onToolStateChange, owner.getPropertiesToolbar());
        
        // --- Set the view on the model *before* configuring it ---
        model.setView(chartPanel);
        
        // --- Now configure the model, which creates the data provider ---
        DataSourceManager.ChartDataSource sourceToLoad = owner.getCurrentSource();
        if (sourceToLoad == null && !chartPanels.isEmpty() && chartPanels.get(0).getDataModel().getCurrentSymbol() != null) {
            sourceToLoad = chartPanels.get(0).getDataModel().getCurrentSymbol();
        }

        boolean isReplay = ReplaySessionManager.getInstance().getCurrentSource() != null;

        if (isReplay) {
            DataSourceManager.ChartDataSource replaySource = ReplaySessionManager.getInstance().getCurrentSource();
            model.configureForReplay(tf, replaySource);
            model.setDatabaseManager(owner.getActiveDbManager(), replaySource);
            ReplaySessionManager.getInstance().addListener(interactionManager);
        } else {
            model.setDatabaseManager(owner.getActiveDbManager(), sourceToLoad);
        }

        new ChartController(model, interactionManager, chartPanel, owner);
        
        String selectedTfString = owner.getTopToolbarPanel().getTimeframeButton().getText();
        Timeframe targetTimeframe = (tf != null) ? tf : Timeframe.fromString(selectedTfString);
        if (targetTimeframe == null) targetTimeframe = Timeframe.H1;

        if (isReplay) {
            model.setDisplayTimeframe(targetTimeframe, true);
        } else if (sourceToLoad != null) {
            model.loadDataset(sourceToLoad, targetTimeframe);
        }

        JPanel container = new JPanel(new BorderLayout());
        container.add(chartPanel, BorderLayout.CENTER);
        container.add(priceAxisPanel, BorderLayout.EAST);
        container.add(timeAxisPanel, BorderLayout.SOUTH);
        chartPanels.add(chartPanel);

        if (activateOnClick) {
            MouseAdapter activator = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { setActiveChartPanel(chartPanel); }
            };
            container.addMouseListener(activator);
            chartPanel.addMouseListener(activator);
        }
        return container;
    }
    
    private Timeframe getNextDefaultTimeframe(List<Timeframe> existingTimeframes) {
        List<Timeframe> sequence = List.of(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.D1, Timeframe.M1, Timeframe.H4);
        
        for (Timeframe tf : sequence) {
            if (!existingTimeframes.contains(tf)) {
                return tf;
            }
        }
        
        return (activeChartPanel != null && activeChartPanel.getDataModel().getCurrentDisplayTimeframe() != null)
               ? activeChartPanel.getDataModel().getCurrentDisplayTimeframe() : Timeframe.H1;
    }

    public void applyLayout(LayoutType layoutType) {
        chartAreaPanel.removeAll();
        indicatorPaneMap.clear();

        int requiredPanels = 1;
        switch (layoutType) {
            case TWO: case TWO_VERTICAL: requiredPanels = 2; break;
            case THREE_LEFT: case THREE_RIGHT: case THREE_VERTICAL: case THREE_HORIZONTAL: requiredPanels = 3; break;
            case FOUR: case FOUR_VERTICAL: requiredPanels = 4; break;
        }
        
        while (chartPanels.size() < requiredPanels) {
             List<Timeframe> existingTimeframes = chartPanels.stream()
                .map(p -> p.getDataModel().getCurrentDisplayTimeframe())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
             Timeframe nextTf = getNextDefaultTimeframe(existingTimeframes);
             createNewChartView(nextTf, true);
        }
        
        if (activeChartPanel == null && !chartPanels.isEmpty()) {
            setActiveChartPanel(chartPanels.get(0));
        }

        JComponent newLayout;
        switch (layoutType) {
            case TWO:
                newLayout = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanels.get(0).getParent(), chartPanels.get(1).getParent());
                ((JSplitPane) newLayout).setResizeWeight(0.5);
                break;
            case TWO_VERTICAL:
                newLayout = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanels.get(0).getParent(), chartPanels.get(1).getParent());
                ((JSplitPane) newLayout).setResizeWeight(0.5);
                break;
            case THREE_RIGHT:
                JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanels.get(1).getParent(), chartPanels.get(2).getParent());
                rightSplit.setResizeWeight(0.5);
                newLayout = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanels.get(0).getParent(), rightSplit);
                ((JSplitPane) newLayout).setResizeWeight(0.5);
                break;
            case THREE_LEFT:
                JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanels.get(0).getParent(), chartPanels.get(1).getParent());
                leftSplit.setResizeWeight(0.5);
                newLayout = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, chartPanels.get(2).getParent());
                ((JSplitPane) newLayout).setResizeWeight(0.5);
                break;
            case THREE_HORIZONTAL:
                JSplitPane leftSplitH = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanels.get(0).getParent(), chartPanels.get(1).getParent());
                leftSplitH.setResizeWeight(0.33);
                newLayout = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitH, chartPanels.get(2).getParent());
                ((JSplitPane) newLayout).setResizeWeight(0.66);
                break;
            case FOUR:
                JPanel fourPanel = new JPanel(new GridLayout(2, 2));
                for(int i = 0; i < 4; i++) fourPanel.add(chartPanels.get(i).getParent());
                newLayout = fourPanel;
                break;
            case THREE_VERTICAL:
                JSplitPane topSplitV = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartPanels.get(0).getParent(), chartPanels.get(1).getParent());
                topSplitV.setResizeWeight(0.33);
                newLayout = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplitV, chartPanels.get(2).getParent());
                ((JSplitPane) newLayout).setResizeWeight(0.66);
                break;
            case FOUR_VERTICAL:
                JPanel fourVerticalPanel = new JPanel(new GridLayout(4, 1));
                for(int i = 0; i < 4; i++) fourVerticalPanel.add(chartPanels.get(i).getParent());
                newLayout = fourVerticalPanel;
                break;
            default: newLayout = (JComponent) chartPanels.get(0).getParent(); break;
        }

        chartAreaPanel.add(newLayout);
        chartAreaPanel.revalidate();
        chartAreaPanel.repaint();
    }
    
    public void addIndicatorPane(Indicator indicator) {
        if (activeChartPanel == null) return;

        IndicatorPanel indicatorPanel = new IndicatorPanel(activeChartPanel.getDataModel(), activeChartPanel.getChartAxis(), indicator);
        PriceAxisPanel indicatorAxisPanel = new PriceAxisPanel(null, indicatorPanel.getLocalYAxis(), null);

        JPanel container = new JPanel(new BorderLayout());
        container.add(indicatorPanel, BorderLayout.CENTER);
        container.add(indicatorAxisPanel, BorderLayout.EAST);

        container.setPreferredSize(new Dimension(100, 150));
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        
        Component mainLayout = chartAreaPanel.getComponent(0);
        chartAreaPanel.removeAll();
        chartAreaPanel.setLayout(new BorderLayout());
        
        JPanel indicatorContainer = new JPanel();
        indicatorContainer.setLayout(new BoxLayout(indicatorContainer, BoxLayout.Y_AXIS));
        indicatorContainer.add(container);
        
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainLayout, indicatorContainer);
        mainSplit.setResizeWeight(0.8);
        
        chartAreaPanel.add(mainSplit);
        
        indicatorPaneMap.put(indicator.getId(), container);
        chartAreaPanel.revalidate();
    }
    
    public void removeIndicatorPane(UUID indicatorId) {
        Component pane = indicatorPaneMap.remove(indicatorId);
        if (pane != null && pane.getParent() != null) {
            Container parent = pane.getParent();
            parent.remove(pane);
            if (parent.getComponentCount() == 0 && parent.getParent() instanceof JSplitPane) {
                JSplitPane splitPane = (JSplitPane) parent.getParent();
                Component otherComponent = (splitPane.getTopComponent() == parent) ? splitPane.getBottomComponent() : splitPane.getTopComponent();
                
                Container grandParent = splitPane.getParent();
                grandParent.remove(splitPane);
                grandParent.add(otherComponent, BorderLayout.CENTER);
                grandParent.revalidate();
                grandParent.repaint();
            } else {
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }
}