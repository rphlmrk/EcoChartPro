package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.journal.JournalAnalysisService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class SidebarCalendarPanel extends JPanel {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    private LocalDate selectedDate;
    private boolean isMonthView = true;

    private final CardLayout cardLayout;
    private final JPanel cardPanel;
    private final JLabel headerLabel;
    private final JPanel monthViewGrid;
    private final JPanel yearViewGrid;
    
    // Store daily stats to render indicators
    private Map<LocalDate, JournalAnalysisService.DailyStats> dailyStatsMap = Collections.emptyMap();

    public SidebarCalendarPanel() {
        this.selectedDate = LocalDate.now();
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        // Adjust size to fit within the new 280px sidebar
        Dimension calendarSize = new Dimension(280, 250);
        setPreferredSize(calendarSize);
        setMaximumSize(calendarSize);

        headerLabel = createHeaderLabel();
        add(createHeaderPanel(headerLabel), BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        monthViewGrid = createMonthViewPanel();
        yearViewGrid = createYearViewPanel();

        cardPanel.add(monthViewGrid, "MONTH_VIEW");
        cardPanel.add(yearViewGrid, "YEAR_VIEW");
        add(cardPanel, BorderLayout.CENTER);

        updateViews();
    }
    
    // method to receive stats from the parent panel.
    public void setDailyStats(Map<LocalDate, JournalAnalysisService.DailyStats> stats) {
        this.dailyStatsMap = (stats != null) ? stats : Collections.emptyMap();
        updateMonthView(); // Trigger a repaint of the days
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
                isMonthView = !isMonthView;
                cardLayout.show(cardPanel, isMonthView ? "MONTH_VIEW" : "YEAR_VIEW");
                updateHeaderLabel();
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
        JPanel grid = new JPanel(new GridLayout(0, 7, 5, 5));
        grid.setOpaque(false);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createYearViewPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 3, 10, 10));
        panel.setOpaque(false);
        return panel;
    }

    private void navigate(int amount) {
        if (isMonthView) {
            selectedDate = selectedDate.plusMonths(amount);
        } else {
            selectedDate = selectedDate.plusYears(amount * 12L);
        }
        updateViews();
    }

    private void updateViews() {
        updateHeaderLabel();
        updateMonthView();
        updateYearView();
    }

    private void updateHeaderLabel() {
        if (isMonthView) {
            headerLabel.setText(selectedDate.format(MONTH_YEAR_FORMATTER));
        } else {
            int year = selectedDate.getYear();
            int startYear = year - (year % 12);
            headerLabel.setText(startYear + " - " + (startYear + 11));
        }
    }

    private void updateMonthView() {
        JPanel grid = (JPanel) ((BorderLayout) monthViewGrid.getLayout()).getLayoutComponent(BorderLayout.CENTER);
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
                updateMonthView();
            });
            if (date.equals(selectedDate)) {
                dayButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
                dayButton.setForeground(UIManager.getColor("Label.foreground"));
            }
            grid.add(dayButton);
        }
        grid.revalidate();
        grid.repaint();
    }

    private void updateYearView() {
        yearViewGrid.removeAll();
        int currentYear = selectedDate.getYear();
        int startYear = currentYear - (currentYear % 12);
        for (int i = 0; i < 12; i++) {
            int year = startYear + i;
            JButton yearButton = createDayButton(String.valueOf(year));
            yearButton.addActionListener(e -> {
                this.selectedDate = this.selectedDate.withYear(year);
                this.isMonthView = true;
                cardLayout.show(cardPanel, "MONTH_VIEW");
                updateViews();
            });
            if (year == currentYear) {
                yearButton.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.focusedBorderColor"), 2));
            }
            yearViewGrid.add(yearButton);
        }
        yearViewGrid.revalidate();
        yearViewGrid.repaint();
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
    
    // inner class for custom painting of the indicator dot.
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
        updateViews();
    }
}