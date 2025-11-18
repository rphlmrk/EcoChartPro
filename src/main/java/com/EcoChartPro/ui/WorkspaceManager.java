package com.EcoChartPro.ui;

import com.EcoChartPro.core.controller.ChartController;
import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.SessionController;
import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig;
import com.EcoChartPro.core.tool.DrawingTool;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.action.TitleBarManager;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.chart.IndicatorPanel;
import com.EcoChartPro.ui.chart.PriceAxisPanel;
import com.EcoChartPro.ui.chart.TimeAxisPanel;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.SessionDialog;
import com.EcoChartPro.ui.toolbar.FloatingDrawingToolbar;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DatabaseManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    private final ChartWorkspacePanel owner;
    private final JPanel chartAreaPanel;
    private final java.util.List<ChartPanel> chartPanels = new ArrayList<>();
    private ChartPanel activeChartPanel;
    private final Map<UUID, Component> indicatorPaneMap = new HashMap<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final WorkspaceContext context;

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

    public WorkspaceManager(ChartWorkspacePanel owner, WorkspaceContext context) {
        this.owner = owner;
        this.context = context;
        this.chartAreaPanel = new JPanel();
        this.chartAreaPanel.setLayout(new BoxLayout(this.chartAreaPanel, BoxLayout.Y_AXIS));
    }

    public void initializeStandardMode() {
        // [MODIFIED] Show an interactive widget until a session is started.
        chartAreaPanel.removeAll();
        chartAreaPanel.setLayout(new BorderLayout()); // Use BorderLayout for proper centering

        NewSessionWidget widget = new NewSessionWidget(SessionDialog.SessionMode.LIVE_PAPER_TRADING);
        widget.addActionListener(e -> {
            SessionController sc = SessionController.getInstance();
            PrimaryFrame frame = (PrimaryFrame) owner.getFrameOwner();

            switch (e.getActionCommand()) {
                case "newLiveSession":
                    sc.showNewLiveSessionDialog(frame);
                    break;
                case "loadLiveSession":
                    sc.loadLiveSessionFromFile(frame);
                    break;
            }
        });

        chartAreaPanel.add(widget, BorderLayout.CENTER);
        chartAreaPanel.revalidate();
        chartAreaPanel.repaint();
    }

    public void initializeReplayMode() {
        // [MODIFIED] Show an interactive widget until a session is started.
        chartAreaPanel.removeAll();
        chartAreaPanel.setLayout(new BorderLayout()); // Use BorderLayout for proper centering

        NewSessionWidget widget = new NewSessionWidget(SessionDialog.SessionMode.REPLAY);
        widget.addActionListener(e -> {
            SessionController sc = SessionController.getInstance();
            PrimaryFrame frame = (PrimaryFrame) owner.getFrameOwner();

            switch (e.getActionCommand()) {
                case "newReplaySession":
                    sc.showNewReplaySessionDialog(frame);
                    break;
                case "loadReplaySession":
                    sc.loadReplaySessionFromFile(frame);
                    break;
            }
        });

        chartAreaPanel.add(widget, BorderLayout.CENTER);
        chartAreaPanel.revalidate();
        chartAreaPanel.repaint();
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
            
            SwingUtilities.invokeLater(() -> {
                owner.getDrawingToolbar().setVisible(true);
                owner.getDrawingToolbar().updatePosition(SettingsService.getInstance().getDrawingToolbarPosition() == DrawingConfig.ToolbarPosition.LEFT
                        ? FloatingDrawingToolbar.DockSide.LEFT
                        : FloatingDrawingToolbar.DockSide.RIGHT);
            });

        } else {
            owner.getDrawingToolbar().setVisible(false);
            owner.getPropertiesToolbar().setVisible(false);
        }
        
        for (ChartPanel p : chartPanels) p.setActive(p == activeChartPanel);

        pcs.firePropertyChange("activePanelChanged", oldPanel, activeChartPanel);
    }

    public Component createNewChartView(Timeframe tf, boolean activateOnClick) {
        ChartDataModel model = new ChartDataModel(context.getDrawingManager());
        ChartInteractionManager interactionManager = new ChartInteractionManager(model);
        model.setInteractionManager(interactionManager);

        ChartAxis chartAxis = new ChartAxis();
        PriceAxisPanel priceAxisPanel = new PriceAxisPanel(model, chartAxis, interactionManager, context);
        TimeAxisPanel timeAxisPanel = new TimeAxisPanel(model, chartAxis, interactionManager);

        Consumer<DrawingTool> onToolStateChange = tool -> {
            TitleBarManager tbm = owner.getTitleBarManager();
            if (tbm == null) return;

            if (tool == null) {
                tbm.restoreIdleTitle();
            } else {
                String toolName = tool.getClass().getSimpleName().replace("Tool", "");
                tbm.setToolActiveTitle(toolName);
            }
        };

        ChartPanel chartPanel = new ChartPanel(model, interactionManager, chartAxis, priceAxisPanel, timeAxisPanel, onToolStateChange, owner.getPropertiesToolbar(), context);
        
        model.setView(chartPanel);
        
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

        new ChartController(model, interactionManager, chartPanel, owner, context);
        
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
        // The original layout manager was BoxLayout. After removing everything,
        // we can add a new single component which can be a JSplitPane or a JPanel
        // with its own layout. The BoxLayout will respect this.
        chartAreaPanel.setLayout(new BoxLayout(chartAreaPanel, BoxLayout.Y_AXIS));
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
        PriceAxisPanel indicatorAxisPanel = new PriceAxisPanel(null, indicatorPanel.getLocalYAxis(), null, context);
        
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

    /**
     * A reusable widget for prompting the user to start a new session.
     */
    private static class NewSessionWidget extends JPanel {
        
        public NewSessionWidget(SessionDialog.SessionMode mode) {
            super(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            // Determine action commands based on mode
            String newSessionCommand, loadSessionCommand;
            if (mode == SessionDialog.SessionMode.REPLAY) {
                newSessionCommand = "newReplaySession";
                loadSessionCommand = "loadReplaySession";
            } else {
                newSessionCommand = "newLiveSession";
                loadSessionCommand = "loadLiveSession";
            }

            // Create cards
            ActionCard newCard = new ActionCard(UITheme.Icons.NEW_FILE, "New Session", "Configure and start a new session", newSessionCommand);
            ActionCard loadCard = new ActionCard(UITheme.Icons.FOLDER, "Load from File", "Load a previously saved session", loadSessionCommand);

            // Forward actions from cards to this widget's listeners
            ActionListener forwarder = this::fireActionPerformed;
            newCard.addActionListener(forwarder);
            loadCard.addActionListener(forwarder);

            // Layout
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.CENTER;

            JLabel titleLabel = new JLabel("Start a New " + (mode == SessionDialog.SessionMode.REPLAY ? "Replay" : "Live") + " Session");
            Font titleFont = javax.swing.UIManager.getFont("h2.font");
            if (titleFont == null) titleFont = new Font("SansSerif", Font.BOLD, 24);
            titleLabel.setFont(titleFont);

            JPanel cardPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
            cardPanel.setOpaque(false);
            cardPanel.add(newCard);
            cardPanel.add(loadCard);

            gbc.insets = new Insets(0, 0, 30, 0);
            add(titleLabel, gbc);
            
            gbc.insets = new Insets(10, 0, 10, 0);
            add(cardPanel, gbc);
        }

        public void addActionListener(ActionListener listener) {
            listenerList.add(ActionListener.class, listener);
        }

        protected void fireActionPerformed(ActionEvent event) {
            Object[] listeners = listenerList.getListenerList();
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == ActionListener.class) {
                    ((ActionListener) listeners[i + 1]).actionPerformed(event);
                }
            }
        }
        
        private static class ActionCard extends JPanel {
            private final Color defaultBg;
            private final Color hoverBg;
            
            public ActionCard(String iconPath, String title, String subtitle, String actionCommand) {
                this.defaultBg = javax.swing.UIManager.getColor("app.titlebar.tab.selected.background");
                this.hoverBg = defaultBg.brighter();
                
                setOpaque(false);
                setBackground(defaultBg);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setPreferredSize(new Dimension(220, 150));
                
                setLayout(new GridBagLayout());
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.insets = new Insets(4, 8, 4, 8);
                gbc.anchor = GridBagConstraints.CENTER;
                
                JLabel iconLabel = new JLabel(UITheme.getIcon(iconPath, 48, 48));
                gbc.insets = new Insets(10, 8, 10, 8);
                add(iconLabel, gbc);

                JLabel titleLabel = new JLabel(title);
                titleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_title"));
                titleLabel.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
                add(titleLabel, gbc);
                
                JLabel subtitleLabel = new JLabel("<html><div style='text-align: center;'>" + subtitle + "</div></html>");
                subtitleLabel.setFont(javax.swing.UIManager.getFont("app.font.widget_content").deriveFont(12f));
                subtitleLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
                add(subtitleLabel, gbc);
                
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { setBackground(hoverBg); }
                    @Override public void mouseExited(MouseEvent e) { setBackground(defaultBg); }
                    @Override public void mouseClicked(MouseEvent e) { fireActionPerformed(actionCommand); }
                });
            }
            
            public void addActionListener(ActionListener listener) {
                listenerList.add(ActionListener.class, listener);
            }
            
            protected void fireActionPerformed(String command) {
                Object[] listeners = listenerList.getListenerList();
                ActionEvent e = null;
                for (int i = listeners.length - 2; i >= 0; i -= 2) {
                    if (listeners[i] == ActionListener.class) {
                        if (e == null) {
                            e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
                        }
                        ((ActionListener) listeners[i + 1]).actionPerformed(e);
                    }
                }
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        }
    }
}