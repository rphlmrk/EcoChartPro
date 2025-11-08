package com.EcoChartPro.ui.toolbar;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig;
import com.EcoChartPro.core.tool.*;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.drawing.MeasureToolObject;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer; // Explicitly import the Swing Timer
import java.util.function.Supplier;

public class FloatingDrawingToolbar extends JDialog implements PropertyChangeListener {

    private final MainWindow mainWindow;
    private final JPanel contentPanel;
    private final JPanel handlePanel;
    private final JPanel buttonsPanel;
    private final ButtonGroup toolButtonGroup = new ButtonGroup();
    private final List<Component> allToolbarComponents = new ArrayList<>();
    private final JScrollPane scrollPane;
    private final Map<String, JToggleButton> toolButtonMap = new HashMap<>();


    private Point initialClick;
    private Orientation currentOrientation = Orientation.VERTICAL;
    private DockSide currentDockSide = DockSide.LEFT;
    private static final int SNAP_DISTANCE = 30;
    private static final int CORNER_RADIUS = 20;

    public enum Orientation { VERTICAL, HORIZONTAL }
    public enum DockSide { LEFT, RIGHT, NONE }

    public FloatingDrawingToolbar(MainWindow owner) {
        super(owner, false);
        this.mainWindow = owner;

        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setFocusableWindowState(false);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
            }
        });

        contentPanel = new JPanel(new BorderLayout(0, 0));
        contentPanel.setBackground(UIManager.getColor("Panel.background"));
        contentPanel.setOpaque(true);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));
        setContentPane(contentPanel);

        handlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        handlePanel.setOpaque(false);
        handlePanel.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        handlePanel.add(new JLabel(UITheme.getThemedIcon(UITheme.Icons.DRAG_HANDLE, 22, 22)));
        addDragListeners(handlePanel);

        buttonsPanel = new JPanel(new GridBagLayout());
        buttonsPanel.setOpaque(false);

        scrollPane = new JScrollPane(buttonsPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        populateComponentList();
        
        setOrientation(Orientation.VERTICAL, true);
        
        SettingsService.getInstance().addPropertyChangeListener(this);
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("drawingToolbarSizeChanged".equals(evt.getPropertyName())) {
            updateSizeAndRepaint();
        }
    }

    private static class FlyoutAction {
        final String name;
        final String iconPath;
        final Supplier<DrawingTool> toolSupplier;

        FlyoutAction(String name, String iconPath, Supplier<DrawingTool> toolSupplier) {
            this.name = name;
            this.iconPath = iconPath;
            this.toolSupplier = toolSupplier;
        }
    }

    private void populateComponentList() {
        allToolbarComponents.clear();

        addToolButton("Info Cursor (Alt+I)", UITheme.Icons.INFO_CURSOR, new InfoTool());

        addToolGroup(List.of(
            new FlyoutAction("Trendline (Alt+T)", UITheme.Icons.TRENDLINE, TrendlineTool::new),
            new FlyoutAction("Ray", UITheme.Icons.RAY, RayTool::new),
            new FlyoutAction("Horizontal Line", UITheme.Icons.HORIZONTAL_LINE, HorizontalLineTool::new),
            new FlyoutAction("Horizontal Ray", UITheme.Icons.HORIZONTAL_RAY, HorizontalRayTool::new),
            new FlyoutAction("Vertical Line", UITheme.Icons.VERTICAL_LINE, VerticalLineTool::new)
        ));

        addToolGroup(List.of(
            new FlyoutAction("Fib Retracement", UITheme.Icons.FIB_RETRACEMENT, FibonacciRetracementTool::new),
            new FlyoutAction("Fib Extension", UITheme.Icons.FIB_EXTENSION, FibonacciExtensionTool::new)
        ));

        addToolGroup(List.of(
            new FlyoutAction("Rectangle (Alt+R)", UITheme.Icons.RECTANGLE, RectangleTool::new),
            new FlyoutAction("Price Range", UITheme.Icons.PRICE_RANGE, () -> new MeasureTool(MeasureToolObject.ToolType.PRICE_RANGE)),
            new FlyoutAction("Date Range", UITheme.Icons.DATE_RANGE, () -> new MeasureTool(MeasureToolObject.ToolType.DATE_RANGE)),
            new FlyoutAction("Measure", UITheme.Icons.MEASURE, () -> new MeasureTool(MeasureToolObject.ToolType.MEASURE))
        ));

        addToolButton("Protected Level Pattern", UITheme.Icons.PROTECTED_LEVEL, new ProtectedLevelPatternTool());

        addToolGroup(List.of(
            new FlyoutAction("Text", UITheme.Icons.TEXT, TextTool::new),
            new FlyoutAction("Anchored Text", UITheme.Icons.ANCHORED_TEXT, AnchoredTextTool::new)
        ));

        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(UIManager.getColor("Separator.foreground"));
        allToolbarComponents.add(separator);

        JButton visibilityButton = addActionButton("Visibility", UITheme.Icons.VISIBILITY_ON);
        visibilityButton.addActionListener(this::showVisibilityMenu);

        JButton removalButton = addActionButton("Bulk Remove", UITheme.Icons.DELETE);
        removalButton.addActionListener(this::showRemovalMenu);
    }

    private void addToolGroup(List<FlyoutAction> actions) {
        if (actions == null || actions.isEmpty()) return;

        FlyoutAction defaultAction = actions.get(0);

        JToggleButton mainButton = new JToggleButton(UITheme.getThemedIcon(defaultAction.iconPath, 22, 22));
        styleButton(mainButton, defaultAction.name);
        mainButton.putClientProperty("flyout.action", defaultAction);
        toolButtonGroup.add(mainButton);
        String baseName = defaultAction.name.split(" \\(")[0];
        toolButtonMap.put(baseName, mainButton);


        mainButton.addActionListener(e -> {
            FlyoutAction currentAction = (FlyoutAction) mainButton.getClientProperty("flyout.action");
            if (currentAction != null && mainWindow.getActiveChartPanel() != null) {
                if (mainButton.isSelected()) {
                    mainWindow.getActiveChartPanel().getDrawingController().setActiveTool(currentAction.toolSupplier.get());
                } else {
                    mainWindow.getActiveChartPanel().getDrawingController().setActiveTool(null);
                }
            }
        });

        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setFocusable(false);

        Timer hideTimer = new Timer(200, e -> popupMenu.setVisible(false));
        hideTimer.setRepeats(false);

        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hideTimer.stop();
                if (!popupMenu.isVisible()) {
                    if (currentOrientation == Orientation.VERTICAL) {
                        popupMenu.show(mainButton, mainButton.getWidth(), 0);
                    } else {
                        popupMenu.show(mainButton, 0, mainButton.getHeight());
                    }
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hideTimer.restart();
            }
        };

        for (FlyoutAction action : actions) {
            JMenuItem menuItem = new JMenuItem(action.name, UITheme.getThemedIcon(action.iconPath, 16, 16));
            menuItem.addActionListener(e -> {
                mainButton.setIcon(UITheme.getThemedIcon(action.iconPath, 22, 22));
                mainButton.setToolTipText(action.name);
                mainButton.putClientProperty("flyout.action", action);
                popupMenu.setVisible(false);

                if (mainButton.isSelected()) {
                    if (mainWindow.getActiveChartPanel() != null) {
                        mainWindow.getActiveChartPanel().getDrawingController().setActiveTool(action.toolSupplier.get());
                    }
                } else {
                    mainButton.setSelected(true);
                }
            });
            menuItem.addMouseListener(hoverListener);
            popupMenu.add(menuItem);
        }

        mainButton.addMouseListener(hoverListener);
        popupMenu.addMouseListener(hoverListener);
        
        allToolbarComponents.add(mainButton);
    }

    private void addToolButton(String toolName, String iconPath, com.EcoChartPro.core.tool.DrawingTool tool) {
        JToggleButton button = new JToggleButton(UITheme.getThemedIcon(iconPath, 22, 22));
        styleButton(button, toolName);
        button.addActionListener(e -> handleToolSelection(button.isSelected(), tool));
        toolButtonGroup.add(button);
        allToolbarComponents.add(button);
        toolButtonMap.put(toolName, button);
    }

    private JButton addActionButton(String toolTip, String iconPath) {
        JButton button = new JButton(UITheme.getThemedIcon(iconPath, 22, 22));
        styleButton(button, toolTip);
        allToolbarComponents.add(button);
        return button;
    }

    private void handleToolSelection(boolean isSelected, DrawingTool tool) {
        if (mainWindow.getActiveChartPanel() != null) {
            if (isSelected) {
                tool.reset();
                mainWindow.getActiveChartPanel().getDrawingController().setActiveTool(tool);
            } else {
                mainWindow.getActiveChartPanel().getDrawingController().setActiveTool(null);
            }
        }
    }

    private void styleButton(AbstractButton button, String toolTip) {
        button.setToolTipText(toolTip);
        button.setFocusPainted(false);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public void setOrientation(Orientation newOrientation, boolean force) {
        if (this.currentOrientation == newOrientation && !force) return;
        
        this.currentOrientation = newOrientation;
        
        buttonsPanel.removeAll();
        contentPanel.removeAll();

        if (newOrientation == Orientation.VERTICAL) {
            buttonsPanel.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.anchor = GridBagConstraints.WEST;
            gbc.insets = new Insets(2, 4, 2, 4); 

            for (Component comp : allToolbarComponents) {
                if (comp instanceof JPanel || comp instanceof JSeparator) {
                    gbc.fill = GridBagConstraints.HORIZONTAL;
                } else {
                    gbc.fill = GridBagConstraints.NONE;
                }
                buttonsPanel.add(comp, gbc);
                gbc.gridy++;
            }
            
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            contentPanel.add(handlePanel, BorderLayout.NORTH);
        } else {
            buttonsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
            allToolbarComponents.forEach(buttonsPanel::add);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            contentPanel.add(handlePanel, BorderLayout.WEST);
        }
        
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        updateSizeAndRepaint();
        
        buttonsPanel.revalidate();
        buttonsPanel.repaint();
    }
    
    private void updateSizeAndRepaint() {
        if (currentOrientation == Orientation.VERTICAL) {
            // Note: In a real app, you might want to get these from settings
            Dimension newSize = new Dimension(45, 300); // Using fixed size for now
            setPreferredSize(newSize);
            setSize(newSize);
        } else {
            setPreferredSize(null); 
            pack();
        }
        revalidate();
        repaint();
    }
    
    private void showVisibilityMenu(ActionEvent e) {
        JButton visibilityButton = (JButton) e.getSource();
        ChartPanel chartPanel = mainWindow.getActiveChartPanel();
        if (chartPanel == null) return;
        JPopupMenu menu = new JPopupMenu();
        JCheckBoxMenuItem hideDrawingsItem = new JCheckBoxMenuItem("Hide Drawings", !chartPanel.getShowDrawings());
        hideDrawingsItem.addActionListener(evt -> chartPanel.setShowDrawings(!hideDrawingsItem.isSelected()));
        menu.add(hideDrawingsItem);
        JCheckBoxMenuItem hideIndicatorsItem = new JCheckBoxMenuItem("Hide Indicators", !chartPanel.getShowIndicators());
        hideIndicatorsItem.addActionListener(evt -> chartPanel.setShowIndicators(!hideIndicatorsItem.isSelected()));
        menu.add(hideIndicatorsItem);
        if (chartPanel.getDataModel().isInReplayMode()) {
            JCheckBoxMenuItem hidePositionsItem = new JCheckBoxMenuItem("Hide Positions & Orders", !chartPanel.getShowPositionsAndOrders());
            hidePositionsItem.addActionListener(evt -> chartPanel.setShowPositionsAndOrders(!hidePositionsItem.isSelected()));
            menu.add(hidePositionsItem);
        }
        menu.show(visibilityButton, 0, visibilityButton.getHeight());
    }

    private void showRemovalMenu(ActionEvent e) {
        JButton removalButton = (JButton) e.getSource();
        ChartPanel chartPanel = mainWindow.getActiveChartPanel();
        if (chartPanel == null) return;
        JPopupMenu menu = new JPopupMenu();
        int drawingCount = DrawingManager.getInstance().getAllDrawings().size();
        if (drawingCount > 0) {
            JMenuItem removeDrawingsItem = new JMenuItem(String.format("Remove %d Drawing(s)", drawingCount));
            removeDrawingsItem.addActionListener(evt -> {
                int choice = JOptionPane.showConfirmDialog(mainWindow, "Are you sure you want to remove all drawings?", "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    DrawingManager.getInstance().clearAllDrawings();
                    chartPanel.repaint();
                }
            });
            menu.add(removeDrawingsItem);
        }
        int indicatorCount = chartPanel.getDataModel().getIndicatorManager().getIndicators().size();
        if (indicatorCount > 0) {
            JMenuItem removeIndicatorsItem = new JMenuItem(String.format("Remove %d Indicator(s)", indicatorCount));
            removeIndicatorsItem.addActionListener(evt -> {
                int choice = JOptionPane.showConfirmDialog(mainWindow, "Are you sure you want to remove all indicators?", "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    chartPanel.getDataModel().getIndicatorManager().clearAllIndicators();
                }
            });
            menu.add(removeIndicatorsItem);
        }
        if (chartPanel.getDataModel().isInReplayMode()) {
            PaperTradingService service = PaperTradingService.getInstance();
            int tradeObjectsCount = service.getOpenPositions().size() + service.getPendingOrders().size();
            if (tradeObjectsCount > 0) {
                JMenuItem removeTradesItem = new JMenuItem(String.format("Remove %d Trade Object(s)", tradeObjectsCount));
                removeTradesItem.setToolTipText("This functionality is currently disabled to prevent accidental account reset.");
                removeTradesItem.setEnabled(false);
                menu.add(removeTradesItem);
            }
        }
        if (menu.getComponentCount() > 0) {
            menu.show(removalButton, 0, removalButton.getHeight());
        }
    }
    
    public boolean isDocked() {
        return this.currentDockSide != DockSide.NONE;
    }

    private void addDragListeners(Component c) {
        c.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                initialClick = e.getPoint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                checkForDocking();
            }
        });
        c.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                Rectangle parentBounds = mainWindow.getContentPane().getBounds();
                Point parentLocationOnScreen = mainWindow.getContentPane().getLocationOnScreen();
                Point currentMouseOnScreen = e.getLocationOnScreen();
                int newX = currentMouseOnScreen.x - initialClick.x;
                int newY = currentMouseOnScreen.y - initialClick.y;
                int minX = parentLocationOnScreen.x;
                int maxX = parentLocationOnScreen.x + parentBounds.width - getWidth();
                int minY = parentLocationOnScreen.y;
                int maxY = parentLocationOnScreen.y + parentBounds.height - getHeight();
                newX = Math.max(minX, Math.min(newX, maxX));
                newY = Math.max(minY, Math.min(newY, maxY));
                setLocation(newX, newY);
                currentDockSide = DockSide.NONE;
                setOrientation(Orientation.HORIZONTAL, true);
            }
        });
    }
    
    public void forceUpdatePosition(DockSide newDefaultSide) {
        this.currentDockSide = DockSide.NONE; 
        updatePosition(newDefaultSide);
    }

    public void updatePosition(DockSide defaultSide) {
        ChartPanel chartPanel = mainWindow.getActiveChartPanel();
        if (chartPanel == null || !chartPanel.isShowing()) return;
        setOrientation(Orientation.VERTICAL, false);
        Point chartLocation = chartPanel.getLocationOnScreen();
        Dimension chartSize = chartPanel.getSize();
        int targetY = chartLocation.y + (chartSize.height / 2) - (getHeight() / 2);
        DockSide sideToDock = isDocked() ? currentDockSide : defaultSide;
        int targetX = (sideToDock == DockSide.LEFT) ? chartLocation.x + 10 : chartLocation.x + chartSize.width - getWidth() - 10;
        setLocation(targetX, targetY);
    }
    
    private void checkForDocking() {
        ChartPanel chartPanel = mainWindow.getActiveChartPanel();
        if (chartPanel == null || !chartPanel.isShowing()) return;
        Point chartLocation = chartPanel.getLocationOnScreen();
        Dimension chartSize = chartPanel.getSize();
        Point toolbarLocation = getLocation();
        if (toolbarLocation.x < chartLocation.x + SNAP_DISTANCE) {
            currentDockSide = DockSide.LEFT;
            updatePosition(DockSide.LEFT);
        } else if (toolbarLocation.x + getWidth() > chartLocation.x + chartSize.width - SNAP_DISTANCE) {
            currentDockSide = DockSide.RIGHT;
            updatePosition(DockSide.RIGHT);
        } else {
            currentDockSide = DockSide.NONE;
            setOrientation(Orientation.HORIZONTAL, false);
        }
    }

    /**
    * Programmatically selects a tool button.
    * @param toolName The base name of the tool, e.g., "Trendline".
    */
    public void activateToolByName(String toolName) {
        if (mainWindow.getActiveChartPanel() == null) return;
        
        JToggleButton buttonToSelect = toolButtonMap.get(toolName);
        if (buttonToSelect != null) {
            buttonToSelect.doClick(); // This will trigger its action listener
            mainWindow.getTitleBarManager().setStaticTitle(toolName + " Active | Click to start drawing");
        }
    }


    public void clearSelection() {
        toolButtonGroup.clearSelection();
    }

    @Override
    public void dispose() {
        SettingsService.getInstance().removePropertyChangeListener(this);
        super.dispose();
    }
}