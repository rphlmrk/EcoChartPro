package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class VolumeProfileSettingsPanel extends JPanel {
    private final SettingsService sm;

    private final JCheckBox vrvpVisibleCheckbox;
    private final JCheckBox svpVisibleCheckbox;
    private final JButton upVolumeColorButton;
    private final JButton downVolumeColorButton;
    private final JButton pocColorButton;
    private final JSpinner rowHeightSpinner;

    public VolumeProfileSettingsPanel(SettingsService settingsService) {
        this.sm = settingsService;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize components
        vrvpVisibleCheckbox = new JCheckBox("Show Visible Range Volume Profile (VRVP)");
        vrvpVisibleCheckbox.setSelected(sm.isVrvpVisible());
        
        svpVisibleCheckbox = new JCheckBox("Show Session Volume Profile (SVP)");
        svpVisibleCheckbox.setSelected(sm.isSvpVisible());

        upVolumeColorButton = createColorButton(sm.getVrvpUpVolumeColor());
        downVolumeColorButton = createColorButton(sm.getVrvpDownVolumeColor());
        pocColorButton = createColorButton(sm.getVrvpPocColor());
        
        rowHeightSpinner = new JSpinner(new SpinnerNumberModel(sm.getVrvpRowHeight(), 1, 10, 1));

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);
        gbc.gridwidth = 2;

        add(vrvpVisibleCheckbox, gbc);
        gbc.gridy++; add(svpVisibleCheckbox, gbc);
        gbc.gridwidth = 1;

        gbc.gridy++; gbc.gridx = 0; add(new JLabel("Up Volume Color:"), gbc);
        gbc.gridx++; add(upVolumeColorButton, gbc);
        
        gbc.gridy++; gbc.gridx = 0; add(new JLabel("Down Volume Color:"), gbc);
        gbc.gridx++; add(downVolumeColorButton, gbc);
        
        gbc.gridy++; gbc.gridx = 0; add(new JLabel("Point of Control (POC) Color:"), gbc);
        gbc.gridx++; add(pocColorButton, gbc);
        
        gbc.gridy++; gbc.gridx = 0; add(new JLabel("Row Height (in ticks):"), gbc);
        gbc.gridx++; add(rowHeightSpinner, gbc);

        // Spacer
        gbc.gridy++;
        gbc.weighty = 1.0;
        add(new JPanel(), gbc);
    }

    public void applyChanges() {
        sm.setVrvpVisible(vrvpVisibleCheckbox.isSelected());
        sm.setSvpVisible(svpVisibleCheckbox.isSelected());
        sm.setVrvpUpVolumeColor(upVolumeColorButton.getBackground());
        sm.setVrvpDownVolumeColor(downVolumeColorButton.getBackground());
        sm.setVrvpPocColor(pocColorButton.getBackground());
        sm.setVrvpRowHeight((Integer) rowHeightSpinner.getValue());
    }

    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton(" ");
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(80, 25));
        button.addActionListener(e -> {
            Consumer<Color> onColorUpdate = newColor -> button.setBackground(newColor);
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }
    
    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        return gbc;
    }
}