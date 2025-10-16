package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dialogs.TimeframeInputDialog;

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
    private final MainWindow owner;
    private final boolean isMac;

    // --- Fields for timeframe input ---
    private final TimeframeInputDialog timeframeInputDialog;
    private final StringBuilder timeframeInputBuffer = new StringBuilder();
    private final Timer timeframeInputTimer;

    private KeyEventDispatcher keyEventDispatcher;

    public KeyboardShortcutManager(JComponent rootComponent, MainWindow owner) {
        this.rootComponent = rootComponent;
        this.owner = owner;
        this.isMac = System.getProperty("os.name").toLowerCase().contains("mac");

        // Initialize timeframe input components
        this.timeframeInputDialog = new TimeframeInputDialog(owner);
        this.timeframeInputTimer = new Timer(3000, e -> clearTimeframeInput());
        this.timeframeInputTimer.setRepeats(false);
    }

    public void setup() {
        InputMap inputMap = rootComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootComponent.getActionMap();

        // --- Drawing Actions ---
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteDrawing");
        actionMap.put("deleteDrawing", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UUID selectedId = DrawingManager.getInstance().getSelectedDrawingId();
                if (selectedId != null) {
                    DrawingManager.getInstance().removeDrawing(selectedId);
                }
            }
        });

        // The Escape action is now partially handled by the KeyEventDispatcher
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
                DrawingManager.getInstance().setSelectedDrawingId(null);
                owner.getTitleBarManager().restoreIdleTitle();
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
                UndoManager.getInstance().undo();
            }
        });

        KeyStroke redoKeyStroke = isMac
            ? KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK)
            : KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(redoKeyStroke, "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UndoManager.getInstance().redo();
            }
        });
        
        // --- [NEW] Global Key Event Dispatcher for Timeframe Input ---
        this.keyEventDispatcher = e -> {
            // We only process events if the main window is active
            if (!owner.isActive()) {
                return false;
            }

            if (e.getID() == KeyEvent.KEY_TYPED) {
                // If the event comes from a text component, let it handle the typing.
                Component focused = e.getComponent();
                if (focused instanceof JTextComponent) {
                    return false; // Do not consume the event, let the text field process it.
                }

                char c = e.getKeyChar();
                if (Character.isLetterOrDigit(c)) {
                    // Start or continue timeframe input
                    timeframeInputBuffer.append(c);
                    ChartPanel activePanel = owner.getActiveChartPanel();
                    if (activePanel != null) {
                        if (!timeframeInputDialog.isVisible()) {
                            timeframeInputDialog.showDialog(activePanel, timeframeInputBuffer.toString());
                        } else {
                            timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                        }
                    }
                    timeframeInputTimer.restart();
                    return true; // Consume the event
                }
            } else if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (timeframeInputBuffer.length() > 0) {
                    // Handle actions only when in timeframe input mode
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ENTER:
                            String input = timeframeInputBuffer.toString().toLowerCase();
                            Timeframe newTf = Timeframe.fromString(input);
                            ChartPanel activePanel = owner.getActiveChartPanel();
                            if (newTf != null && activePanel != null) {
                                activePanel.getDataModel().setDisplayTimeframe(newTf);
                                owner.getTopToolbarPanel().selectTimeframe(newTf.displayName());
                            }
                            clearTimeframeInput();
                            return true; // Consume the event

                        case KeyEvent.VK_BACK_SPACE:
                            timeframeInputBuffer.setLength(timeframeInputBuffer.length() - 1);
                            if (timeframeInputBuffer.length() == 0) {
                                clearTimeframeInput();
                            } else {
                                timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                                timeframeInputTimer.restart();
                            }
                            return true; // Consume the event
                        
                        case KeyEvent.VK_ESCAPE:
                            clearTimeframeInput();
                            return true; // Consume the event
                    }
                }
            }
            return false; // Let the event be processed by other components
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this.keyEventDispatcher);
    }

    private void clearTimeframeInput() {
        timeframeInputBuffer.setLength(0);
        timeframeInputDialog.setVisible(false);
        timeframeInputTimer.stop();
    }

    /**
     * Unregisters the global key listener to prevent memory leaks.
     */
    public void dispose() {
        if (keyEventDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
        }
    }
}