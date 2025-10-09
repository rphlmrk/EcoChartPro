package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.DailyStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.MonthlyStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.WeeklyStats;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class JournalViewPanel extends JPanel {
    private enum ViewMode { MONTH, YEAR, DECADE }
    private static final int YEARS_PER_DECADE_VIEW = 12;

    private final JournalAnalysisService analysisService;
    private List<Trade> allTrades = Collections.emptyList();
    private Map<LocalDate, DailyStats> dailyAnalysisResults;
    private Map<YearMonth, MonthlyStats> monthlyAnalysisResults;
    private Map<Integer, WeeklyStats> weeklyAnalysisResults;

    private final CardLayout mainCardLayout;
    private final JPanel mainCardPanel;

    // --- Main Calendar View components ---
    private final CardLayout calendarViewLayout;
    private final JPanel calendarViewHolder;
    private final JPanel monthViewGrid, yearViewGrid, decadeViewGrid;
    private final JPanel weeklyRecapPanel;
    private final JLabel headerLabel;
    private final WeeklySummaryPanel monthlySummaryPanel;
    private DayCellPanel.ViewMode currentDayCellViewMode = DayCellPanel.ViewMode.PNL;
    private YearMonth currentYearMonth;
    private ViewMode currentViewMode = ViewMode.MONTH;

    public JournalViewPanel() {
        this.analysisService = new JournalAnalysisService();
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 25, 20, 25));

        this.mainCardLayout = new CardLayout();
        this.mainCardPanel = new JPanel(mainCardLayout);
        mainCardPanel.setOpaque(false);

        this.headerLabel = new JLabel("", SwingConstants.CENTER);
        this.monthlySummaryPanel = new WeeklySummaryPanel();
        this.weeklyRecapPanel = new JPanel(new GridLayout(6, 1, 0, 5));

        this.calendarViewLayout = new CardLayout();
        this.calendarViewHolder = new JPanel(calendarViewLayout);
        this.monthViewGrid = new JPanel(new GridLayout(6, 7, 5, 5));
        this.yearViewGrid = new JPanel(new GridLayout(4, 3, 10, 10));
        this.decadeViewGrid = new JPanel(new GridLayout(4, 3, 10, 10));

        mainCardPanel.add(createCustomCalendarView(), "CALENDAR_VIEW");
        // Placeholder for the weekly view, which will be created on demand
        mainCardPanel.add(new JPanel(), "WEEKLY_DETAIL_VIEW");
        add(mainCardPanel, BorderLayout.CENTER);

        setView("CALENDAR_VIEW");
    }

    public void loadSessionData(List<Trade> trades) {
        this.allTrades = (trades != null) ? trades : Collections.emptyList();
        this.dailyAnalysisResults = analysisService.analyzeTradesByDay(this.allTrades);
        this.monthlyAnalysisResults = analysisService.analyzePerformanceByMonth(this.allTrades);
        this.weeklyAnalysisResults = analysisService.analyzeTradesByWeek(this.allTrades);

        JournalAnalysisService.DateRange range = analysisService.getDateRange(this.allTrades).orElse(null);
        this.currentYearMonth = (range != null) ? YearMonth.from(range.maxDate()) : YearMonth.now();
        
        currentViewMode = ViewMode.MONTH;
        updateView();
        setView("CALENDAR_VIEW");
    }

    private void updateView() {
        switch (currentViewMode) {
            case MONTH:
                rebuildMonthView();
                calendarViewLayout.show(calendarViewHolder, ViewMode.MONTH.name());
                break;
            case YEAR:
                rebuildYearView();
                calendarViewLayout.show(calendarViewHolder, ViewMode.YEAR.name());
                break;
            case DECADE:
                rebuildDecadeView();
                calendarViewLayout.show(calendarViewHolder, ViewMode.DECADE.name());
                break;
        }
    }

    private void setView(String viewName) {
        mainCardLayout.show(mainCardPanel, viewName);
    }
    
    private void handleDateSelection(LocalDate selectedDate) {
        if (dailyAnalysisResults != null && dailyAnalysisResults.containsKey(selectedDate)) {
            // Create and switch to the new weekly detail view on demand
            WeeklyDetailViewPanel detailView = new WeeklyDetailViewPanel(allTrades, dailyAnalysisResults, selectedDate, this::setView);
            mainCardPanel.add(detailView, "WEEKLY_DETAIL_VIEW");
            setView("WEEKLY_DETAIL_VIEW");
        }
    }

    private JPanel createCustomCalendarView() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        panel.add(createCalendarHeaderPanel(), BorderLayout.NORTH);

        JPanel monthViewPanel = new JPanel(new BorderLayout(10, 0));
        monthViewPanel.setOpaque(false);
        monthViewGrid.setOpaque(false);
        weeklyRecapPanel.setOpaque(false);
        monthViewPanel.add(createDayOfWeekHeader(), BorderLayout.NORTH);
        monthViewPanel.add(monthViewGrid, BorderLayout.CENTER);
        monthViewPanel.add(weeklyRecapPanel, BorderLayout.EAST);
        
        calendarViewHolder.setOpaque(false);
        calendarViewHolder.add(monthViewPanel, ViewMode.MONTH.name());
        yearViewGrid.setOpaque(false);
        calendarViewHolder.add(yearViewGrid, ViewMode.YEAR.name());
        decadeViewGrid.setOpaque(false);
        calendarViewHolder.add(decadeViewGrid, ViewMode.DECADE.name());

        panel.add(calendarViewHolder, BorderLayout.CENTER);
        panel.add(monthlySummaryPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createCalendarHeaderPanel() {
        JPanel navigationPanel = new JPanel(new BorderLayout());
        navigationPanel.setOpaque(false);

        JButton prevButton = createNavButton(UITheme.Icons.ARROW_LEFT);
        prevButton.addActionListener(e -> navigate(-1));
        JButton nextButton = createNavButton(UITheme.Icons.ARROW_RIGHT);
        nextButton.addActionListener(e -> navigate(1));

        headerLabel.setFont(UIManager.getFont("app.font.heading"));
        headerLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        headerLabel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (currentViewMode == ViewMode.MONTH) currentViewMode = ViewMode.YEAR;
                else if (currentViewMode == ViewMode.YEAR) currentViewMode = ViewMode.DECADE;
                updateView();
            }
        });
        navigationPanel.add(prevButton, BorderLayout.WEST);
        navigationPanel.add(headerLabel, BorderLayout.CENTER);
        navigationPanel.add(nextButton, BorderLayout.EAST);

        JToggleButton pnlButton = new JToggleButton("P&L View", true);
        JToggleButton planButton = new JToggleButton("Plan View");
        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(pnlButton);
        viewGroup.add(planButton);
        pnlButton.addActionListener(e -> setDayCellViewMode(DayCellPanel.ViewMode.PNL));
        planButton.addActionListener(e -> setDayCellViewMode(DayCellPanel.ViewMode.PLAN));
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        togglePanel.setOpaque(false);
        togglePanel.add(pnlButton);
        togglePanel.add(planButton);

        JPanel fullHeader = new JPanel();
        fullHeader.setOpaque(false);
        fullHeader.setLayout(new BoxLayout(fullHeader, BoxLayout.Y_AXIS));
        fullHeader.add(navigationPanel);
        fullHeader.add(togglePanel);

        return fullHeader;
    }

    private void navigate(int direction) {
        switch (currentViewMode) {
            case MONTH: currentYearMonth = currentYearMonth.plusMonths(direction); break;
            case YEAR: currentYearMonth = currentYearMonth.plusYears(direction); break;
            case DECADE: currentYearMonth = currentYearMonth.plusYears((long) direction * YEARS_PER_DECADE_VIEW); break;
        }
        updateView();
    }
    
    private void rebuildMonthView() {
        if (currentYearMonth == null) return;

        monthViewGrid.removeAll();
        weeklyRecapPanel.removeAll();
        headerLabel.setText(currentYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        LocalDate startOfCalendar = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        WeekFields weekFields = WeekFields.of(Locale.US);

        for (int w = 0; w < 6; w++) {
            LocalDate firstDayOfWeekInRow = startOfCalendar.plusWeeks(w);
            int weekNumber = firstDayOfWeekInRow.get(weekFields.weekOfWeekBasedYear());

            for (int d = 0; d < 7; d++) {
                LocalDate date = firstDayOfWeekInRow.plusDays(d);
                DayCellPanel dayCell = new DayCellPanel(date.getDayOfMonth());

                if (date.getMonth() == currentYearMonth.getMonth()) {
                    DailyStats stats = (dailyAnalysisResults != null) ? dailyAnalysisResults.get(date) : null;
                    dayCell.updateData(stats, currentDayCellViewMode);
                    if (stats != null) {
                        dayCell.setCursor(new Cursor(Cursor.HAND_CURSOR));
                        dayCell.addMouseListener(new MouseAdapter() {
                            @Override public void mouseClicked(MouseEvent e) { handleDateSelection(date); }
                        });
                    }
                } else {
                    dayCell.updateData(null, currentDayCellViewMode);
                    dayCell.setEnabled(false);
                }
                monthViewGrid.add(dayCell);
            }
            WeeklyStats weekStats = weeklyAnalysisResults != null ? weeklyAnalysisResults.get(weekNumber) : null;
            weeklyRecapPanel.add(new WeeklyRecapLabel(weekStats));
        }

        monthViewGrid.revalidate();
        monthViewGrid.repaint();
        weeklyRecapPanel.revalidate();
        weeklyRecapPanel.repaint();
        updateMonthlySummary();
    }

    private void rebuildYearView() {
        yearViewGrid.removeAll();
        headerLabel.setText(String.valueOf(currentYearMonth.getYear()));

        for (int i = 1; i <= 12; i++) {
            final YearMonth month = YearMonth.of(currentYearMonth.getYear(), i);
            IndicatorButton monthButton = new IndicatorButton(month.getMonth().getDisplayName(TextStyle.FULL, Locale.US));
            boolean hasData = monthlyAnalysisResults != null && monthlyAnalysisResults.containsKey(month);
            monthButton.setHasData(hasData);
            monthButton.addActionListener(e -> {
                currentYearMonth = month;
                currentViewMode = ViewMode.MONTH;
                updateView();
            });
            yearViewGrid.add(monthButton);
        }
        yearViewGrid.revalidate();
        yearViewGrid.repaint();
    }

    private void rebuildDecadeView() {
        decadeViewGrid.removeAll();
        int currentYear = currentYearMonth.getYear();
        int yearBlockStart = currentYear - (currentYear % YEARS_PER_DECADE_VIEW);
        headerLabel.setText(String.format("%d - %d", yearBlockStart, yearBlockStart + YEARS_PER_DECADE_VIEW - 1));

        for (int i = 0; i < YEARS_PER_DECADE_VIEW; i++) {
            final int year = yearBlockStart + i;
            IndicatorButton yearButton = new IndicatorButton(String.valueOf(year));
            boolean hasData = monthlyAnalysisResults != null && monthlyAnalysisResults.keySet().stream().anyMatch(ym -> ym.getYear() == year);
            yearButton.setHasData(hasData);
            yearButton.addActionListener(e -> {
                currentYearMonth = YearMonth.of(year, currentYearMonth.getMonthValue());
                currentViewMode = ViewMode.YEAR;
                updateView();
            });
            decadeViewGrid.add(yearButton);
        }
        decadeViewGrid.revalidate();
        decadeViewGrid.repaint();
    }

    private void setDayCellViewMode(DayCellPanel.ViewMode mode) {
        if (this.currentDayCellViewMode != mode) {
            this.currentDayCellViewMode = mode;
            rebuildMonthView();
        }
    }

    private void updateMonthlySummary() {
        if (monthlyAnalysisResults != null && monthlyAnalysisResults.containsKey(currentYearMonth)) {
            monthlySummaryPanel.updateData(monthlyAnalysisResults.get(currentYearMonth));
        } else {
            monthlySummaryPanel.clearData();
        }
    }
    
    private JPanel createDayOfWeekHeader() {
        JPanel panel = new JPanel(new GridLayout(1, 8, 5, 5));
        panel.setOpaque(false);
        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        for (String day : days) {
            panel.add(createHeaderLabel(day));
        }
        panel.add(createHeaderLabel("WEEK"));
        return panel;
    }

    private JLabel createHeaderLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }

    private JButton createNavButton(String iconPath) {
        JButton button = new JButton(UITheme.getIcon(iconPath, 16, 16, UIManager.getColor("Label.disabledForeground")));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static class IndicatorButton extends JButton {
        private boolean hasData = false;

        IndicatorButton(String text) {
            super(text);
            setFont(UIManager.getFont("app.font.subheading"));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        void setHasData(boolean hasData) {
            this.hasData = hasData;
            setEnabled(hasData);
            setForeground(hasData ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground"));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hasData) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIManager.getColor("app.color.accent"));
                g2d.fillOval(getWidth() / 2 - 2, getHeight() - 12, 5, 5);
                g2d.dispose();
            }
        }
    }

    private static class WeeklyRecapLabel extends JLabel {
        private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0%");

        WeeklyRecapLabel(WeeklyStats stats) {
            setVerticalAlignment(TOP);
            setForeground(UIManager.getColor("Label.disabledForeground"));
            setFont(UIManager.getFont("app.font.widget_content").deriveFont(11f));
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            if (stats == null) {
                setText("");
            } else {
                String text = String.format("<html><body style='width: 100px;'>"
                    + "%d trades<br>"
                    + "P&L: %s<br>"
                    + "Plan Adherence: %s<br>"
                    + "Win Rate: %s"
                    + "</body></html>",
                    stats.tradeCount(),
                    PNL_FORMAT.format(stats.totalPnl()),
                    PERCENT_FORMAT.format(stats.planFollowedPercentage()),
                    PERCENT_FORMAT.format(stats.winRatio())
                );
                setText(text);
            }
        }
    }
}