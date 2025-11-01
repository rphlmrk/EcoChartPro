package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ChartSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JComboBox<ChartType> chartTypeComboBox;
    private final JButton bullColorButton;
    private final JButton bearColorButton;
    private final JButton backgroundColorButton;
    private final JButton gridColorButton;
    private final JCheckBox daySeparatorsCheckBox;
    private final JComboBox<SettingsManager.CrosshairFPS> crosshairFpsComboBox;
    private final JComboBox<SettingsManager.PriceAxisLabelPosition> priceAxisPositionComboBox;
    private final JCheckBox showPriceLabelsCheckbox;
    private final JCheckBox showOrdersOnAxisCheckbox;
    private final JCheckBox showDrawingsOnAxisCheckbox;

    public ChartSettingsPanel(SettingsManager settingsManager) {
        this.sm = settingsManager;
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize components
        chartTypeComboBox = new JComboBox<>(ChartType.values());
        chartTypeComboBox.setSelectedItem(sm.getCurrentChartType());

        bullColorButton = createColorButton(sm.getBullColor());
        bearColorButton = createColorButton(sm.getBearColor());
        backgroundColorButton = createColorButton(sm.getChartBackground());
        gridColorButton = createColorButton(sm.getGridColor());

        daySeparatorsCheckBox = new JCheckBox("Show Day Separators");
        daySeparatorsCheckBox.setSelected(sm.isDaySeparatorsEnabled());

        crosshairFpsComboBox = new JComboBox<>(SettingsManager.CrosshairFPS.values());
        crosshairFpsComboBox.setSelectedItem(sm.getCrosshairFps());
        
        priceAxisPositionComboBox = new JComboBox<>(SettingsManager.PriceAxisLabelPosition.values());
        priceAxisPositionComboBox.setSelectedItem(sm.getPriceAxisLabelPosition());
        
        showPriceLabelsCheckbox = new JCheckBox("Enable Price Axis Labels");
        showPriceLabelsCheckbox.setSelected(sm.isPriceAxisLabelsEnabled());
        
        showOrdersOnAxisCheckbox = new JCheckBox("Show Orders");
        showOrdersOnAxisCheckbox.setSelected(sm.isPriceAxisLabelsShowOrders());

        showDrawingsOnAxisCheckbox = new JCheckBox("Show Drawings");
        showDrawingsOnAxisCheckbox.setSelected(sm.isPriceAxisLabelsShowDrawings());

        initUI();
    }

    private void initUI() {
        GridBagConstraints gbc = createGbc(0, 0);

        add(new JLabel("Default Chart Type:"), gbc);
        gbc.gridx++; add(chartTypeComboBox, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Bull/Up Color:"), gbc);
        gbc.gridx++; add(bullColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Bear/Down Color:"), gbc);
        gbc.gridx++; add(bearColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Background Color:"), gbc);
        gbc.gridx++; add(backgroundColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Grid Color:"), gbc);
        gbc.gridx++; add(gridColorButton, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2; add(daySeparatorsCheckBox, gbc); gbc.gridwidth = 1;
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Crosshair Refresh Rate:"), gbc);
        gbc.gridx++; add(crosshairFpsComboBox, gbc);

        gbc.gridy++; add(createSeparator("Price Axis Labels"), gbc);
        
        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2; add(showPriceLabelsCheckbox, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Label Position:"), gbc);
        gbc.gridx++; add(priceAxisPositionComboBox, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Show on Axis:"), gbc);
        gbc.gridx++; 
        JPanel showPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        showPanel.add(showOrdersOnAxisCheckbox);
        showPanel.add(showDrawingsOnAxisCheckbox);
        add(showPanel, gbc);

        // Spacer
        gbc.gridy++; gbc.weighty = 1.0; add(new JPanel(), gbc);
    }
    
    public void applyChanges() {
        sm.setCurrentChartType((ChartType) chartTypeComboBox.getSelectedItem());
        sm.setBullColor(bullColorButton.getBackground());
        sm.setBearColor(bearColorButton.getBackground());
        sm.setChartBackground(backgroundColorButton.getBackground());
        sm.setGridColor(gridColorButton.getBackground());
        sm.setDaySeparatorsEnabled(daySeparatorsCheckBox.isSelected());
        sm.setCrosshairFps((SettingsManager.CrosshairFPS) crosshairFpsComboBox.getSelectedItem());
        sm.setPriceAxisLabelsEnabled(showPriceLabelsCheckbox.isSelected());
        sm.setPriceAxisLabelPosition((SettingsManager.PriceAxisLabelPosition) priceAxisPositionComboBox.getSelectedItem());
        sm.setPriceAxisLabelsShowOrders(showOrdersOnAxisCheckbox.isSelected());
        sm.setPriceAxisLabelsShowDrawings(showDrawingsOnAxisCheckbox.isSelected());
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

    private Component createSeparator(String text) {
        JSeparator separator = new JSeparator();
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(text), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(separator, gbc);
        return panel;
    }

    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }
}