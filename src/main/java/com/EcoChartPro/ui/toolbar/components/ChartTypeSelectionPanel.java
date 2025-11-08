package com.EcoChartPro.ui.toolbar.components;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.model.chart.ChartType;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ChartTypeSelectionPanel extends JPanel implements PropertyChangeListener {

    private final EventListenerList listenerList = new EventListenerList();
    private final JCheckBox vrvpCheckBox;
    private final JCheckBox svpCheckBox;

    public ChartTypeSelectionPanel() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Add Chart Type buttons
        int columnCount = 0;
        for (ChartType type : ChartType.values()) {
            JButton button = new JButton(type.getDisplayName());
            button.setFocusPainted(false);
            button.setMargin(new Insets(4, 8, 4, 8));
            button.setActionCommand("chartTypeChanged:" + type.name());
            button.addActionListener(e -> fireActionPerformed(e.getActionCommand()));
            
            gbc.gridx = columnCount % 2;
            gbc.gridy = columnCount / 2;
            add(button, gbc);
            columnCount++;
        }

        // Add Separator
        gbc.gridy = (columnCount + 1) / 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(8, 0, 8, 0);
        add(new JSeparator(), gbc);
        
        // Reset constraints for checkboxes
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 5, 2, 5); // Add some horizontal padding for checkboxes

        SettingsService sm = SettingsService.getInstance();

        // Add VRVP Checkbox
        gbc.gridy++;
        vrvpCheckBox = new JCheckBox("Visible Range Volume Profile");
        vrvpCheckBox.setSelected(sm.isVrvpVisible());
        vrvpCheckBox.setOpaque(false);
        vrvpCheckBox.addActionListener(e -> sm.setVrvpVisible(vrvpCheckBox.isSelected()));
        add(vrvpCheckBox, gbc);

        // Add SVP Checkbox
        gbc.gridy++;
        svpCheckBox = new JCheckBox("Session Volume Profile");
        svpCheckBox.setSelected(sm.isSvpVisible());
        svpCheckBox.setOpaque(false);
        svpCheckBox.addActionListener(e -> sm.setSvpVisible(svpCheckBox.isSelected()));
        add(svpCheckBox, gbc);

        // Listen for external changes to update the checkboxes
        sm.addPropertyChangeListener("volumeProfileVisibilityChanged", this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // This ensures the checkboxes update if the setting is changed elsewhere
        if ("volumeProfileVisibilityChanged".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                SettingsService sm = SettingsService.getInstance();
                vrvpCheckBox.setSelected(sm.isVrvpVisible());
                svpCheckBox.setSelected(sm.isSvpVisible());
            });
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