package com.EcoChartPro.ui.components;

import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A custom panel that displays an interactive, multi-view calendar (month/year).
 * It allows navigation and notifies a listener upon date selection.
 */
public class CalendarPanel extends JPanel {

    private enum ViewMode { MONTH, YEAR }

    private ViewMode currentViewMode = ViewMode.MONTH;
    private YearMonth currentYearMonth;
    private LocalDate selectedDate;
    private LocalDate minDate;
    private LocalDate maxDate;

    private final Consumer<LocalDate> onDateSelectCallback;
    private JLabel headerLabel;
    private JPanel cardPanel;
    private CardLayout cardLayout;
    private JPanel monthViewGrid;
    private JPanel yearViewGrid;

    private final ButtonGroup dayButtonsGroup = new ButtonGroup();

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final int YEARS_PER_VIEW = 12;

    public CalendarPanel(Consumer<LocalDate> onDateSelectCallback) {
        this.onDateSelectCallback = onDateSelectCallback;
        this.currentYearMonth = YearMonth.now();
        this.selectedDate = null;

        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        add(createHeaderPanel(), BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setOpaque(false);

        monthViewGrid = new JPanel(new GridLayout(6, 7, 5, 5));
        yearViewGrid = new JPanel(new GridLayout(4, 3, 10, 10));

        cardPanel.add(createMonthViewPanel(monthViewGrid), ViewMode.MONTH.name());
        cardPanel.add(createYearViewPanel(yearViewGrid), ViewMode.YEAR.name());

        add(cardPanel, BorderLayout.CENTER);
        updateView();
    }

    public void setDataRange(LocalDate min, LocalDate max) {
        this.minDate = min;
        this.maxDate = max;
        updateView(); // Re-render to enable/disable days
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(UIManager.getFont("app.font.widget_title"));
        headerLabel.setForeground(UIManager.getColor("Label.foreground"));
        headerLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        headerLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (currentViewMode == ViewMode.MONTH) {
                    currentViewMode = ViewMode.YEAR;
                    updateView();
                }
            }
        });
        headerPanel.add(headerLabel, BorderLayout.CENTER);

        JButton prevButton = createNavButton(UITheme.Icons.ARROW_LEFT);
        prevButton.addActionListener(e -> navigate(-1));
        headerPanel.add(prevButton, BorderLayout.WEST);

        JButton nextButton = createNavButton(UITheme.Icons.ARROW_RIGHT);
        nextButton.addActionListener(e -> navigate(1));
        headerPanel.add(nextButton, BorderLayout.EAST);

        return headerPanel;
    }

    private void navigate(int direction) {
        if (currentViewMode == ViewMode.MONTH) {
            currentYearMonth = currentYearMonth.plusMonths(direction);
        } else {
            currentYearMonth = currentYearMonth.plusYears((long) direction * YEARS_PER_VIEW);
        }
        updateView();
    }

    private JPanel createMonthViewPanel(JPanel dayGrid) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.add(createDaysOfWeekPanel(), BorderLayout.NORTH);
        dayGrid.setOpaque(false);
        panel.add(dayGrid, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane createYearViewPanel(JPanel yearGrid) {
        yearGrid.setOpaque(false);
        yearGrid.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        JScrollPane scrollPane = new JScrollPane(yearGrid);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private void updateView() {
        if (currentViewMode == ViewMode.MONTH) {
            rebuildMonthView();
            cardLayout.show(cardPanel, ViewMode.MONTH.name());
        } else {
            rebuildYearView();
            cardLayout.show(cardPanel, ViewMode.YEAR.name());
        }
    }

    private void rebuildMonthView() {
        monthViewGrid.removeAll();
        dayButtonsGroup.clearSelection();
        headerLabel.setText(currentYearMonth.format(MONTH_YEAR_FORMATTER));

        LocalDate firstDayOfMonth = currentYearMonth.atDay(1);
        int firstDayOfWeekValue = firstDayOfMonth.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : firstDayOfMonth.getDayOfWeek().getValue();
        for (int i = 0; i < firstDayOfWeekValue; i++) {
            monthViewGrid.add(new JLabel(""));
        }

        for (int day = 1; day <= currentYearMonth.lengthOfMonth(); day++) {
            LocalDate date = currentYearMonth.atDay(day);
            DayButton dayButton = new DayButton(String.valueOf(day), date);
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                dayButton.setForeground(Color.RED.brighter());
            }
            if (date.equals(selectedDate)) {
                dayButton.setSelected(true);
            }
            // Disable button if outside the available data range
            boolean isEnabled = (minDate == null || !date.isBefore(minDate)) && (maxDate == null || !date.isAfter(maxDate));
            dayButton.setEnabled(isEnabled);

            dayButtonsGroup.add(dayButton);
            monthViewGrid.add(dayButton);
        }

        while (monthViewGrid.getComponentCount() < 42) {
            monthViewGrid.add(new JLabel(""));
        }

        monthViewGrid.revalidate();
        monthViewGrid.repaint();
    }

    private void rebuildYearView() {
        yearViewGrid.removeAll();
        int currentYear = currentYearMonth.getYear();
        int yearBlockStart = currentYear - (currentYear % YEARS_PER_VIEW);
        headerLabel.setText(String.format("%d - %d", yearBlockStart, yearBlockStart + YEARS_PER_VIEW - 1));

        for (int i = 0; i < YEARS_PER_VIEW; i++) {
            final int year = yearBlockStart + i;
            JButton yearButton = new JButton(String.valueOf(year));
            styleYearButton(yearButton);
            yearButton.addActionListener(e -> {
                currentYearMonth = YearMonth.of(year, currentYearMonth.getMonthValue());
                currentViewMode = ViewMode.MONTH;
                updateView();
            });
            yearViewGrid.add(yearButton);
        }

        yearViewGrid.revalidate();
        yearViewGrid.repaint();
    }

    private void styleYearButton(JButton button) {
        button.setFont(UIManager.getFont("app.font.subheading"));
        button.setForeground(UIManager.getColor("Label.foreground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    private JPanel createDaysOfWeekPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 7, 5, 5));
        panel.setOpaque(false);
        DayOfWeek[] days = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
        for (DayOfWeek day : days) {
            JLabel dayLabel = new JLabel(day.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US).toUpperCase(), SwingConstants.CENTER);
            dayLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
            dayLabel.setForeground(day == DayOfWeek.SUNDAY ? Color.RED.brighter() : UIManager.getColor("Label.disabledForeground"));
            panel.add(dayLabel);
        }
        return panel;
    }

    private JButton createNavButton(String iconPath) {
        JButton button = new JButton(UITheme.getIcon(iconPath, 16, 16, UIManager.getColor("Label.disabledForeground")));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    private class DayButton extends JToggleButton {
        private final LocalDate date;

        public DayButton(String text, LocalDate date) {
            super(text);
            this.date = date;
            setFont(UIManager.getFont("app.font.widget_content"));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            addActionListener(e -> {
                selectedDate = this.date;
                if (onDateSelectCallback != null) {
                    onDateSelectCallback.accept(selectedDate);
                }
                // Instead of repainting the parent, we let the ButtonGroup handle deselection painting.
                // This is a more standard Swing approach.
            });
        }

        @Override
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            setForeground(b ? UIManager.getColor("Label.foreground") : UIManager.getColor("Label.disabledForeground").darker());
            if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                 setForeground(b ? Color.RED.brighter() : Color.RED.darker());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Custom painting for circular selection highlight.
            if (isSelected() && isEnabled()) {
                int diameter = Math.min(getWidth(), getHeight()) - 4;
                int x = (getWidth() - diameter) / 2;
                int y = (getHeight() - diameter) / 2;
                g2d.setColor(UIManager.getColor("app.color.neutral"));
                g2d.fillOval(x, y, diameter, diameter);
                // Set text color to white for selected state
                setForeground(Color.WHITE);
            } else {
                // Reset text color for non-selected state
                if (isEnabled()) {
                    setForeground(date.getDayOfWeek() == DayOfWeek.SUNDAY ? Color.RED.brighter() : UIManager.getColor("Label.foreground"));
                }
            }
            
            // Let the superclass handle painting the text on top of our custom background
            super.paintComponent(g2d);
            g2d.dispose();
        }
    }
}