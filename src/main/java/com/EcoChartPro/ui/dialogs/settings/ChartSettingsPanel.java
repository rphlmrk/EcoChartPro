package com.EcoChartPro.ui.dialogs.settings;

import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.ui.components.CustomColorChooserPanel;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Consumer;

public class ChartSettingsPanel extends JPanel {
    private final SettingsManager sm;

    private final JComboBox<ChartType> chartTypeComboBox;
    private final JButton bullColorButton;
    private final JButton bearColorButton;
    private final JButton backgroundColorButton;
    private final JButton gridColorButton;
    private final JButton axisTextColorButton;
    private final JButton crosshairColorButton;
    private final JButton crosshairLabelBgButton;
    private final JButton crosshairLabelFgButton;
    private final JButton livePriceBullTextButton;
    private final JButton livePriceBearTextButton;
    private final JSpinner livePriceFontSizeSpinner;
    private final JCheckBox daySeparatorsCheckBox;
    private final JSpinner daySeparatorTimeSpinner;
    private final JButton daySeparatorColorButton; // [NEW]
    private final JComboBox<SettingsManager.CrosshairFPS> crosshairFpsComboBox;
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
        axisTextColorButton = createColorButton(sm.getAxisTextColor());
        crosshairColorButton = createColorButton(sm.getCrosshairColor());
        crosshairLabelBgButton = createColorButton(sm.getCrosshairLabelBackgroundColor());
        crosshairLabelFgButton = createColorButton(sm.getCrosshairLabelForegroundColor());
        livePriceBullTextButton = createColorButton(sm.getLivePriceLabelBullTextColor());
        livePriceBearTextButton = createColorButton(sm.getLivePriceLabelBearTextColor());
        livePriceFontSizeSpinner = new JSpinner(new SpinnerNumberModel(sm.getLivePriceLabelFontSize(), 8, 24, 1));

        daySeparatorsCheckBox = new JCheckBox("Show Day Separators");
        daySeparatorsCheckBox.setSelected(sm.isDaySeparatorsEnabled());

        SpinnerDateModel timeModel = new SpinnerDateModel();
        timeModel.setValue(Date.from(sm.getDaySeparatorStartTime().atDate(java.time.LocalDate.EPOCH).toInstant(ZoneOffset.UTC)));
        daySeparatorTimeSpinner = new JSpinner(timeModel);
        daySeparatorTimeSpinner.setEditor(new JSpinner.DateEditor(daySeparatorTimeSpinner, "HH:mm"));
        
        daySeparatorColorButton = createColorButton(sm.getDaySeparatorColor()); // [NEW]

        crosshairFpsComboBox = new JComboBox<>(SettingsManager.CrosshairFPS.values());
        crosshairFpsComboBox.setSelectedItem(sm.getCrosshairFps());
        
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

        gbc.gridy++; add(createSeparator("Chart Colors"), gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Bullish Candle:"), gbc);
        gbc.gridx++; add(bullColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Bearish Candle:"), gbc);
        gbc.gridx++; add(bearColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Chart Background:"), gbc);
        gbc.gridx++; add(backgroundColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Grid Lines:"), gbc);
        gbc.gridx++; add(gridColorButton, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Axis Text & Labels:"), gbc);
        gbc.gridx++; add(axisTextColorButton, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Crosshair Line:"), gbc);
        gbc.gridx++; add(crosshairColorButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Crosshair Label Background:"), gbc);
        gbc.gridx++; add(crosshairLabelBgButton, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Crosshair Label Text:"), gbc);
        gbc.gridx++; add(crosshairLabelFgButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Live Price Text (Bullish):"), gbc);
        gbc.gridx++; add(livePriceBullTextButton, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Live Price Text (Bearish):"), gbc);
        gbc.gridx++; add(livePriceBearTextButton, gbc);

        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Live Price Font Size:"), gbc);
        gbc.gridx++; add(livePriceFontSizeSpinner, gbc);
        
        gbc.gridy++; add(createSeparator("Chart Elements"), gbc);

        // [MODIFIED] Day Separator layout with color picker
        gbc.gridx = 0; gbc.gridy++; add(daySeparatorsCheckBox, gbc);
        gbc.gridx = 1; 
        JPanel daySeparatorPanel = new JPanel(new GridBagLayout());
        GridBagConstraints dspGbc = new GridBagConstraints();
        dspGbc.insets = new Insets(0, 0, 0, 5);
        daySeparatorPanel.add(new JLabel("Day Start (UTC):"), dspGbc);
        dspGbc.gridx++; daySeparatorPanel.add(daySeparatorTimeSpinner, dspGbc);
        dspGbc.gridx++; daySeparatorPanel.add(daySeparatorColorButton, dspGbc);
        add(daySeparatorPanel, gbc);
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Crosshair Refresh Rate:"), gbc);
        gbc.gridx++; add(crosshairFpsComboBox, gbc);

        gbc.gridy++; add(createSeparator("Price Axis Labels"), gbc);
        
        gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2; add(showPriceLabelsCheckbox, gbc); gbc.gridwidth = 1;
        
        gbc.gridx = 0; gbc.gridy++; add(new JLabel("Show on Axis:"), gbc);
        gbc.gridx++; 
        JPanel showPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        showPanel.add(showOrdersOnAxisCheckbox);
        showPanel.add(showDrawingsOnAxisCheckbox);
        add(showPanel, gbc);

        gbc.gridy++; gbc.weighty = 1.0; add(new JPanel(), gbc);
    }
    
    public void applyChanges() {
        sm.setCurrentChartType((ChartType) chartTypeComboBox.getSelectedItem());
        sm.setBullColor(bullColorButton.getBackground());
        sm.setBearColor(bearColorButton.getBackground());
        sm.setChartBackground(backgroundColorButton.getBackground());
        sm.setGridColor(gridColorButton.getBackground());
        sm.setAxisTextColor(axisTextColorButton.getBackground());
        sm.setCrosshairColor(crosshairColorButton.getBackground());
        sm.setCrosshairLabelBackgroundColor(crosshairLabelBgButton.getBackground());
        sm.setCrosshairLabelForegroundColor(crosshairLabelFgButton.getBackground());
        sm.setLivePriceLabelBullTextColor(livePriceBullTextButton.getBackground());
        sm.setLivePriceLabelBearTextColor(livePriceBearTextButton.getBackground());
        sm.setLivePriceLabelFontSize((Integer)livePriceFontSizeSpinner.getValue());
        sm.setDaySeparatorsEnabled(daySeparatorsCheckBox.isSelected());
        Date separatorDate = (Date) daySeparatorTimeSpinner.getValue();
        sm.setDaySeparatorStartTime(separatorDate.toInstant().atZone(ZoneOffset.UTC).toLocalTime());
        sm.setDaySeparatorColor(daySeparatorColorButton.getBackground()); // [NEW]
        sm.setCrosshairFps((SettingsManager.CrosshairFPS) crosshairFpsComboBox.getSelectedItem());
        sm.setPriceAxisLabelsEnabled(showPriceLabelsCheckbox.isSelected());
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
        gbc.gridwidth = 2; // Span both columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, gbc);
        
        gbc.gridy = 1;
        gbc.insets = new Insets(0,0,5,0);
        panel.add(separator, gbc);
        
        return panel;
    }

    private GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return gbc;
    }
}