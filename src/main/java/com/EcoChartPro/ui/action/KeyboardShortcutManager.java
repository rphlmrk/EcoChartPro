package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.ChartWorkspacePanel;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dialogs.SymbolSearchDialog;
import com.EcoChartPro.ui.dialogs.TimeframeInputDialog;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.UUID;

/**
 * Manages the registration of global keyboard shortcuts for the main application window.
 */
public class KeyboardShortcutManager {

    private final JComponent rootComponent;
    private final ChartWorkspacePanel owner;
    private final WorkspaceContext workspaceContext; // [NEW]
    private final boolean isMac;

    // --- Fields for timeframe input ---
    private final TimeframeInputDialog timeframeInputDialog;
    private final StringBuilder timeframeInputBuffer = new StringBuilder();
    private final Timer timeframeInputTimer;

    // --- Fields for symbol search ---
    private final SymbolSearchDialog symbolSearchDialog;
    private final StringBuilder symbolSearchBuffer = new StringBuilder();
    private final Timer symbolSearchTimer;

    private KeyEventDispatcher keyEventDispatcher;

    public KeyboardShortcutManager(JComponent rootComponent, ChartWorkspacePanel owner, WorkspaceContext context) { // [MODIFIED]
        this.rootComponent = rootComponent;
        this.owner = owner;
        this.workspaceContext = context; // [NEW]
        this.isMac = System.getProperty("os.name").toLowerCase().contains("mac");

        // Initialize timeframe input components
        this.timeframeInputDialog = new TimeframeInputDialog(owner.getFrameOwner());
        this.timeframeInputTimer = new Timer(3000, e -> clearTimeframeInput());
        this.timeframeInputTimer.setRepeats(false);

        // Initialize symbol search components and add listener for MOUSE clicks
        this.symbolSearchDialog = new SymbolSearchDialog(owner.getFrameOwner(), owner.getReplayController().isPresent());
        this.symbolSearchDialog.addActionListener(e -> {
            if (e.getSource() instanceof ChartDataSource) {
                owner.changeActiveSymbol((ChartDataSource) e.getSource());
            }
            clearSymbolSearch(); // Always clear on close/select
        });
        
        // Make the timer's action mouse-aware and fix forward reference
        this.symbolSearchTimer = new Timer(3000, e -> {
            if (symbolSearchDialog.isVisible()) {
                // Get the current mouse position on the screen
                Point mousePos = MouseInfo.getPointerInfo().getLocation();
                
                // Get the dialog's bounds on the screen
                Rectangle dialogBounds = symbolSearchDialog.getBounds();
                dialogBounds.setLocation(symbolSearchDialog.getLocationOnScreen());

                // If the mouse is inside the dialog, reset the timer and do not close.
                // Otherwise, close the dialog.
                if (dialogBounds.contains(mousePos)) {
                    // Get the timer from the event source to avoid illegal forward reference
                    ((Timer) e.getSource()).restart();
                } else {
                    clearSymbolSearch();
                }
            }
        });
        this.symbolSearchTimer.setRepeats(false);
    }

    public void setup() {
        InputMap inputMap = rootComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootComponent.getActionMap();

        // --- Drawing Actions ---
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteDrawing");
        actionMap.put("deleteDrawing", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UUID selectedId = workspaceContext.getDrawingManager().getSelectedDrawingId(); // [MODIFIED]
                if (selectedId != null) {
                    workspaceContext.getDrawingManager().removeDrawing(selectedId); // [MODIFIED]
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeTool");
        actionMap.put("escapeTool", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (owner.getActiveChartPanel() != null) {
                    ChartPanel activePanel = owner.getActiveChartPanel();
                    if (activePanel.isPriceSelectionMode()) {
                        if (activePanel.getPriceSelectionCallback() != null) {
                            activePanel.getPriceSelectionCallback().accept(null);
                        }
                        activePanel.exitPriceSelectionMode();
                    } else {
                        activePanel.getDrawingController().setActiveTool(null);
                    }
                }
                owner.getDrawingToolbar().clearSelection();
                workspaceContext.getDrawingManager().setSelectedDrawingId(null); // [MODIFIED]
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK), "activateTrendline");
        actionMap.put("activateTrendline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                owner.getDrawingToolbar().activateToolByName("Trendline");
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK), "activateRectangle");
        actionMap.put("activateRectangle", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                owner.getDrawingToolbar().activateToolByName("Rectangle");
            }
        });

        // --- Edit Actions ---
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                workspaceContext.getUndoManager().undo(); // [MODIFIED]
            }
        });

        KeyStroke redoKeyStroke = isMac
            ? KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK)
            : KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(redoKeyStroke, "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                workspaceContext.getUndoManager().redo(); // [MODIFIED]
            }
        });
        
        // --- Global Key Event Dispatcher ---
        this.keyEventDispatcher = e -> {
            // Check if the panel is showing on screen before processing key events.
            if (!owner.isShowing()) return false;
            
            Component focused = e.getComponent();
            if (focused instanceof JTextComponent) return false;

            ChartPanel activePanel = owner.getActiveChartPanel();

            if (e.getID() == KeyEvent.KEY_TYPED) {
                char c = e.getKeyChar();
                if (symbolSearchDialog.isVisible()) {
                    if (Character.isLetterOrDigit(c) || c == '-' || c == '/') {
                        symbolSearchBuffer.append(c);
                        symbolSearchDialog.updateSearch(symbolSearchBuffer.toString());
                        symbolSearchTimer.restart();
                    }
                    return true;
                }
                if (timeframeInputDialog.isVisible()) {
                    String currentInput = timeframeInputBuffer.toString();
                    boolean hasUnit = currentInput.matches(".*[mhdMHD]$");
                    if (Character.isDigit(c) && !hasUnit) {
                        timeframeInputBuffer.append(c);
                    } else if (Character.isLetter(c) && !hasUnit && "mhd".indexOf(Character.toLowerCase(c)) != -1) {
                        timeframeInputBuffer.append(c);
                    }
                    timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                    timeframeInputTimer.restart();
                    return true;
                }
                if (Character.isDigit(c)) {
                    timeframeInputBuffer.append(c);
                    if (activePanel != null) timeframeInputDialog.showDialog(activePanel, timeframeInputBuffer.toString());
                    timeframeInputTimer.restart();
                    return true;
                } else if (Character.isLetter(c)) {
                    symbolSearchBuffer.append(c);
                    if (activePanel != null) symbolSearchDialog.showDialog(activePanel, symbolSearchBuffer.toString());
                    symbolSearchTimer.restart();
                    return true;
                }

            } else if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (symbolSearchDialog.isVisible()) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ENTER:
                            symbolSearchDialog.confirmSelection(); // This will fire the action listener
                            return true;
                        case KeyEvent.VK_ESCAPE:
                            clearSymbolSearch();
                            return true;
                        case KeyEvent.VK_UP:
                            symbolSearchDialog.moveSelectionUp();
                            symbolSearchTimer.restart(); // Reset timer on navigation
                            return true;
                        case KeyEvent.VK_DOWN:
                            symbolSearchDialog.moveSelectionDown();
                            symbolSearchTimer.restart(); // Reset timer on navigation
                            return true;
                        case KeyEvent.VK_BACK_SPACE:
                            symbolSearchBuffer.setLength(Math.max(0, symbolSearchBuffer.length() - 1));
                            if (symbolSearchBuffer.length() == 0) clearSymbolSearch();
                            else {
                                symbolSearchDialog.updateSearch(symbolSearchBuffer.toString());
                                symbolSearchTimer.restart();
                            }
                            return true;
                    }
                }
                if (timeframeInputDialog.isVisible()) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ENTER:
                            Timeframe newTf = Timeframe.fromString(timeframeInputBuffer.toString().toLowerCase());
                            if (newTf != null && activePanel != null) {
                                activePanel.getDataModel().setDisplayTimeframe(newTf);
                                owner.getTopToolbarPanel().selectTimeframe(newTf.displayName());
                            }
                            clearTimeframeInput();
                            return true;
                        case KeyEvent.VK_BACK_SPACE:
                            timeframeInputBuffer.setLength(Math.max(0, timeframeInputBuffer.length() - 1));
                            if (timeframeInputBuffer.length() == 0) clearTimeframeInput();
                            else {
                                timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                                timeframeInputTimer.restart();
                            }
                            return true;
                        case KeyEvent.VK_ESCAPE:
                            clearTimeframeInput();
                            return true;
                    }
                }
            }
            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this.keyEventDispatcher);
    }

    private void clearTimeframeInput() {
        timeframeInputBuffer.setLength(0);
        timeframeInputDialog.setVisible(false);
        timeframeInputTimer.stop();
    }
    
    private void clearSymbolSearch() {
        symbolSearchBuffer.setLength(0);
        symbolSearchDialog.setVisible(false);
        symbolSearchTimer.stop();
    }

    public void dispose() {
        if (keyEventDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
        }
    }
}