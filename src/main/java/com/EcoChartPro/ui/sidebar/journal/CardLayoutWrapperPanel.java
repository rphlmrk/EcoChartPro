package com.EcoChartPro.ui.sidebar.journal;

import javax.swing.*;
import java.awt.*;

/**
 * A wrapper panel that correctly reports its preferred size based on the
 * currently visible component in its CardLayout. This is the definitive
 * solution for dynamically-sized card layouts.
 */
public class CardLayoutWrapperPanel extends JPanel {

    public CardLayoutWrapperPanel() {
        super(new CardLayout());
    }

    @Override
    public Dimension getPreferredSize() {
        // Find the component that is currently set to be visible.
        for (Component c : getComponents()) {
            if (c.isVisible()) {
                // Return the preferred size of ONLY the visible component.
                return c.getPreferredSize();
            }
        }
        // Fallback if no component is visible.
        return super.getPreferredSize();
    }
}