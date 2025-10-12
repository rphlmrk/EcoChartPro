package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.dialogs.CustomTimeframeDialog;

import javax.swing.*;
import javax.swing.event.EventListenerList;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * A custom panel containing buttons for all available timeframes.
 * This is designed to be placed inside a JPopupMenu.
 */
public class TimeframeSelectionPanel extends JPanel {
    
    private final EventListenerList listenerList = new EventListenerList();

    public TimeframeSelectionPanel() {
        setLayout(new GridLayout(0, 4, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);
        
        List<Timeframe> allTimeframes = Timeframe.getStandardTimeframes();
        
        for (Timeframe tf : allTimeframes) {
            JButton button = new JButton(tf.displayName());
            button.setFocusPainted(false);
            button.setMargin(new Insets(4, 8, 4, 8));
            button.setActionCommand("timeframeChanged:" + tf.displayName());
            
            button.addActionListener(e -> fireActionPerformed(e.getActionCommand()));
            
            add(button);
        }

        JButton customButton = new JButton("Custom...");
        customButton.setFocusPainted(false);
        customButton.setMargin(new Insets(4, 8, 4, 8));
        customButton.addActionListener(e -> {
            // [FIX] Find the owner Frame in a more robust way.
            // 1. Get the popup menu that contains this panel.
            JPopupMenu popup = (JPopupMenu) SwingUtilities.getAncestorOfClass(JPopupMenu.class, this);
            if (popup == null) return; // Should not happen

            // 2. Get the component that the popup is attached to (the invoker).
            Component invoker = popup.getInvoker();
            if (invoker == null) return;

            // 3. Find the top-level Frame from that component.
            Frame owner = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, invoker);

            // Now, we can safely create the dialog.
            CustomTimeframeDialog dialog = new CustomTimeframeDialog(owner);
            dialog.setVisible(true); 

            Timeframe customTf = dialog.getCustomTimeframe();
            if (customTf != null) {
                ActionEvent customEvent = new ActionEvent(customTf, ActionEvent.ACTION_PERFORMED, "timeframeChanged");
                fireCustomActionPerformed(customEvent);
            }
        });
        add(customButton);
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

    protected void fireCustomActionPerformed(ActionEvent e) {
        Component parent = this;
        while (parent != null && !(parent instanceof JPopupMenu)) {
            parent = parent.getParent();
        }
        if (parent instanceof JPopupMenu) {
            ((JPopupMenu) parent).setVisible(false);
        }

        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }
}