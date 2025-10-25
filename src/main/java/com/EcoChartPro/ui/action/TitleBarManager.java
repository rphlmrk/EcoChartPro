package com.EcoChartPro.ui.action;

import javax.swing.*;
import java.util.List;

/**
 * Manages the dynamic, context-sensitive title bar for a JFrame.
 */
public class TitleBarManager {

    private final JFrame owner;
    private final String baseTitle = ""; // Set base title to empty

    public TitleBarManager(JFrame owner) {
        this.owner = owner;
    }

    /**
     * Starts the manager by setting the base title.
     */
    public void start() {
        owner.setTitle(baseTitle);
    }

    /**
     * Sets a persistent, static message on the title bar.
     * @param text The message to display.
     */
    public void setStaticTitle(String text) {
        owner.setTitle(text);
    }

    /**
     * [NEW] Sets a specific title for when a drawing tool is active.
     * It stops the idle animation and provides helpful cancellation instructions.
     * @param toolName The name of the active tool (e.g., "Trendline").
     */
    public void setToolActiveTitle(String toolName) {
        // The DrawingController uses a Right-click to cancel, which is more accurate here.
        owner.setTitle(String.format("%s Active | Right-click or Esc to cancel", toolName));
    }

    /**
     * Restores the title bar to its idle state, showing the base application title.
     */
    public void restoreIdleTitle() {
        owner.setTitle(baseTitle);
    }

    /**
     * Disposes of any resources.
     */
    public void dispose() {
        // No resources to dispose
    }
}