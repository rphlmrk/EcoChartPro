package com.EcoChartPro.ui.action;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.UndoManager;
import com.EcoChartPro.ui.MainWindow;

import javax.swing.*;
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

    public KeyboardShortcutManager(JComponent rootComponent, MainWindow owner) {
        this.rootComponent = rootComponent;
        this.owner = owner;
        this.isMac = System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public void setup() {
        InputMap inputMap = rootComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootComponent.getActionMap();

        // Drawing Actions
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

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeTool");
        actionMap.put("escapeTool", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (owner.getActiveChartPanel() != null) {
                    owner.getActiveChartPanel().getDrawingController().setActiveTool(null);
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

        // Edit Actions
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
    }
}