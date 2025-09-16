package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import javax.swing.event.EventListenerList;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A custom panel containing buttons to select a chart layout.
 * This is designed to be placed inside a JPopupMenu.
 */
public class LayoutSelectionPanel extends JPanel {

    private final EventListenerList listenerList = new EventListenerList();

    // A map of the action command to the icon path
    private static final Map<String, String> LAYOUT_CONFIG = new LinkedHashMap<>();
    static {
        LAYOUT_CONFIG.put("layoutChanged:1 View", UITheme.Icons.LAYOUT_1);
        LAYOUT_CONFIG.put("layoutChanged:2 Views", UITheme.Icons.LAYOUT_2_H);
        LAYOUT_CONFIG.put("layoutChanged:2 Views (Vertical)", UITheme.Icons.LAYOUT_2_V);
        LAYOUT_CONFIG.put("layoutChanged:3 Views (Horizontal)", UITheme.Icons.LAYOUT_3_H);
        LAYOUT_CONFIG.put("layoutChanged:3 Views (Left Stack)", UITheme.Icons.LAYOUT_3_L);
        LAYOUT_CONFIG.put("layoutChanged:3 Views (Right Stack)", UITheme.Icons.LAYOUT_3_R);
        LAYOUT_CONFIG.put("layoutChanged:3 Views (Vertical)", UITheme.Icons.LAYOUT_3_V);
        LAYOUT_CONFIG.put("layoutChanged:4 Views", UITheme.Icons.LAYOUT_4);
        LAYOUT_CONFIG.put("layoutChanged:4 Views (Vertical)", UITheme.Icons.LAYOUT_4_V);
    }

    public LayoutSelectionPanel() {
        // Updated to a grid that better fits the number of options
        setLayout(new GridLayout(3, 3, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);

        for (Map.Entry<String, String> entry : LAYOUT_CONFIG.entrySet()) {
            JButton button = createLayoutButton(entry.getKey(), entry.getValue());
            add(button);
        }
    }

    private JButton createLayoutButton(String actionCommand, String iconPath) {
        Icon icon = UITheme.getIcon(iconPath, 24, 24, UIManager.getColor("Button.foreground"));
        JButton button = new JButton(icon);

        // Style for a clean, flat look inside a popup
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setActionCommand(actionCommand);
        
        // Add listener to fire event to the parent
        button.addActionListener(e -> fireActionPerformed(e.getActionCommand()));

        return button;
    }

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    protected void fireActionPerformed(String command) {
        Object[] listeners = listenerList.getListenerList();
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }
}