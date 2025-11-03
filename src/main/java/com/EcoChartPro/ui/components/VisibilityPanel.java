package com.EcoChartPro.ui.components;

import com.EcoChartPro.model.Timeframe;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

public class VisibilityPanel extends JPanel {

    //EnumMap is only for enums. Use a general-purpose map like LinkedHashMap.
    private final Map<Timeframe, JCheckBox> checkBoxMap = new LinkedHashMap<>();
    private final Consumer<Map<Timeframe, Boolean>> onUpdate;

    public VisibilityPanel(Map<Timeframe, Boolean> initialVisibility, Consumer<Map<Timeframe, Boolean>> onUpdate) {
        this.onUpdate = onUpdate;
        setLayout(new GridBagLayout());
        setBorder(new TitledBorder("Visibility on Timeframes"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0;
        gbc.gridy = 0;

        for (Timeframe tf : Timeframe.getStandardTimeframes()) {
            JCheckBox cb = new JCheckBox(tf.displayName());
            cb.setSelected(initialVisibility.getOrDefault(tf, true));
            cb.addActionListener(e -> notifyUpdate());
            checkBoxMap.put(tf, cb);
            add(cb, gbc);
            
            gbc.gridx++;
            if (gbc.gridx > 2) { // 3 columns
                gbc.gridx = 0;
                gbc.gridy++;
            }
        }
    }

    private void notifyUpdate() {
        if (onUpdate != null) {
            onUpdate.accept(getVisibilityMap());
        }
    }

    /**
     * public getter method to retrieve the current state of the checkboxes.
     * @return A map of timeframes to their visibility state.
     */
    public Map<Timeframe, Boolean> getVisibilityMap() {
        // EnumMap is only for enums. Use a general-purpose map.
        Map<Timeframe, Boolean> visibilityMap = new LinkedHashMap<>();
        for (Map.Entry<Timeframe, JCheckBox> entry : checkBoxMap.entrySet()) {
            visibilityMap.put(entry.getKey(), entry.getValue().isSelected());
        }
        return visibilityMap;
    }
}