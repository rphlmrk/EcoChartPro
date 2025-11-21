package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.ui.WorkspaceManager;
import com.EcoChartPro.ui.home.theme.UITheme;

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

    // A map of the LayoutType enum to the icon path for robust handling
    private static final Map<WorkspaceManager.LayoutType, String> LAYOUT_CONFIG = new LinkedHashMap<>();
    static {
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.ONE, UITheme.Icons.LAYOUT_1);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.TWO, UITheme.Icons.LAYOUT_2_H);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.TWO_VERTICAL, UITheme.Icons.LAYOUT_2_V);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.THREE_HORIZONTAL, UITheme.Icons.LAYOUT_3_H);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.THREE_LEFT, UITheme.Icons.LAYOUT_3_L);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.THREE_RIGHT, UITheme.Icons.LAYOUT_3_R);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.THREE_VERTICAL, UITheme.Icons.LAYOUT_3_V);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.FOUR, UITheme.Icons.LAYOUT_4);
        LAYOUT_CONFIG.put(WorkspaceManager.LayoutType.FOUR_VERTICAL, UITheme.Icons.LAYOUT_4_V);
    }

    public LayoutSelectionPanel() {
        // Updated to a grid that better fits the number of options
        setLayout(new GridLayout(3, 3, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);

        for (Map.Entry<WorkspaceManager.LayoutType, String> entry : LAYOUT_CONFIG.entrySet()) {
            JButton button = createLayoutButton(entry.getKey(), entry.getValue());
            add(button);
        }
    }

    private JButton createLayoutButton(WorkspaceManager.LayoutType layoutType, String iconPath) {
        Icon icon = UITheme.getIcon(iconPath, 24, 24, UIManager.getColor("Button.foreground"));
        JButton button = new JButton(icon);

        // Style for a clean, flat look inside a popup
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // The action command is now the direct enum name, which is robust
        button.setActionCommand("layoutChanged:" + layoutType.name());
        
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