package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.dialogs.CustomTimeframeDialog;

import javax.swing.*;
import javax.swing.event.EventListenerList;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

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
            // --- Custom Timeframe Dialog ---

            // [FIX] Retrieve the actual application window.
            // Since this panel is inside a JPopupMenu, 'getWindowAncestor(this)' returns
            // the popup's HeavyWeightWindow, which causes the IllegalArgumentException.
            // We must get the JPopupMenu's invoker (the toolbar button) and get its window.
            Window owner = null;
            Container parent = this.getParent();
            if (parent instanceof JPopupMenu) {
                Component invoker = ((JPopupMenu) parent).getInvoker();
                if (invoker != null) {
                    owner = SwingUtilities.getWindowAncestor(invoker);
                }
            }

            // Fallback if not in a popup (e.g. testing)
            if (owner == null) {
                owner = SwingUtilities.getWindowAncestor(this);
            }

            // Final safety check: ensure we don't pass a HeavyWeightWindow
            if (owner != null && owner.getClass().getName().contains("HeavyWeightWindow")) {
                owner = null; // JDialog accepts null (creates a default owner)
            }

            CustomTimeframeDialog dialog = new CustomTimeframeDialog(owner);
            dialog.setVisible(true);

            Timeframe customTf = dialog.getCustomTimeframe();
            if (customTf != null) {
                // Fire a rich ActionEvent with the Timeframe object as the source
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

    // --- Custom Timeframe Dialog ---
    protected void fireCustomActionPerformed(ActionEvent e) {
        // Find the parent JPopupMenu and hide it before firing the event.
        Component parent = this;
        while (parent != null && !(parent instanceof JPopupMenu)) {
            parent = parent.getParent();
        }
        if (parent instanceof JPopupMenu) {
            ((JPopupMenu) parent).setVisible(false);
        }

        // Fire the event to the parent toolbar
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ActionListener.class) {
                ((ActionListener) listeners[i + 1]).actionPerformed(e);
            }
        }
    }
}