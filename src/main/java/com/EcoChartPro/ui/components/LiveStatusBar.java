package com.EcoChartPro.ui.components;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * The main status bar component for the Live Trading tab.
 * Contains connectivity status on the left and world clocks on the right.
 */
public class LiveStatusBar extends JPanel {

    private final CompactConnectionWidget connectionWidget;
    private final MultiClockWidget clockWidget;

    public LiveStatusBar() {
        super(new BorderLayout());

        // Style
        // Use the titlebar background color for consistency, or a slightly darker panel
        // background
        Color bg = UIManager.getColor("app.titlebar.background");
        if (bg == null)
            bg = UIManager.getColor("Panel.background");
        setBackground(bg);
        setOpaque(true);

        // Top border line
        setBorder(new MatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")));
        setPreferredSize(new Dimension(0, 24)); // Compact height

        // Initialize child widgets
        connectionWidget = new CompactConnectionWidget();
        clockWidget = new MultiClockWidget();

        add(connectionWidget, BorderLayout.WEST);
        add(clockWidget, BorderLayout.EAST);
    }

    /**
     * Clean up resources (timers, listeners) when the bar is destroyed.
     */
    public void dispose() {
        // Removing components triggers removeNotify() on them,
        // which cleans up their individual listeners/timers.
        removeAll();
    }
}