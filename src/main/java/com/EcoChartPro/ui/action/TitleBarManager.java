package com.EcoChartPro.ui.action;

import javax.swing.*;
import java.util.List;

/**
 * Manages the dynamic, context-sensitive title bar for a JFrame.
 * It can display an idle, rotating list of shortcuts or a static, context-specific message.
 */
public class TitleBarManager {

    private final JFrame owner;
    private final Timer idleShortcutTimer;
    private final List<String> idleShortcuts;
    private int currentShortcutIndex = 0;

    public TitleBarManager(JFrame owner) {
        this.owner = owner;
        boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");

        // Initialize idle shortcuts based on OS
        String undoShortcut = isMac ? "Cmd+Z: Undo" : "Ctrl+Z: Undo";
        String redoShortcut = isMac ? "Cmd+Shift+Z: Redo" : "Ctrl+Y: Redo";
        this.idleShortcuts = List.of(
            "Alt+T: Trendline",
            "Alt+R: Rectangle",
            "On Chart: Type Timeframe (e.g., 5m, 1h) + Enter",
            undoShortcut,
            redoShortcut
        );

        this.idleShortcutTimer = new Timer(3000, e -> {
            currentShortcutIndex = (currentShortcutIndex + 1) % idleShortcuts.size();
            owner.setTitle(idleShortcuts.get(currentShortcutIndex));
        });
        this.idleShortcutTimer.setInitialDelay(0);
    }

    /**
     * Starts the idle animation.
     */
    public void start() {
        idleShortcutTimer.start();
    }

    /**
     * Sets a persistent, static message on the title bar, stopping the idle animation.
     * @param text The message to display.
     */
    public void setStaticTitle(String text) {
        if (idleShortcutTimer.isRunning()) {
            idleShortcutTimer.stop();
        }
        owner.setTitle(text);
    }

    /**
     * [NEW] Sets a specific title for when a drawing tool is active.
     * It stops the idle animation and provides helpful cancellation instructions.
     * @param toolName The name of the active tool (e.g., "Trendline").
     */
    public void setToolActiveTitle(String toolName) {
        if (idleShortcutTimer.isRunning()) {
            idleShortcutTimer.stop();
        }
        // The DrawingController uses a Right-click to cancel, which is more accurate here.
        owner.setTitle(String.format("%s Active | Right-click or Esc to cancel", toolName));
    }

    /**
     * Restores the title bar to its idle state, showing rotating shortcuts.
     */
    public void restoreIdleTitle() {
        if (!idleShortcutTimer.isRunning()) {
            // Immediately set the first hint to avoid a delay
            owner.setTitle(idleShortcuts.get(currentShortcutIndex));
            idleShortcutTimer.start();
        }
    }

    /**
     * Stops the timer to prevent memory leaks when the window is closed.
     */
    public void dispose() {
        if (idleShortcutTimer != null) {
            idleShortcutTimer.stop();
        }
    }
}