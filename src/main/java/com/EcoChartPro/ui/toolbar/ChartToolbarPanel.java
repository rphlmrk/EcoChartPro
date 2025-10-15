package com.EcoChartPro.ui.toolbar;

import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.IndicatorDialog;
import com.EcoChartPro.ui.toolbar.components.LayoutSelectionPanel;
import com.EcoChartPro.ui.toolbar.components.SymbolSelectionPanel;
import com.EcoChartPro.ui.toolbar.components.TimeframeSelectionPanel;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;
import com.EcoChartPro.utils.DataSourceManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ChartToolbarPanel extends JPanel {

    private final JButton symbolSelectorButton;
    private ChartDataSource selectedDataSource;

    private final JButton timeframeButton;
    private final JButton layoutButton;
    private final JToggleButton crosshairSyncButton;
    
    private final JPopupMenu symbolPopup;
    private final JPopupMenu timeframePopup;
    private final JPopupMenu layoutPopup;

    private final boolean isReplayMode;
    private final JButton undoButton;
    private final JButton redoButton;

    private final Icon undoEnabledIcon;
    private final Icon undoDisabledIcon;
    private final Icon redoEnabledIcon;
    private final Icon redoDisabledIcon;

    public ChartToolbarPanel(boolean isReplayMode) {
        this.isReplayMode = isReplayMode;
        setLayout(new BorderLayout());
        setBackground(UIManager.getColor("ToolBar.background"));
        setPreferredSize(new Dimension(0, 45));
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));

        this.undoEnabledIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_LEFT, 18, 18, UIManager.getColor("Button.foreground"));
        this.undoDisabledIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_LEFT, 18, 18, UIManager.getColor("Button.disabledText"));
        this.redoEnabledIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_RIGHT, 18, 18, UIManager.getColor("Button.foreground"));
        this.redoDisabledIcon = UITheme.getIcon(UITheme.Icons.ARROW_CIRCLE_RIGHT, 18, 18, UIManager.getColor("Button.disabledText"));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 8));
        leftPanel.setOpaque(false);

        symbolSelectorButton = new JButton();
        styleToolbarButton(symbolSelectorButton);
        leftPanel.add(symbolSelectorButton);

        symbolSelectorButton.setIcon(UITheme.getIcon(UITheme.Icons.SEARCH, 18, 18));
        setCurrentSymbol(null); 
        
        leftPanel.add(Box.createHorizontalStrut(10));
        leftPanel.add(createToolbarSeparator());
        leftPanel.add(Box.createHorizontalStrut(5));

        timeframeButton = new JButton("Timeframe");
        styleToolbarButton(timeframeButton);
        timeframeButton.setToolTipText("Select Chart Timeframe");
        timeframeButton.setIcon(UITheme.getIcon(UITheme.Icons.CLOCK, 16, 16));
        leftPanel.add(timeframeButton);

        layoutButton = new JButton(UITheme.getIcon(UITheme.Icons.LAYOUT_GRID, 18, 18));
        styleToolbarButton(layoutButton);
        layoutButton.setToolTipText("Change Chart Layout");
        layoutButton.setEnabled(true);
        leftPanel.add(layoutButton);
        
        leftPanel.add(Box.createHorizontalStrut(5));
        leftPanel.add(createToolbarSeparator());
        leftPanel.add(Box.createHorizontalStrut(5));

        JButton indicatorsButton = new JButton("ƒx Indicators");
        styleToolbarButton(indicatorsButton);
        indicatorsButton.setToolTipText("Add, remove, or edit indicators");
        indicatorsButton.setIcon(UITheme.getIcon(UITheme.Icons.INDICATORS, 16, 16, UIManager.getColor("Button.disabledText")));
        indicatorsButton.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            if (owner instanceof MainWindow mainWindow) {
                ChartPanel activePanel = mainWindow.getActiveChartPanel();
                if (activePanel != null) {
                    new IndicatorDialog(owner, activePanel).setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(owner, "Please select a chart panel first.", "No Active Chart", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        leftPanel.add(indicatorsButton);
        
        leftPanel.add(Box.createHorizontalStrut(5));
        leftPanel.add(createToolbarSeparator());
        leftPanel.add(Box.createHorizontalStrut(5));

        undoButton = new JButton(undoDisabledIcon);
        styleToolbarButton(undoButton);
        undoButton.setToolTipText("Undo (Ctrl+Z)");
        undoButton.addActionListener(e -> UndoManager.getInstance().undo());
        leftPanel.add(undoButton);
        redoButton = new JButton(redoDisabledIcon);
        styleToolbarButton(redoButton);
        redoButton.setToolTipText("Redo (Ctrl+Y)");
        redoButton.addActionListener(e -> UndoManager.getInstance().redo());
        leftPanel.add(redoButton);

        leftPanel.add(Box.createHorizontalStrut(5));
        leftPanel.add(createToolbarSeparator());
        leftPanel.add(Box.createHorizontalStrut(5));

        crosshairSyncButton = new JToggleButton(UITheme.getIcon(UITheme.Icons.CROSSHAIR, 18, 18));
        styleToolbarButton(crosshairSyncButton);
        crosshairSyncButton.setToolTipText("Synchronize Crosshair Across All Panels");
        crosshairSyncButton.setSelected(true); 
        crosshairSyncButton.addActionListener(e -> {
            CrosshairManager.getInstance().setSyncEnabled(crosshairSyncButton.isSelected());
        });
        leftPanel.add(crosshairSyncButton);

        add(leftPanel, BorderLayout.WEST);
        
        if (isReplayMode) {
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 8));
            rightPanel.setOpaque(false);
            JButton buyButton = createTradeButton("BUY", "placeLongOrder", UIManager.getColor("app.color.positive"));
            rightPanel.add(buyButton);
            JButton sellButton = createTradeButton("SELL", "placeShortOrder", UIManager.getColor("app.color.negative"));
            rightPanel.add(sellButton);
            add(rightPanel, BorderLayout.EAST);
        }
        
        this.timeframePopup = createPopupMenu(new TimeframeSelectionPanel());
        this.layoutPopup = createPopupMenu(new LayoutSelectionPanel());
        this.symbolPopup = new JPopupMenu();
        this.symbolPopup.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        SymbolSelectionPanel symbolPanel = new SymbolSelectionPanel();
        symbolPanel.addActionListener(e -> {
            if (e.getSource() instanceof ChartDataSource) {
                ChartDataSource selected = (ChartDataSource) e.getSource();
                setCurrentSymbol(selected);
                symbolPopup.setVisible(false);
                fireActionEvent("selectionChanged");
            }
        });
        this.symbolPopup.add(symbolPanel);

        setupHoverPopup(symbolSelectorButton, symbolPopup);
        setupHoverPopup(timeframeButton, timeframePopup);
        setupHoverPopup(layoutButton, layoutPopup);
    }

    private JPopupMenu createPopupMenu(JPanel contentPanel) {
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        if (contentPanel instanceof TimeframeSelectionPanel panel) {
            panel.addActionListener(e -> {
                if (e.getSource() instanceof Timeframe) {
                    ActionEvent newEvent = new ActionEvent(e.getSource(), e.getID(), "timeframeChanged");
                    fireActionEvent(newEvent);
                } else {
                    fireActionEvent(e.getActionCommand());
                    popupMenu.setVisible(false);
                }
            });
        } else if (contentPanel instanceof LayoutSelectionPanel panel) {
            panel.addActionListener(e -> {
                // The action command is now already in the correct format (e.g., "layoutChanged:TWO_VERTICAL")
                fireActionEvent(e.getActionCommand());
                popupMenu.setVisible(false);
            });
        }
        popupMenu.add(contentPanel);
        return popupMenu;
    }

    private void addRecursiveMouseListener(Component component, MouseAdapter adapter) {
        component.addMouseListener(adapter);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                addRecursiveMouseListener(child, adapter);
            }
        }
    }

    private void setupHoverPopup(final AbstractButton button, final JPopupMenu popup) {
        final Timer hideTimer = new Timer(300, e -> {
            if (!popup.isVisible()) {
                return;
            }

            Point mousePosOnScreen = MouseInfo.getPointerInfo().getLocation();
            Rectangle buttonBounds = new Rectangle(button.getLocationOnScreen(), button.getSize());
            boolean onButton = buttonBounds.contains(mousePosOnScreen);
            Rectangle popupBounds = new Rectangle(popup.getLocationOnScreen(), popup.getSize());
            boolean onPopup = popupBounds.contains(mousePosOnScreen);

            if (!onButton && !onPopup) {
                popup.setVisible(false);
            }
        });
        hideTimer.setRepeats(false);

        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hideTimer.stop();
                if (button.isEnabled() && !popup.isVisible()) {
                    popup.show(button, 0, button.getHeight());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hideTimer.start();
            }
        };

        button.addMouseListener(hoverListener);
        addRecursiveMouseListener(popup, hoverListener);
    }
    
    public void setUndoEnabled(boolean enabled) {
        undoButton.setEnabled(enabled);
        undoButton.setIcon(enabled ? undoEnabledIcon : undoDisabledIcon);
    }

    public void setRedoEnabled(boolean enabled) {
        redoButton.setEnabled(enabled);
        redoButton.setIcon(enabled ? redoEnabledIcon : redoDisabledIcon);
    }

    private JSeparator createToolbarSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(UIManager.getColor("Separator.foreground"));
        return separator;
    }

    private void styleToolbarButton(AbstractButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setForeground(UIManager.getColor("Button.foreground"));
    }

    private JButton createTradeButton(String text, String actionCommand, Color color) {
        JButton button = new JButton(text);
        button.setActionCommand(actionCommand);
        button.setFocusPainted(false);
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setMargin(new Insets(4, 15, 4, 15));
        button.addActionListener(e -> fireActionEvent(e.getActionCommand()));
        return button;
    }

    public void selectTimeframe(String tf) {
        if (tf != null && !tf.isBlank()) {
            timeframeButton.setText(tf);
        }
    }

    private void fireActionEvent(ActionEvent e) {
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }
    
    private void fireActionEvent(String command) {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        fireActionEvent(e);
    }
    
    public void populateTimeframes(List<String> availableTimeframes) {
        timeframeButton.setEnabled(availableTimeframes != null && !availableTimeframes.isEmpty());
    }

    public void setCurrentSymbol(ChartDataSource source) {
        this.selectedDataSource = source;
        if (source != null) {
            symbolSelectorButton.setText(source.displayName());
            if (isReplayMode) {
                symbolSelectorButton.setToolTipText("Current Session: " + source.displayName() + ". Click to start a new session.");
            } else {
                symbolSelectorButton.setToolTipText("Symbol: " + source.displayName());
            }
        } else {
            symbolSelectorButton.setText("Select Symbol");
            symbolSelectorButton.setToolTipText("Search and select a symbol");
        }
    }
    
    public ChartDataSource getSelectedDataSource() { return this.selectedDataSource; }
    public void addActionListener(ActionListener l) { listenerList.add(ActionListener.class, l); }
    public void removeActionListener(ActionListener l) { listenerList.remove(ActionListener.class, l); }

    public JButton getTimeframeButton() {
        return timeframeButton;
    }
}