package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class SidebarCalendarPanel extends JPanel {

    private enum ViewMode { MONTH, YEAR, DECADE }

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int YEARS_PER_VIEW = 12;

    private ViewMode currentViewMode = ViewMode.MONTH;
    private LocalDate selectedDate;

    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JLabel headerLabel;
    private final JPanel monthViewContainer;
    private final JPanel yearViewGrid;
    private final JPanel decadeViewGrid;

    private Map<LocalDate, JournalAnalysisService.DailyStats> dailyStatsMap = Collections.emptyMap();

    public SidebarCalendarPanel() {
        this.selectedDate = LocalDate.now();
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        Dimension calendarSize = new Dimension(280, 250);
        setPreferredSize(calendarSize);
        setMaximumSize(calendarSize);

        headerLabel = createHeaderLabel();
        add(createHeaderPanel(headerLabel), BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        monthViewContainer = createMonthViewPanel();
        yearViewGrid = createGridView(4, 3);
        decadeViewGrid = createGridView(4, 3);

        cardPanel.add(monthViewContainer, ViewMode.MONTH.name());
        cardPanel.add(yearViewGrid, ViewMode.YEAR.name());
        cardPanel.add(decadeViewGrid, ViewMode.DECADE.name());
        add(cardPanel, BorderLayout.CENTER);

        updateView();
    }

    public void setDailyStats(Map<LocalDate, JournalAnalysisService.DailyStats> stats) {
        this.dailyStatsMap = (stats != null) ? stats : Collections.emptyMap();
        updateView();
    }

    private JPanel createHeaderPanel(JLabel titleLabel) {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JButton prevButton = createNavButton("<");
        prevButton.addActionListener(e -> navigate(-1));
        JButton nextButton = createNavButton(">");
        nextButton.addActionListener(e -> navigate(1));
        headerPanel.add(prevButton, BorderLayout.WEST);
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(nextButton, BorderLayout.EAST);
        return headerPanel;
    }

    private JLabel createHeaderLabel() {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setFont(UIManager.getFont("app.font.widget_title").deriveFont(16f));
        label.setForeground(UIManager.getColor("Label.foreground"));
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentViewMode == ViewMode.MONTH) {
                    currentViewMode = ViewMode.YEAR;
                } else if (currentViewMode == ViewMode.YEAR) {
                    currentViewMode = ViewMode.DECADE;
                }
                updateView();
            }
        });
        return label;
    }

    private JPanel createMonthViewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        JPanel dayNamesPanel = new JPanel(new GridLayout(1, 7));
        dayNamesPanel.setOpaque(false);
        DayOfWeek[] daysOfWeek = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
        for (DayOfWeek day : daysOfWeek) {
            JLabel dayLabel = new JLabel(day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), SwingConstants.CENTER);
            dayLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
            dayLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            dayNamesPanel.add(dayLabel);
        }
        panel.add(dayNamesPanel, BorderLayout.NORTH);
        JPanel grid = createGridView(0, 7);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createGridView(int rows, int cols) {
        JPanel panel = new JPanel(new GridLayout(rows, cols, 5, 5));
        panel.setOpaque(false);
        return panel;
    }

    private void navigate(int amount) {
        switch (currentViewMode) {
            case MONTH:
                selectedDate = selectedDate.plusMonths(amount);
                break;
            case YEAR:
                selectedDate = selectedDate.plusYears(amount);
                break;
            case DECADE:
                selectedDate = selectedDate.plusYears((long) amount * YEARS_PER_VIEW);
                break;
        }
        updateView();
    }

    private void updateView() {
        updateHeaderLabel();
        switch (currentViewMode) {
            case MONTH:
                rebuildMonthView();
                cardLayout.show(cardPanel, ViewMode.MONTH.name());
                break;
            case YEAR:
                rebuildYearView();
                cardLayout.show(cardPanel, ViewMode.YEAR.name());
                break;
            case DECADE:
                rebuildDecadeView();
                cardLayout.show(cardPanel, ViewMode.DECADE.name());
                break;
        }
    }

    private void updateHeaderLabel() {
        switch (currentViewMode) {
            case MONTH:
                headerLabel.setText(selectedDate.format(MONTH_YEAR_FORMATTER));
                break;
            case YEAR:
                headerLabel.setText(String.valueOf(selectedDate.getYear()));
                break;
            case DECADE:
                int year = selectedDate.getYear();
                int startYear = year - (year % YEARS_PER_VIEW);
                headerLabel.setText(startYear + " - " + (startYear + 11));
                break;
        }
    }

    private void rebuildMonthView() {
        JPanel grid = (JPanel) ((BorderLayout) monthViewContainer.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        grid.removeAll();
        YearMonth yearMonth = YearMonth.from(selectedDate);
        LocalDate firstOfMonth = selectedDate.withDayOfMonth(1);
        int firstDayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < firstDayOfWeek; i++) {
            grid.add(new JLabel(""));
        }
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = selectedDate.withDayOfMonth(day);
            DayButton dayButton = new DayButton(String.valueOf(day), dailyStatsMap.get(date));
            dayButton.addActionListener(e -> {
                LocalDate oldDate = this.selectedDate;
                this.selectedDate = date;
                firePropertyChange("date", oldDate, this.selectedDate);
                rebuildMonthView(); // Just repaint this view
            });
            if (date.equals(selectedDate)) {
                dayButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
            }
            grid.add(dayButton);
        }
        grid.revalidate();
        grid.repaint();
    }

    private void rebuildYearView() {
        yearViewGrid.removeAll();
        for (Month month : Month.values()) {
            JButton monthButton = createDayButton(month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            monthButton.addActionListener(e -> {
                this.selectedDate = this.selectedDate.withMonth(month.getValue());
                this.currentViewMode = ViewMode.MONTH;
                updateView();
            });
            if (month.equals(selectedDate.getMonth())) {
                monthButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
            }
            yearViewGrid.add(monthButton);
        }
        yearViewGrid.revalidate();
        yearViewGrid.repaint();
    }

    private void rebuildDecadeView() {
        decadeViewGrid.removeAll();
        int currentYear = selectedDate.getYear();
        int startYear = currentYear - (currentYear % YEARS_PER_VIEW);
        for (int i = 0; i < 12; i++) {
            int year = startYear + i;
            JButton yearButton = createDayButton(String.valueOf(year));
            yearButton.addActionListener(e -> {
                this.selectedDate = this.selectedDate.withYear(year);
                this.currentViewMode = ViewMode.YEAR; // This is the fix
                updateView();
            });
            if (year == currentYear) {
                yearButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
            }
            decadeViewGrid.add(yearButton);
        }
        decadeViewGrid.revalidate();
        decadeViewGrid.repaint();
    }

    private JButton createNavButton(String text) {
        JButton button = new JButton(text);
        button.setFont(UIManager.getFont("app.font.widget_title").deriveFont(18f));
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createDayButton(String text) {
        JButton button = new JButton(text);
        button.setFont(UIManager.getFont("app.font.widget_content"));
        button.setForeground(UIManager.getColor("Label.foreground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
    
    private static class DayButton extends JButton {
        private final JournalAnalysisService.DailyStats stats;

        DayButton(String text, JournalAnalysisService.DailyStats stats) {
            super(text);
            this.stats = stats;
            setFont(UIManager.getFont("app.font.widget_content"));
            setForeground(UIManager.getColor("Label.foreground"));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (stats != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color dotColor = stats.totalPnl().compareTo(BigDecimal.ZERO) >= 0
                        ? UIManager.getColor("app.color.positive")
                        : UIManager.getColor("app.color.negative");
                g2d.setColor(dotColor);

                int dotSize = 4;
                int x = (getWidth() - dotSize) / 2;
                int y = getHeight() - dotSize - 2;
                g2d.fillOval(x, y, dotSize, dotSize);
                g2d.dispose();
            }
        }
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate newDate) {
        this.selectedDate = newDate;
        updateView();
    }
}