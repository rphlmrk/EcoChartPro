package com.EcoChartPro.ui.trading;

import com.EcoChartPro.core.controller.WorkspaceContext;
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

    public ModifyPositionDialog(Frame owner, ChartPanel chartPanel, Position position, WorkspaceContext context) {
        super(owner, true);
        this.chartPanel = chartPanel;

        setUndecorated(true);

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        String symbolString = position.symbol().name();
        String titleText = "Modify Position - " + symbolString;
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")));
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        headerPanel.add(titleLabel);


        this.modifyPanel = new ModifyPositionPanel(position, chartPanel, context, (success) -> {
            dispose();
        });
        
        this.modifyPanel.addPropertyChangeListener(this);

        JButton modifyButton = modifyPanel.getModifyPositionButton();
        modifyButton.setBackground(UIManager.getColor("app.color.neutral"));
        modifyButton.setForeground(Color.WHITE);
        modifyButton.setFocusPainted(false);

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(modifyPanel, BorderLayout.CENTER);
        setContentPane(rootPanel);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }
    
    @Override
    public void dispose() {
        chartPanel.exitPriceSelectionMode();
        chartPanel.setOrderPreview(null);
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
                        setVisible(true);
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