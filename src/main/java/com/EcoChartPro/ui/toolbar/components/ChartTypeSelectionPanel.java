package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.model.chart.ChartType;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChartTypeSelectionPanel extends JPanel {

    private final EventListenerList listenerList = new EventListenerList();

    public ChartTypeSelectionPanel() {
        setLayout(new GridLayout(0, 2, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);

        for (ChartType type : ChartType.values()) {
            JButton button = new JButton(type.getDisplayName());
            button.setFocusPainted(false);
            button.setMargin(new Insets(4, 8, 4, 8));
            button.setActionCommand("chartTypeChanged:" + type.name());
            button.addActionListener(e -> fireActionPerformed(e.getActionCommand()));
            add(button);
        }
    }

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    protected void fireActionPerformed(String command) {
        // Find the parent JPopupMenu and hide it.
        Component parent = this;
        while (parent != null && !(parent instanceof JPopupMenu)) {
            parent = parent.getParent();
        }
        if (parent instanceof JPopupMenu) {
            ((JPopupMenu) parent).setVisible(false);
        }

        // Fire the event to the parent toolbar.
        Object[] listeners = listenerList.getListenerList();
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command);
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }
}