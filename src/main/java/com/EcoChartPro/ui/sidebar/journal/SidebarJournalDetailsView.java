package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.controller.WorkspaceContext;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * This panel is the dedicated scrollable area for the journal's content,
 * containing the daily summary, trade list, and any other details.
 * It is placed within a JScrollPane in the CENTER of the main journal mode panel.
 */
public class SidebarJournalDetailsView extends JPanel {

    private final DailySummaryView dailySummaryView;
    private final TradeListView tradeListView;

    public SidebarJournalDetailsView(WorkspaceContext context) {
        setLayout(new BorderLayout());
        setOpaque(false);

        dailySummaryView = new DailySummaryView();
        tradeListView = new TradeListView(context);

        add(dailySummaryView, BorderLayout.NORTH);
        add(tradeListView, BorderLayout.CENTER);
    }

    public DailySummaryView getDailySummaryView() {
        return dailySummaryView;
    }

    public TradeListView getTradeListView() {
        return tradeListView;
    }

    public void addJumpToTradeListener(PropertyChangeListener listener) {
        tradeListView.addJumpToTradeListener(listener);
    }
}