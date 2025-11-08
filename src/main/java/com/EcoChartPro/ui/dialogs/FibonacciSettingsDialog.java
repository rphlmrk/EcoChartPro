package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DrawingConfig.DrawingToolTemplate;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject.FibLevelProperties;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.VisibilityPanel;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A dialog for configuring the levels of a Fibonacci drawing tool.
 */
public class FibonacciSettingsDialog extends JDialog {

    private final Map<Double, JCheckBox> enabledCheckBoxes = new LinkedHashMap<>();
    private final Map<Double, JButton> colorButtons = new LinkedHashMap<>();
    private final Map<Double, FibLevelProperties> initialLevels;
    private final Consumer<SaveResult> onSaveCallback;
    private VisibilityPanel visibilityPanel;
    private final String toolClassName;
    private JCheckBox showLabelCheckBox;

    // New record to pass back all settings
    public record SaveResult(Map<Double, FibLevelProperties> levels, Map<Timeframe, Boolean> visibility, boolean showPriceLabel) {}

    public FibonacciSettingsDialog(Window owner, String title, String toolClassName, Map<Double, FibLevelProperties> initialLevels, Map<Timeframe, Boolean> initialVisibility, boolean initialShowLabel, Consumer<SaveResult> onSaveCallback) {
        super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        this.toolClassName = toolClassName;
        this.initialLevels = new TreeMap<>(initialLevels); // Use TreeMap to sort levels by key
        this.onSaveCallback = onSaveCallback;

        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Style", createStylePanel(initialShowLabel));
        tabbedPane.addTab("Levels", createLevelsPanel());

        if (initialVisibility != null) {
            this.visibilityPanel = new VisibilityPanel(initialVisibility, null);
            visibilityPanel.setBorder(BorderFactory.createEmptyBorder(10,5,10,5));
            tabbedPane.addTab("Visibility", this.visibilityPanel);
        }

        add(tabbedPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(350, 400));
        setLocationRelativeTo(owner);
    }

    private JPanel createStylePanel(boolean initialShowLabel) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        showLabelCheckBox = new JCheckBox("Show Labels on Price Axis", initialShowLabel);
        panel.add(showLabelCheckBox);
        return panel;
    }

    private JScrollPane createLevelsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        for (Map.Entry<Double, FibLevelProperties> entry : this.initialLevels.entrySet()) {
            Double level = entry.getKey();
            FibLevelProperties props = entry.getValue();

            JCheckBox enabledCheckbox = new JCheckBox("", props.enabled());
            JButton colorButton = createColorButton(props.color());
            colorButton.setEnabled(props.enabled());

            enabledCheckbox.addActionListener(e -> colorButton.setEnabled(enabledCheckbox.isSelected()));
            
            enabledCheckBoxes.put(level, enabledCheckbox);
            colorButtons.put(level, colorButton);

            gbc.gridx = 0;
            gbc.gridy = row;
            panel.add(enabledCheckbox, gbc);

            gbc.gridx = 1;
            panel.add(new JLabel(String.format("%.3f", level)), gbc);

            gbc.gridx = 2;
            panel.add(colorButton, gbc);
            row++;
        }
        
        gbc.gridx = 3; gbc.weightx = 1.0;
        panel.add(new JLabel(""), gbc); // Glue

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Levels"));
        return scrollPane;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        rightButtons.add(okButton);
        rightButtons.add(cancelButton);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton saveAsDefaultButton = new JButton("Save as Default");
        leftButtons.add(saveAsDefaultButton);

        panel.add(leftButtons, BorderLayout.WEST);
        panel.add(rightButtons, BorderLayout.EAST);

        saveAsDefaultButton.addActionListener(e -> onSaveAsDefault());

        okButton.addActionListener(e -> {
            Map<Double, FibLevelProperties> newLevels = gatherLevelsFromDialog();
            Map<Timeframe, Boolean> newVisibility = (visibilityPanel != null) ? visibilityPanel.getVisibilityMap() : null;
            boolean showLabel = showLabelCheckBox.isSelected();

            onSaveCallback.accept(new SaveResult(newLevels, newVisibility, showLabel));
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());

        return panel;
    }
    
    private void onSaveAsDefault() {
        SettingsService sm = SettingsService.getInstance();
        DrawingToolTemplate activeTemplate = sm.getActiveTemplateForTool(toolClassName);
        if (activeTemplate == null) return;

        Map<Double, FibLevelProperties> newLevels = gatherLevelsFromDialog();
        boolean showLabel = showLabelCheckBox.isSelected();

        Map<String, Object> newSpecificProps = new HashMap<>(activeTemplate.specificProps());
        newSpecificProps.put("levels", newLevels);

        DrawingToolTemplate updatedTemplate = new DrawingToolTemplate(
            activeTemplate.id(),
            activeTemplate.name(),
            activeTemplate.color(), // color is not edited here
            activeTemplate.stroke(), // stroke is not edited here
            showLabel, // update this property
            newSpecificProps // update the specific props
        );

        sm.updateTemplate(toolClassName, updatedTemplate);

        JOptionPane.showMessageDialog(this, "Active template '" + activeTemplate.name() + "' updated for " + getTitle().replace(" Settings", "") + ".", "Defaults Saved", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private Map<Double, FibLevelProperties> gatherLevelsFromDialog() {
        Map<Double, FibLevelProperties> newLevels = new TreeMap<>();
        for (Double level : initialLevels.keySet()) {
            boolean enabled = enabledCheckBoxes.get(level).isSelected();
            Color color = colorButtons.get(level).getBackground();
            newLevels.put(level, new FibLevelProperties(enabled, color));
        }
        return newLevels;
    }

    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton();
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(80, 22));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        button.addActionListener(e -> {
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), button::setBackground);
            JPopupMenu popup = new JPopupMenu();
            popup.add(colorPanel);
            popup.show(button, 0, button.getHeight());
        });
        return button;
    }
}