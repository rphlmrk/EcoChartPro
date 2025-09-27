package com.EcoChartPro.ui.sidebar;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.sidebar.checklists.ChecklistsViewPanel;
import com.EcoChartPro.ui.sidebar.journal.SidebarJournalModePanel;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TradingSidebarPanel extends JPanel implements PropertyChangeListener {

    private static final int SIDEBAR_EXPANDED_WIDTH = 280;
    private static final int SIDEBAR_COLLAPSED_WIDTH = 40;

    private final CardLayout cardLayout;
    private final JPanel contentPanel;
    private final SidebarNavPanel navPanel;
    private final SidebarHeaderPanel headerPanel;
    private final JPanel mainArea;

    private final PositionsViewPanel positionsView;
    private final SidebarJournalModePanel journalModeView;
    private final ChecklistsViewPanel checklistsView;
    private ChartPanel activeChartPanel;

    private boolean isCollapsed = false;

    public TradingSidebarPanel() {
        super(new BorderLayout());
        setPreferredSize(new Dimension(SIDEBAR_EXPANDED_WIDTH, 0));
        setBackground(UIManager.getColor("Panel.background"));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Component.borderColor")));

        headerPanel = new SidebarHeaderPanel(e -> toggleSidebar());
        navPanel = new SidebarNavPanel();

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setOpaque(false);

        this.positionsView = new PositionsViewPanel();
        this.journalModeView = new SidebarJournalModePanel();
        this.checklistsView = new ChecklistsViewPanel();

        contentPanel.add(this.positionsView, "VIEW_POSITIONS");
        contentPanel.add(journalModeView, "VIEW_JOURNAL");
        contentPanel.add(this.checklistsView, "VIEW_CHECKLISTS");

        mainArea = new JPanel(new BorderLayout());
        mainArea.setOpaque(false);
        mainArea.add(navPanel, BorderLayout.NORTH);
        mainArea.add(contentPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);
        add(mainArea, BorderLayout.CENTER);

        navPanel.addActionListener(e -> cardLayout.show(contentPanel, e.getActionCommand()));
        journalModeView.addJumpToTradeListener(evt -> this.firePropertyChange(evt.getPropertyName(), null, evt.getNewValue()));
        PaperTradingService.getInstance().addPropertyChangeListener(this);
    }

    public void addJumpToTradeListener(PropertyChangeListener listener) {
        // This ensures the event from child components is correctly bubbled up.
        this.addPropertyChangeListener("jumpToTrade", listener);
    }

    public void setActiveChartPanel(ChartPanel activeChartPanel) {
        this.activeChartPanel = activeChartPanel;
        if (this.positionsView != null) {
            this.positionsView.setActiveChartPanel(activeChartPanel);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        SwingUtilities.invokeLater(() -> {
            switch (evt.getPropertyName()) {
                case "openPositionsUpdated":
                    List<Position> updatedPositions = (List<Position>) evt.getNewValue();
                    positionsView.updatePositions(updatedPositions, activeChartPanel);
                    if (activeChartPanel != null) {
                        activeChartPanel.repaint();
                    }
                    break;
                // Handle pending order updates
                case "pendingOrdersUpdated":
                    List<Order> updatedOrders = (List<Order>) evt.getNewValue();
                    positionsView.updateOrders(updatedOrders);
                    if (activeChartPanel != null) {
                        activeChartPanel.repaint();
                    }
                    break;
                case "tradeHistoryUpdated":
                    List<Trade> updatedTrades = (List<Trade>) evt.getNewValue();
                    journalModeView.updateJournalData(updatedTrades);
                    break;
                case "unrealizedPnlCalculated":
                    @SuppressWarnings("unchecked")
                    Map<UUID, BigDecimal> pnlMap = (Map<UUID, BigDecimal>) evt.getNewValue();
                    positionsView.updateLivePnl(pnlMap);
                    break;
            }
        });
    }

    private void toggleSidebar() {
        boolean oldState = isCollapsed;
        isCollapsed = !isCollapsed;
        headerPanel.setCollapsed(isCollapsed);
        mainArea.setVisible(!isCollapsed);
        int newWidth = isCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;
        setPreferredSize(new Dimension(newWidth, 0));
        revalidate();
        repaint();
        firePropertyChange("sidebarToggled", oldState, isCollapsed);
    }

    public void cleanup() {
        PaperTradingService.getInstance().removePropertyChangeListener(this);
        System.out.println("TradingSidebarPanel cleaned up and listener removed.");
    }
}