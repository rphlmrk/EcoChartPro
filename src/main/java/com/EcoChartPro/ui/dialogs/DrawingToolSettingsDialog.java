package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.VisibilityPanel;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A generic settings dialog for common drawing object properties like color,
 * stroke, and visibility.
 */
public class DrawingToolSettingsDialog extends JDialog {

    private final DrawingObject initialDrawing;
    private final DrawingManager drawingManager;

    // --- Components ---
    private JButton colorButton;
    private JSpinner thicknessSpinner;
    private VisibilityPanel visibilityPanel;
    private JCheckBox showLabelCheckBox; 

    public DrawingToolSettingsDialog(Frame owner, DrawingObject drawing, DrawingManager drawingManager) {
        super(owner, "Drawing Settings", true);
        this.initialDrawing = drawing;
        this.drawingManager = drawingManager;

        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Style", createStylePanel());
        tabbedPane.addTab("Visibility", createVisibilityPanel());

        add(tabbedPane, BorderLayout.CENTER);
        add(createButtonPanel(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createStylePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Color
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Color:"), gbc);
        gbc.gridx = 1;
        colorButton = createColorButton(initialDrawing.color());
        panel.add(colorButton, gbc);

        // Thickness
        gbc.gridy = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Thickness:"), gbc);
        gbc.gridx = 1;
        SpinnerNumberModel thicknessModel = new SpinnerNumberModel((int) initialDrawing.stroke().getLineWidth(), 1, 10, 1);
        thicknessSpinner = new JSpinner(thicknessModel);
        panel.add(thicknessSpinner, gbc);

        // Show Label Checkbox
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        showLabelCheckBox = new JCheckBox("Show Label on Price Axis");
        showLabelCheckBox.setSelected(initialDrawing.showPriceLabel());
        panel.add(showLabelCheckBox, gbc);
        
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        panel.add(new JLabel(""), gbc); // Glue

        return panel;
    }

    private JPanel createVisibilityPanel() {
        visibilityPanel = new VisibilityPanel(initialDrawing.visibility(), null);
        visibilityPanel.setBorder(BorderFactory.createEmptyBorder(10,5,10,5));
        return visibilityPanel;
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

        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        saveAsDefaultButton.addActionListener(e -> onSaveAsDefault());

        return panel;
    }

    private void onOk() {
        Color newColor = colorButton.getBackground();
        BasicStroke newStroke = new BasicStroke((Integer) thicknessSpinner.getValue());
        Map<Timeframe, Boolean> newVisibility = visibilityPanel.getVisibilityMap();
        boolean showLabel = showLabelCheckBox.isSelected(); // MODIFICATION

        DrawingObject updatedDrawing = initialDrawing.withColor(newColor)
                                                     .withStroke(newStroke)
                                                     .withVisibility(newVisibility)
                                                     .withShowPriceLabel(showLabel);

        drawingManager.updateDrawing(updatedDrawing);
        dispose();
    }
    
    private void onSaveAsDefault() {
        String toolName = initialDrawing.getClass().getSimpleName();
        SettingsManager sm = SettingsManager.getInstance();
        sm.setToolDefaultColor(toolName, colorButton.getBackground());
        sm.setToolDefaultStroke(toolName, new BasicStroke((Integer) thicknessSpinner.getValue()));
        sm.setToolDefaultShowPriceLabel(toolName, showLabelCheckBox.isSelected());
        
        JOptionPane.showMessageDialog(this,
            "Current style saved as default for " + toolName.replace("Object", "") + ".",
            "Default Saved",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    private JButton createColorButton(Color initialColor) {
        JButton button = new JButton();
        button.setBackground(initialColor);
        button.setPreferredSize(new Dimension(100, 25));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        button.addActionListener(e -> {
            Consumer<Color> onColorUpdate = button::setBackground;
            CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), onColorUpdate);
            JPopupMenu popupMenu = new JPopupMenu();
            popupMenu.add(colorPanel);
            popupMenu.show(button, 0, button.getHeight());
        });
        return button;
    }
}