package com.EcoChartPro.ui.sidebar.journal;

import com.EcoChartPro.core.journal.JournalAnalysisService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public class SidebarWeeklyViewPanel extends JPanel {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    private LocalDate selectedDate;
    private final JLabel headerLabel;
    private final DayButton[] dayButtons = new DayButton[7];
    private final Border defaultButtonBorder = BorderFactory.createEmptyBorder(5, 5, 5, 5);
    private final Border selectedButtonBorder;
    
    private Map<LocalDate, JournalAnalysisService.DailyStats> dailyStatsMap = Collections.emptyMap();

    public SidebarWeeklyViewPanel() {
        this.selectedDate = LocalDate.now();
        this.selectedButtonBorder = new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, UIManager.getColor("Component.focusedBorderColor")),
                BorderFactory.createEmptyBorder(5, 5, 3, 5)
        );

        setOpaque(true);
        setBackground(UIManager.getColor("Panel.background"));
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        
        setMaximumSize(new Dimension(280, Short.MAX_VALUE));
        setPreferredSize(new Dimension(280, 120));

        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(16f));
        headerLabel.setForeground(UIManager.getColor("Label.foreground"));
        add(createHeaderPanel(headerLabel), BorderLayout.NORTH);

        JPanel calendarGridPanel = new JPanel(new GridLayout(2, 7, 5, 0));
        calendarGridPanel.setOpaque(false);

        DayOfWeek[] daysOfWeek = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
        for (DayOfWeek day : daysOfWeek) {
            JLabel dayLabel = new JLabel(day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), SwingConstants.CENTER);
            dayLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
            dayLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            calendarGridPanel.add(dayLabel);
        }

        for (int i = 0; i < 7; i++) {
            dayButtons[i] = new DayButton("0", null);
            calendarGridPanel.add(dayButtons[i]);
        }

        add(calendarGridPanel, BorderLayout.CENTER);
        updateView(this.selectedDate, false);
    }

    public void setDailyStats(Map<LocalDate, JournalAnalysisService.DailyStats> stats) {
        this.dailyStatsMap = (stats != null) ? stats : Collections.emptyMap();
        rebuildWeeklyView();
    }

    public void updateView(LocalDate newDate, boolean fireEvent) {
        LocalDate oldDate = this.selectedDate;
        this.selectedDate = newDate != null ? newDate : LocalDate.now();
        rebuildWeeklyView();
        if (fireEvent) {
            firePropertyChange("date", oldDate, this.selectedDate);
        }
    }

    private void rebuildWeeklyView() {
        headerLabel.setText(selectedDate.format(MONTH_YEAR_FORMATTER));
        LocalDate startOfWeek = selectedDate;
        while (startOfWeek.getDayOfWeek() != DayOfWeek.SUNDAY) {
            startOfWeek = startOfWeek.minusDays(1);
        }

        for (int i = 0; i < 7; i++) {
            LocalDate dateForButton = startOfWeek.plusDays(i);
            DayButton button = dayButtons[i];
            button.setText(String.valueOf(dateForButton.getDayOfMonth()));
            button.setForeground(UIManager.getColor("Button.foreground"));
            
            button.setStats(dailyStatsMap.get(dateForButton));

            if (dateForButton.equals(selectedDate)) {
                button.setBorder(selectedButtonBorder);
            } else {
                button.setBorder(defaultButtonBorder);
            }
            if (dateForButton.getMonth() != selectedDate.getMonth()) {
                button.setForeground(UIManager.getColor("Button.disabledText"));
            }

            for (ActionListener al : button.getActionListeners()) {
                button.removeActionListener(al);
            }
            button.addActionListener(e -> updateView(dateForButton, true));
        }
        revalidate();
        repaint();
    }

    private JPanel createHeaderPanel(JLabel titleLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JButton prevButton = createNavButton("<");
        prevButton.addActionListener(e -> updateView(selectedDate.minusWeeks(1), true));
        JButton nextButton = createNavButton(">");
        nextButton.addActionListener(e -> updateView(selectedDate.plusWeeks(1), true));
        panel.add(prevButton, BorderLayout.WEST);
        panel.add(titleLabel, BorderLayout.CENTER);
        panel.add(nextButton, BorderLayout.EAST);
        return panel;
    }

    private JButton createNavButton(String text) {
        JButton button = new JButton(text);
        button.setFont(UIManager.getFont("app.font.widget_title").deriveFont(18f));
        button.setForeground(UIManager.getColor("Label.disabledForeground"));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
    
    private static class DayButton extends JButton {
        private JournalAnalysisService.DailyStats stats;

        DayButton(String initialText, JournalAnalysisService.DailyStats stats) {
            super(initialText);
            this.stats = stats;
            setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(30, 30));
            setMaximumSize(new Dimension(30, 30));
        }
        
        public void setStats(JournalAnalysisService.DailyStats stats) {
            this.stats = stats;
            repaint();
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
}