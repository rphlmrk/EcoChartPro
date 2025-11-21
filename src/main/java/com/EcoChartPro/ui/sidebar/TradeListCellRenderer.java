package com.EcoChartPro.ui.sidebar;

import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.home.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A custom ListCellRenderer that can display three types of items in the history list:
 * 1. A date header (when given a LocalDate object).
 * 2. A trade row (when given a Trade object).
 * 3. An empty/info message (when given a String).
 */
public class TradeListCellRenderer implements ListCellRenderer<Object> {

    // Formatters
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");

    // Reusable components for rendering
    private final JPanel rendererPanel;
    private final JPanel tradeRowPanel;
    private final JLabel dateHeaderLabel;
    private final JLabel emptyMessageLabel;
    private final JLabel directionLabel;
    private final JLabel symbolLabel;
    private final JLabel pnlLabel;
    private final JButton journalButton;

    public TradeListCellRenderer() {
        // --- Main container panel ---
        rendererPanel = new JPanel(new BorderLayout());
        rendererPanel.setOpaque(true); // Must be opaque to draw background color

        // --- Component for Date Headers ---
        dateHeaderLabel = new JLabel();
        dateHeaderLabel.setOpaque(false);
        dateHeaderLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD, 13f));
        dateHeaderLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0)); // More vertical padding

        // --- Component for Empty/Info Messages ---
        emptyMessageLabel = new JLabel();
        emptyMessageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyMessageLabel.setFont(UIManager.getFont("app.font.widget_content"));

        // --- Components and Layout for Trade Rows ---
        tradeRowPanel = new JPanel(new BorderLayout(10, 0)); // Use BorderLayout for the main row
        tradeRowPanel.setOpaque(false);
        // Add a bottom border to separate trade rows
        tradeRowPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));


        // --- Left side components (Icon + Symbol) ---
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.setOpaque(false);
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0)); // Vertical padding
        directionLabel = new JLabel();
        symbolLabel = new JLabel();
        symbolLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        leftPanel.add(directionLabel);
        leftPanel.add(symbolLabel);

        // --- Right side components (P&L + Journal Button) ---
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0)); // Vertical padding
        pnlLabel = new JLabel();
        pnlLabel.setFont(UIManager.getFont("app.font.widget_content"));
        journalButton = new JButton();
        journalButton.setOpaque(false);
        journalButton.setContentAreaFilled(false);
        journalButton.setBorderPainted(false);
        journalButton.setFocusPainted(false);
        journalButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        journalButton.setPreferredSize(new Dimension(24, 24));
        journalButton.setMargin(new Insets(0, 0, 0, 0));
        rightPanel.add(pnlLabel);
        rightPanel.add(journalButton);

        // Add the left and right panels to the main trade row panel
        tradeRowPanel.add(leftPanel, BorderLayout.WEST);
        tradeRowPanel.add(rightPanel, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        rendererPanel.removeAll();
        rendererPanel.setBackground(list.getBackground());

        if (value instanceof LocalDate) {
            configureForDateHeader((LocalDate) value, list);
        } else if (value instanceof Trade) {
            configureForTradeRow((Trade) value, list, isSelected);
        } else {
            configureForEmptyMessage(value.toString(), list);
        }

        return rendererPanel;
    }

    private void configureForDateHeader(LocalDate date, JList<?> list) {
        dateHeaderLabel.setText(date.format(DATE_FORMATTER));
        dateHeaderLabel.setForeground(UIManager.getColor("Label.foreground"));
        rendererPanel.add(dateHeaderLabel, BorderLayout.CENTER);
    }

    private void configureForTradeRow(Trade trade, JList<?> list, boolean isSelected) {
        // Use a wrapper for selection highlighting
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(isSelected); // Only opaque when selected
        if (isSelected) {
            wrapper.setBackground(UIManager.getColor("List.selectionBackground"));
        }
        wrapper.add(tradeRowPanel, BorderLayout.CENTER);
        rendererPanel.add(wrapper, BorderLayout.CENTER);


        // --- Configure Direction Icon & Symbol ---
        boolean isLong = trade.direction() == TradeDirection.LONG;
        String directionIconPath = isLong ? UITheme.Icons.TRADE_ARROW_UP : UITheme.Icons.TRADE_ARROW_DOWN;
        Color directionColor = isLong ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative");
        directionLabel.setIcon(UITheme.getIcon(directionIconPath, 14, 14, directionColor));
        symbolLabel.setText(trade.symbol().name().toUpperCase());
        symbolLabel.setForeground(UIManager.getColor("Label.foreground"));

        // --- Configure P&L ---
        BigDecimal pnl = trade.profitAndLoss();
        pnlLabel.setText(PNL_FORMAT.format(pnl));
        pnlLabel.setForeground(pnl.compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        
        // --- Configure Journal Button ---
        boolean hasNotes = trade.notes() != null && !trade.notes().trim().isEmpty();
        Color journalIconColor = hasNotes ? UIManager.getColor("app.color.neutral") : UIManager.getColor("Label.disabledForeground");
        journalButton.setIcon(UITheme.getIcon(UITheme.Icons.JOURNAL, 16, 16, journalIconColor));
        journalButton.setToolTipText(hasNotes ? "Edit journal entry" : "Add journal entry");
    }

    private void configureForEmptyMessage(String message, JList<?> list) {
        // The original implementation set a sticky preferred size on the shared renderer panel,
        // which caused all subsequent trade rows to have an incorrect, large height.
        // This new implementation uses a temporary wrapper panel to provide the desired
        // spacing for the empty message, without creating side effects for other cells.
        JPanel emptyWrapper = new JPanel(new GridBagLayout());
        emptyWrapper.setOpaque(false);
        emptyWrapper.setPreferredSize(new Dimension(100, 100));

        emptyMessageLabel.setText(message);
        emptyMessageLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        emptyWrapper.add(emptyMessageLabel);
        rendererPanel.add(emptyWrapper, BorderLayout.CENTER);
    }
}