package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.TextObject;
import com.EcoChartPro.model.drawing.TextProperties;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;
import com.EcoChartPro.ui.components.FontChooserPanel;
import com.EcoChartPro.ui.components.VisibilityPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class TextSettingsDialog extends JDialog {

    // --- Components for Text Tab ---
    private JTextArea textArea;
    private JButton textColorButton;
    private JLabel fontDisplayLabel;
    private JButton changeFontButton;
    private JCheckBox showBackgroundCheckBox;
    private JButton backgroundColorButton;
    private JCheckBox showBorderCheckBox;
    private JButton borderColorButton;
    private JCheckBox wrapTextCheckBox;

    // --- Components for Visibility Tab ---
    private final VisibilityPanel visibilityPanel;

    // --- State Holders ---
    private Font selectedFont;
    private TextObject updatedTextObject;
    private final boolean isScreenAnchored;

    public TextSettingsDialog(Frame owner, TextObject existingObject) {
        super(owner, "Text Settings", true);
        setSize(420, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));


        // --- Initialize with existing values or defaults ---
        Map<Timeframe, Boolean> initialVisibility;
        if (existingObject != null) {
            this.selectedFont = existingObject.font();
            TextProperties props = existingObject.properties();
            this.isScreenAnchored = props.screenAnchored();
            initialVisibility = existingObject.visibility();

            // Init values for components
            textArea = new JTextArea(existingObject.text());
            textColorButton = new JButton();
            textColorButton.setBackground(existingObject.color());
            fontDisplayLabel = new JLabel();
            changeFontButton = new JButton("Font...");
            showBackgroundCheckBox = new JCheckBox("Background", props.showBackground());
            backgroundColorButton = new JButton();
            backgroundColorButton.setBackground(props.backgroundColor());
            showBorderCheckBox = new JCheckBox("Border", props.showBorder());
            borderColorButton = new JButton();
            borderColorButton.setBackground(props.borderColor());
            wrapTextCheckBox = new JCheckBox("Text wrap", props.wrapText());

        } else { // Defaults for a new object
            SettingsManager sm = SettingsManager.getInstance();
            TextProperties defaultProps = sm.getToolDefaultTextProperties("TextObject", new TextProperties(false, new Color(33, 150, 243, 80), false, new Color(33, 150, 243), true, false));
            selectedFont = sm.getToolDefaultFont("TextObject", new Font("SansSerif", Font.PLAIN, 14));
            isScreenAnchored = false; // This is determined by the tool, not the settings dialog
            initialVisibility = createDefaultVisibility();
            
            textArea = new JTextArea("Your text here...");
            textColorButton = new JButton();
            textColorButton.setBackground(sm.getToolDefaultColor("TextObject", Color.WHITE));
            fontDisplayLabel = new JLabel();
            changeFontButton = new JButton("Font...");
            showBackgroundCheckBox = new JCheckBox("Background", defaultProps.showBackground());
            backgroundColorButton = new JButton();
            backgroundColorButton.setBackground(defaultProps.backgroundColor());
            showBorderCheckBox = new JCheckBox("Border", defaultProps.showBorder());
            borderColorButton = new JButton();
            borderColorButton.setBackground(defaultProps.borderColor());
            wrapTextCheckBox = new JCheckBox("Text wrap", defaultProps.wrapText());
        }

        updateFontDisplayLabel();

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- Create and add Text Tab ---
        JPanel textStylePanel = createTextStylePanel();
        tabbedPane.addTab("Text", textStylePanel);

        // --- Create and add Visibility Tab ---
        visibilityPanel = new VisibilityPanel(initialVisibility, null);
        visibilityPanel.setBorder(BorderFactory.createEmptyBorder(10,5,10,5));
        tabbedPane.addTab("Visibility", visibilityPanel);

        // --- Bottom panel for OK/Cancel buttons ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        rightButtons.add(okButton);
        rightButtons.add(cancelButton);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton saveAsDefaultButton = new JButton("Save as Default");
        leftButtons.add(saveAsDefaultButton);

        bottomPanel.add(leftButtons, BorderLayout.WEST);
        bottomPanel.add(rightButtons, BorderLayout.EAST);


        // --- Add components to dialog ---
        add(tabbedPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.PAGE_END);

        // --- Action Listeners ---
        okButton.addActionListener(e -> onOk());
        cancelButton.addActionListener(e -> dispose());
        changeFontButton.addActionListener(e -> openFontChooser());
        saveAsDefaultButton.addActionListener(e -> onSaveAsDefault());
    }

    private void onSaveAsDefault() {
        SettingsManager sm = SettingsManager.getInstance();

        // Save Color
        sm.setToolDefaultColor("TextObject", textColorButton.getBackground());

        // Save Font
        sm.setToolDefaultFont("TextObject", this.selectedFont);

        // Save Text Properties (excluding screen-anchored state)
        TextProperties defaultProps = sm.getToolDefaultTextProperties("TextObject", null);
        boolean currentScreenAnchored = (defaultProps != null) && defaultProps.screenAnchored();

        TextProperties newProps = new TextProperties(
            showBackgroundCheckBox.isSelected(),
            backgroundColorButton.getBackground(),
            showBorderCheckBox.isSelected(),
            borderColorButton.getBackground(),
            wrapTextCheckBox.isSelected(),
            currentScreenAnchored // Preserve the existing screen-anchored default
        );
        sm.setToolDefaultTextProperties("TextObject", newProps);

        JOptionPane.showMessageDialog(this, "Default settings for Text tool saved.", "Defaults Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openFontChooser() {
        FontChooserPanel fontChooserPanel = new FontChooserPanel(this.selectedFont);
        JDialog fontDialog = new JDialog(this, "Choose Font", true);
        fontDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        fontDialog.setLayout(new BorderLayout());
        fontDialog.add(fontChooserPanel, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);
        fontDialog.add(buttonPanel, BorderLayout.SOUTH);

        okButton.addActionListener(e -> {
            this.selectedFont = fontChooserPanel.getSelectedFont();
            updateFontDisplayLabel();
            fontDialog.dispose();
        });
        cancelButton.addActionListener(e -> fontDialog.dispose());

        fontDialog.pack();
        fontDialog.setLocationRelativeTo(this);
        fontDialog.setVisible(true);
    }

    private void updateFontDisplayLabel() {
        if (selectedFont == null) return;
        String styleStr;
        if (selectedFont.isBold() && selectedFont.isItalic()) styleStr = "Bold Italic";
        else if (selectedFont.isBold()) styleStr = "Bold";
        else if (selectedFont.isItalic()) styleStr = "Italic";
        else styleStr = "Plain";
        fontDisplayLabel.setText(String.format("%s, %s, %d", selectedFont.getFamily(), styleStr, selectedFont.getSize()));
    }

    private JPanel createTextStylePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 5));

        // --- Top Toolbar for Font and Color ---
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        textColorButton.setToolTipText("Text Color");
        textColorButton.setPreferredSize(new Dimension(24, 24));
        textColorButton.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        textColorButton.addActionListener(e -> chooseColor(textColorButton));
        toolbar.add(textColorButton);

        toolbar.add(fontDisplayLabel);
        toolbar.add(changeFontButton);

        // --- Center Text Area ---
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane textScrollPane = new JScrollPane(textArea);
        textScrollPane.setBorder(new TitledBorder(""));

        // --- Bottom Options Panel ---
        JPanel bottomOptions = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Background
        gbc.gridx = 0; gbc.gridy = 0;
        bottomOptions.add(showBackgroundCheckBox, gbc);
        gbc.gridx = 1;
        configureColorButton(backgroundColorButton, "Background Color", showBackgroundCheckBox);
        bottomOptions.add(backgroundColorButton, gbc);

        // Border
        gbc.gridx = 0; gbc.gridy = 1;
        bottomOptions.add(showBorderCheckBox, gbc);
        gbc.gridx = 1;
        configureColorButton(borderColorButton, "Border Color", showBorderCheckBox);
        bottomOptions.add(borderColorButton, gbc);

        // Wrap Text
        gbc.gridx = 0; gbc.gridy = 2;
        bottomOptions.add(wrapTextCheckBox, gbc);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(textScrollPane, BorderLayout.CENTER);
        panel.add(bottomOptions, BorderLayout.SOUTH);

        return panel;
    }
    
    private void configureColorButton(JButton button, String toolTip, JCheckBox bağlıCheckBox) {
        button.setToolTipText(toolTip);
        button.setPreferredSize(new Dimension(50, 24));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        button.setEnabled(bağlıCheckBox.isSelected());
        button.addActionListener(e -> chooseColor(button));
        bağlıCheckBox.addActionListener(e -> button.setEnabled(bağlıCheckBox.isSelected()));
    }

    private void onOk() {
        // --- Gather Font Info ---
        Font finalFont = this.selectedFont;

        // --- Gather Properties ---
        TextProperties finalProperties = new TextProperties(
            showBackgroundCheckBox.isSelected(),
            backgroundColorButton.getBackground(),
            showBorderCheckBox.isSelected(),
            borderColorButton.getBackground(),
            wrapTextCheckBox.isSelected(),
            this.isScreenAnchored
        );

        // --- Gather Visibility ---
        Map<Timeframe, Boolean> finalVisibility = visibilityPanel.getVisibilityMap();

        // --- Create Final Object ---
        updatedTextObject = new TextObject(
            null,
            null,
            textArea.getText(),
            finalFont,
            textColorButton.getBackground(),
            finalProperties,
            finalVisibility
        );
        dispose();
    }

    /**
     * Replaces the JColorChooser with our custom component in a popup.
     * @param button The button whose color will be changed.
     */
    private void chooseColor(JButton button) {
        Consumer<Color> onColorUpdate = button::setBackground;

        CustomColorChooserPanel colorPanel = new CustomColorChooserPanel(button.getBackground(), onColorUpdate);
        
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        popupMenu.add(colorPanel);
        popupMenu.show(button, 0, button.getHeight());
    }

    private Map<Timeframe, Boolean> createDefaultVisibility() {
        Map<Timeframe, Boolean> map = new EnumMap<>(Timeframe.class);
        for (Timeframe tf : Timeframe.values()) {
            map.put(tf, true);
        }
        return map;
    }

    public TextObject getUpdatedTextObject() {
        return updatedTextObject;
    }
}