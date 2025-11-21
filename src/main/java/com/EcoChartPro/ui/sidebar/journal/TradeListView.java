package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.home.theme.UITheme;
import com.EcoChartPro.ui.trading.JournalEntryDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * A panel for displaying the list of individual trades for a selected day.
 * Shows a "No trades" message if the list is empty.
 */
public class TradeListView extends JPanel {

    private final DefaultListModel<Trade> listModel;
    private final CardLayout cardLayout;
    private final JPanel contentWrapper;
    private PropertyChangeSupport pcs;
    private final WorkspaceContext context;

    public TradeListView(WorkspaceContext context) {
        super(new BorderLayout(0, 10));
        this.context = context;
        this.pcs = new PropertyChangeSupport(this);

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 15, 15, 15));

        JLabel tradesHeaderLabel = new JLabel("Trades");
        tradesHeaderLabel.setFont(UIManager.getFont("app.font.widget_title"));
        tradesHeaderLabel.setForeground(UIManager.getColor("Label.foreground"));
        tradesHeaderLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
            BorderFactory.createEmptyBorder(0, 0, 5, 0)
        ));
        add(tradesHeaderLabel, BorderLayout.NORTH);
        
        cardLayout = new CardLayout();
        contentWrapper = new JPanel(cardLayout);
        contentWrapper.setOpaque(false);

        // --- List View ---
        listModel = new DefaultListModel<>();
        JList<Trade> tradeList = new JList<>(listModel);
        tradeList.setOpaque(false);
        tradeList.setCellRenderer(new SimpleTradeListCellRenderer());
        
        tradeList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;

                int index = tradeList.locationToIndex(e.getPoint());
                if (index == -1) return;

                Trade trade = listModel.getElementAt(index);
                Rectangle cellBounds = tradeList.getCellBounds(index, index);
                if (cellBounds == null) return;
                
                // Heuristic for button positions (from right to left)
                int journalButtonStartX = cellBounds.x + cellBounds.width - 30;
                int jumpButtonStartX = journalButtonStartX - 30;

                // Check if click is on the Journal button
                if (e.getPoint().x > journalButtonStartX) {
                    Frame owner = (Frame) SwingUtilities.getWindowAncestor(TradeListView.this);
                    JournalEntryDialog dialog = new JournalEntryDialog(owner, trade, context);
                    dialog.setVisible(true);
                // Check if click is on the Jump button
                } else if (e.getPoint().x > jumpButtonStartX && e.getPoint().x < journalButtonStartX) {
                    pcs.firePropertyChange("jumpToTrade", null, trade);
                }
            }
        });


        // --- No Trades View ---
        JPanel noTradesPanel = new JPanel(new GridBagLayout());
        noTradesPanel.setOpaque(false);
        noTradesPanel.setPreferredSize(new Dimension(0, 50));
        JLabel noTradesLabel = new JLabel("No trades recorded for this day.");
        noTradesLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        noTradesPanel.add(noTradesLabel);

        contentWrapper.add(tradeList, "LIST");
        contentWrapper.add(noTradesPanel, "EMPTY");
        
        add(contentWrapper, BorderLayout.CENTER);
        setPreferredSize(new Dimension(0, 750));
    }

    public void updateView(List<Trade> dailyTrades) {
        listModel.clear();
        if (dailyTrades == null || dailyTrades.isEmpty()) {
            cardLayout.show(contentWrapper, "EMPTY");
        } else {
            listModel.addAll(dailyTrades);
            cardLayout.show(contentWrapper, "LIST");
        }
        revalidate();
        repaint();
    }
    
    public void addJumpToTradeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener("jumpToTrade", listener);
    }

    private static class SimpleTradeListCellRenderer extends JPanel implements ListCellRenderer<Trade> {
        private final JLabel directionLabel = new JLabel();
        private final JLabel pnlLabel = new JLabel();
        private final JButton journalButton = new JButton();
        private final JButton jumpButton = new JButton();
        private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$0.00;-$0.00");

        SimpleTradeListCellRenderer() {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            add(directionLabel, BorderLayout.WEST);

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            rightPanel.setOpaque(false);
            rightPanel.add(pnlLabel);
            
            jumpButton.setIcon(UITheme.getIcon(UITheme.Icons.JUMP_TO, 14, 14, UIManager.getColor("Label.disabledForeground")));
            jumpButton.setToolTipText("View on Chart");
            jumpButton.setOpaque(false);
            jumpButton.setContentAreaFilled(false);
            jumpButton.setBorderPainted(false);
            jumpButton.setFocusPainted(false);
            jumpButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            journalButton.setOpaque(false);
            journalButton.setContentAreaFilled(false);
            journalButton.setBorderPainted(false);
            journalButton.setFocusPainted(false);
            journalButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

            rightPanel.add(jumpButton);
            rightPanel.add(journalButton);
            add(rightPanel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Trade> list, Trade trade, int index, boolean isSelected, boolean cellHasFocus) {
            boolean isLong = trade.direction() == TradeDirection.LONG;
            directionLabel.setText(isLong ? "LONG" : "SHORT");
            directionLabel.setForeground(isLong ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));

            BigDecimal pnl = trade.profitAndLoss();
            pnlLabel.setText(PNL_FORMAT.format(pnl));
            pnlLabel.setForeground(pnl.compareTo(BigDecimal.ZERO) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
            
            boolean hasNotes = trade.notes() != null && !trade.notes().trim().isEmpty();
            Color journalIconColor = hasNotes ? UIManager.getColor("app.color.neutral") : UIManager.getColor("Label.disabledForeground");
            journalButton.setIcon(UITheme.getIcon(UITheme.Icons.JOURNAL, 14, 14, journalIconColor));
            journalButton.setToolTipText(hasNotes ? "Edit journal entry" : "Add journal entry");

            if (isSelected) {
                setBackground(UIManager.getColor("List.selectionBackground"));
                setOpaque(true);
            } else {
                setOpaque(false);
            }
            return this;
        }
    }
}