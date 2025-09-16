package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.model.Timeframe;

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
        // Use a grid layout for a neat arrangement of timeframe buttons
        setLayout(new GridLayout(0, 4, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);
        
        List<Timeframe> allTimeframes = List.of(Timeframe.values());
        
        for (Timeframe tf : allTimeframes) {
            JButton button = new JButton(tf.getDisplayName());
            button.setFocusPainted(false);
            button.setMargin(new Insets(4, 8, 4, 8));
            button.setActionCommand("timeframeChanged:" + tf.getDisplayName());
            
            button.addActionListener(e -> fireActionPerformed(e.getActionCommand()));
            
            add(button);
        }
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