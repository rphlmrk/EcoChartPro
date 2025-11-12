package com.EcoChartPro.ui.sidebar;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.trading.ModifyPositionDialog;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class PositionRowPanel extends JPanel {
    private final Position position;
    private final JLabel pnlLabel;
    private final JLabel entryPriceLabel;
    private final JLabel pnlPercentageLabel;
    private final JLabel sizeLabel;
    
    private final ChartPanel chartPanel;
    private final WorkspaceContext context;

    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("+#0.00%;-#0.00%");


    public PositionRowPanel(Position position, ChartPanel chartPanel, WorkspaceContext context) {
        this.position = position;
        this.chartPanel = chartPanel;
        this.context = context;
        
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        
        // --- ROW 1 ---
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 2, 4);

        // Column 0: Symbol
        gbc.gridx = 0;
        JLabel symbolLabel = new JLabel(position.symbol().name());
        symbolLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(14f));
        symbolLabel.setForeground(UIManager.getColor("Label.foreground"));
        add(symbolLabel, gbc);

        // Column 1: Entry Price
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 8, 2, 4); // Add left padding
        entryPriceLabel = new JLabel(position.entryPrice().setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        entryPriceLabel.setForeground(UIManager.getColor("Label.foreground"));
        entryPriceLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        add(entryPriceLabel, gbc);
        
        // Column 2: Spacer (this pushes everything apart)
        gbc.gridx = 2;
        gbc.weightx = 1.0; // This is the expanding component
        add(Box.createHorizontalGlue(), gbc);
        gbc.weightx = 0; // Reset for other components

        // Column 3: P&L ($) Label
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 2, 4); // Reset padding
        add(createGrayLabel("P&L ($):"), gbc);

        // Column 4: P&L ($) Value
        gbc.gridx = 4;
        pnlLabel = new JLabel(PNL_FORMAT.format(0));
        pnlLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        pnlLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        add(pnlLabel, gbc);

        // Column 5: Modify Button
        gbc.gridx = 5;
        gbc.anchor = GridBagConstraints.EAST;
        JButton modifyButton = createActionButton("M");
        modifyButton.setToolTipText("Modify Stop Loss / Take Profit");
        modifyButton.addActionListener(e -> handleModify());
        add(modifyButton, gbc);


        // --- ROW 2 ---
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 4);

        // Column 0: Direction
        gbc.gridx = 0;
        JLabel directionLabel = new JLabel(position.direction().toString());
        directionLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        directionLabel.setForeground(position.direction() == TradeDirection.LONG ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        add(directionLabel, gbc);
        
        // Column 1: Size
        gbc.gridx = 1;
        gbc.insets = new Insets(0, 8, 0, 4); // Add left padding
        sizeLabel = new JLabel(position.size().setScale(5, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        sizeLabel.setForeground(UIManager.getColor("Label.foreground"));
        sizeLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        add(sizeLabel, gbc);

        // Column 2: Spacer (must be in both rows to maintain alignment)
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        add(Box.createHorizontalGlue(), gbc);
        gbc.weightx = 0;

        // Column 3: P&L (%) Label
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 4); // Reset padding
        add(createGrayLabel("P&L (%):"), gbc);

        // Column 4: P&L (%) Value
        gbc.gridx = 4;
        pnlPercentageLabel = new JLabel(PERCENT_FORMAT.format(0));
        pnlPercentageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        pnlPercentageLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        add(pnlPercentageLabel, gbc);
        
        // Column 5: Close Button
        gbc.gridx = 5;
        gbc.anchor = GridBagConstraints.EAST;
        JButton closeButton = createActionButton("X");
        closeButton.setToolTipText("Close Position at Market");
        closeButton.addActionListener(e -> handleClose());
        add(closeButton, gbc);
    }

    private JLabel createGrayLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(10f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }

    private JButton createActionButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 5, 0, 5));
        return button;
    }

    private void handleClose() {
        if (chartPanel == null) {
            JOptionPane.showMessageDialog(this, "No active chart to determine market price.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        KLine currentBar = chartPanel.getDataModel().getCurrentReplayKLine();
        if (currentBar == null) {
            JOptionPane.showMessageDialog(this, "Cannot close position, replay data not available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int choice = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to close this position at the market price?",
            "Confirm Close",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (choice == JOptionPane.YES_OPTION) {
            context.getPaperTradingService().closePosition(position.id(), currentBar);
        }
    }

    private void handleModify() {
        if (chartPanel == null) {
            JOptionPane.showMessageDialog(this, "No active chart context to open dialog.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ModifyPositionDialog dialog = new ModifyPositionDialog(owner, chartPanel, position, context);
        dialog.setVisible(true);
    }
    
    public Position getPosition() {
        return position;
    }
    
    public void updatePnl(BigDecimal pnl) {
        pnlLabel.setText(PNL_FORMAT.format(pnl));
        Color pnlColor = pnl.compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative");
        pnlLabel.setForeground(pnlColor);

        BigDecimal initialValue = position.entryPrice().multiply(position.size());
        if (initialValue.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal pnlRatio = pnl.divide(initialValue, 4, RoundingMode.HALF_UP);
            pnlPercentageLabel.setText(PERCENT_FORMAT.format(pnlRatio));
            pnlPercentageLabel.setForeground(pnlColor);
        } else {
            pnlPercentageLabel.setText("N/A");
            pnlPercentageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        }
    }
}