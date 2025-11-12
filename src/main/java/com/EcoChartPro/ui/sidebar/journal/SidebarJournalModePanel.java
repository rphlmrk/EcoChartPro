package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.controller.WorkspaceContext;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.Trade;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class SidebarJournalModePanel extends JPanel {

    private final CardLayout calendarCardLayout;
    private final CardLayoutWrapperPanel calendarContainerPanel;
    private final SidebarCalendarPanel fullCalendarView;
    private final SidebarWeeklyViewPanel compactWeeklyView;
    private final JScrollPane detailsScrollPane;
    
    // TotalSummaryView
    private final DailySummaryView dailySummaryView;
    private final TradeListView tradeListView;
    private final SidebarJournalDetailsView journalDetailsView;

    private final JournalAnalysisService analysisService;
    private List<Trade> allTrades;
    private Map<LocalDate, JournalAnalysisService.DailyStats> dailyStats;

    private String currentCalendarView = "FULL_CALENDAR";
    private int lastScrollValue = 0;
    private Timer debounceTimer;
    private static final int DEBOUNCE_DELAY = 250;
    private boolean isScrollingDown = false;
    private boolean compactViewLocked = false;
    private static final int MIN_SCROLL_THRESHOLD = 5;


    public SidebarJournalModePanel(WorkspaceContext context) {
        super(new BorderLayout());
        setOpaque(false);
        this.analysisService = new JournalAnalysisService();

        calendarContainerPanel = new CardLayoutWrapperPanel();
        calendarCardLayout = (CardLayout) calendarContainerPanel.getLayout();
        fullCalendarView = new SidebarCalendarPanel();
        compactWeeklyView = new SidebarWeeklyViewPanel();
        compactWeeklyView.setMaximumSize(new Dimension(300, Short.MAX_VALUE));
        calendarContainerPanel.add(fullCalendarView, "FULL_CALENDAR");
        calendarContainerPanel.add(compactWeeklyView, "COMPACT_CALENDAR");

        journalDetailsView = new SidebarJournalDetailsView(context);
        this.dailySummaryView = journalDetailsView.getDailySummaryView();
        this.tradeListView = journalDetailsView.getTradeListView();
        
        detailsScrollPane = new JScrollPane(journalDetailsView);
        detailsScrollPane.setBorder(null);
        detailsScrollPane.setOpaque(false);
        detailsScrollPane.getViewport().setOpaque(false);
        detailsScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(calendarContainerPanel, BorderLayout.NORTH);
        add(detailsScrollPane, BorderLayout.CENTER);

        detailsScrollPane.getVerticalScrollBar().getModel().addChangeListener(e -> {
            int scrollValue = detailsScrollPane.getVerticalScrollBar().getValue();
            String targetView = (scrollValue > 0) ? "COMPACT_CALENDAR" : "FULL_CALENDAR";

            int scrollDelta = scrollValue - lastScrollValue;
            isScrollingDown = scrollDelta > MIN_SCROLL_THRESHOLD;

            if (scrollDelta > MIN_SCROLL_THRESHOLD) {
                compactViewLocked = true;
            } else if (scrollDelta < -MIN_SCROLL_THRESHOLD) {
                compactViewLocked = false;
            }
            
            if (debounceTimer != null) {
                debounceTimer.cancel();
            }
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        if (!targetView.equals(currentCalendarView)) {
                            if (targetView.equals("COMPACT_CALENDAR") ||
                                (targetView.equals("FULL_CALENDAR") && !compactViewLocked)) {
                                currentCalendarView = targetView;
                                calendarCardLayout.show(calendarContainerPanel, currentCalendarView);
                                if ("COMPACT_CALENDAR".equals(currentCalendarView)) {
                                    LocalDate selectedDate = fullCalendarView.getSelectedDate();
                                    compactWeeklyView.updateView(selectedDate, false);
                                } else if ("FULL_CALENDAR".equals(currentCalendarView)) {
                                    fullCalendarView.setSelectedDate(compactWeeklyView.getSelectedDate());
                                }
                                calendarContainerPanel.revalidate();
                                calendarContainerPanel.repaint();
                                SidebarJournalModePanel.this.revalidate();
                            }
                        }
                    });
                }
            }, DEBOUNCE_DELAY);

            lastScrollValue = scrollValue;
        });
        
        fullCalendarView.addPropertyChangeListener("date", evt -> refreshDetailsForSelectedDate());
        compactWeeklyView.addPropertyChangeListener("date", evt -> refreshDetailsForSelectedDate());
    }
    
    public void addJumpToTradeListener(PropertyChangeListener listener) {
        journalDetailsView.addJumpToTradeListener(listener);
    }

    public void updateJournalData(List<Trade> trades) {
        this.allTrades = trades;
        if (trades == null || trades.isEmpty()) {
            this.dailyStats = Collections.emptyMap();
        } else {
            this.dailyStats = analysisService.analyzeTradesByDay(trades);
        }
        
        fullCalendarView.setDailyStats(this.dailyStats);
        compactWeeklyView.setDailyStats(this.dailyStats);
        refreshDetailsForSelectedDate();
    }

    private void refreshDetailsForSelectedDate() {
        if (allTrades == null) {
            dailySummaryView.updateView(null, LocalDate.now());
            tradeListView.updateView(Collections.emptyList());
            return;
        }

        LocalDate selectedDate = "FULL_CALENDAR".equals(currentCalendarView) ? fullCalendarView.getSelectedDate() : compactWeeklyView.getSelectedDate();
        JournalAnalysisService.DailyStats statsForDay = dailyStats.get(selectedDate);
        List<Trade> tradesForDay = allTrades.stream()
                .filter(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate().equals(selectedDate))
                .collect(Collectors.toList());

        dailySummaryView.updateView(statsForDay, selectedDate);
        tradeListView.updateView(tradesForDay);
    }
}