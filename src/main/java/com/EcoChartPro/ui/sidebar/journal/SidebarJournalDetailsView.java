package com.EcoChartPro.ui.sidebar.journal;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * This panel is the dedicated scrollable area for the journal's content,
 * containing the daily summary, trade list, and any other details.
 * It is placed within a JScrollPane in the CENTER of the main journal mode panel.
 */
public class SidebarJournalDetailsView extends JPanel {

    // References to the new, more specific view components.
    private final DailySummaryView dailySummaryView;
    private final TradeListView tradeListView;

    public SidebarJournalDetailsView() {
        // A BorderLayout respects the preferred size of its NORTH component.
        setLayout(new BorderLayout());
        setOpaque(false); // Let the parent's background show through.

        dailySummaryView = new DailySummaryView();
        tradeListView = new TradeListView();

        // By placing the summary in the NORTH, it gets its preferred height (250px).
        add(dailySummaryView, BorderLayout.NORTH);
        // The trade list in the CENTER will take up the remaining available space.
        add(tradeListView, BorderLayout.CENTER);
    }

    // Getter for the daily summary view
    public DailySummaryView getDailySummaryView() {
        return dailySummaryView;
    }

    // Getter for the trade list view
    public TradeListView getTradeListView() {
        return tradeListView;
    }

    public void addJumpToTradeListener(PropertyChangeListener listener) {
        tradeListView.addJumpToTradeListener(listener);
    }
}