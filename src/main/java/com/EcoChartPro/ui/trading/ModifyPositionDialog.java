package com.EcoChartPro.ui.trading;

import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.ChartPanel;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.RoundingMode;

/**
 * A modal dialog that wraps the ModifyPositionPanel to allow users to modify an open position.
 */
public class ModifyPositionDialog extends JDialog implements PropertyChangeListener {

    private final ChartPanel chartPanel;
    private final ModifyPositionPanel modifyPanel;
    // private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#.########");

    public ModifyPositionDialog(Frame owner, ChartPanel chartPanel, Position position) {
        super(owner, true); // Modal, title is now handled by our custom header
        this.chartPanel = chartPanel;

        setUndecorated(true);

        // Main container with a border to define the dialog's edge
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        // Custom header panel
        String symbolString = position.symbol().name();
        String titleText = "Modify Position - " + symbolString;
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel);


        this.modifyPanel = new ModifyPositionPanel(position, chartPanel, (success) -> {
            // This callback is triggered by both "Modify Position" and "Cancel" buttons.
            dispose();
        });
        
        this.modifyPanel.addPropertyChangeListener(this);

        JButton modifyButton = modifyPanel.getModifyPositionButton();
        modifyButton.setBackground(UIManager.getColor("app.color.neutral"));
        modifyButton.setForeground(Color.WHITE);
        modifyButton.setFocusPainted(false);

        // Assemble the dialog with the custom header
        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(modifyPanel, BorderLayout.CENTER);
        setContentPane(rootPanel);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
    
    /**
     * Overriding dispose to ensure the chart state is always cleaned up.
     * This is called when the dialog is closed via the panel's callback.
     */
    @Override
    public void dispose() {
        chartPanel.exitPriceSelectionMode(); // Explicitly exit the chart's mode
        chartPanel.setOrderPreview(null);      // Clear any lingering preview lines
        super.dispose();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();

        if ("priceSelectionRequested".equals(propName)) {
            String targetFieldName = (String) evt.getNewValue();
            if (targetFieldName == null) return;
            
            setVisible(false);

            SwingUtilities.invokeLater(() -> {
                chartPanel.enterPriceSelectionMode(selectedPrice -> {
                    if (selectedPrice == null) {
                        setVisible(true); // Just make the dialog visible again
                        return;
                    }
                    String formattedPrice = selectedPrice.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                    
                    switch (targetFieldName) {
                        case "stopLoss":
                            modifyPanel.getStopLossField().setText(formattedPrice);
                            break;
                        case "takeProfit":
                            modifyPanel.getTakeProfitField().setText(formattedPrice);
                            break;
                    }
                    
                    setVisible(true);
                });
            });
        } else if ("previewUpdated".equals(propName)) {
            chartPanel.setOrderPreview((OrderPreview) evt.getNewValue());
        }
    }
}