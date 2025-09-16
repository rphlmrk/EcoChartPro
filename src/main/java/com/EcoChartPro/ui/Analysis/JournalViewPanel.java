package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.DailyStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.WeeklyStats;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JournalViewPanel extends JPanel {
    private final JournalAnalysisService analysisService;
    private Map<LocalDate, DailyStats> dailyAnalysisResults;
    private Map<Integer, WeeklyStats> weeklyAnalysisResults;
    private DayCellPanel.ViewMode currentViewMode = DayCellPanel.ViewMode.PNL;
    private YearMonth currentYearMonth;
    private JLabel headerMonthLabel;
    private final DayCellPanel[][] dayCells = new DayCellPanel[6][7];
    private final WeeklySummaryPanel[] weeklySummaryPanels = new WeeklySummaryPanel[6];
    private final JPanel legendPanel;
    private static final DateTimeFormatter HEADER_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final Logger logger = LoggerFactory.getLogger(JournalViewPanel.class);

    private JButton prevButton;
    private JButton nextButton;
    private LocalDate minDate;
    private LocalDate maxDate;

    public JournalViewPanel() {
        this.analysisService = new JournalAnalysisService();
        this.currentYearMonth = YearMonth.now();
        setOpaque(false);
        setLayout(new BorderLayout(20, 15));
        setBorder(BorderFactory.createEmptyBorder(80, 25, 20, 25));
        add(createHeader(), BorderLayout.NORTH);
        add(createCalendarView(), BorderLayout.CENTER);

        JPanel southWrapper = new JPanel(new BorderLayout(0, 15));
        southWrapper.setOpaque(false);

        this.legendPanel = createLegend();
        southWrapper.add(createBottomControls(), BorderLayout.NORTH);
        southWrapper.add(legendPanel, BorderLayout.CENTER);

        add(southWrapper, BorderLayout.SOUTH);
        updateLegend();
    }
    
    private JPanel createBottomControls() {
        JPanel bottomControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomControlsPanel.setOpaque(false);
        
        JButton loadButton = new JButton("Load Session File...");
        loadButton.addActionListener(e -> handleLoadSessionFile());
        bottomControlsPanel.add(loadButton);
        
        return bottomControlsPanel;
    }

    private void handleLoadSessionFile() {
        JFileChooser fileChooser = new JFileChooser();
        try {
            fileChooser.setCurrentDirectory(SessionManager.getInstance().getSessionsDirectory().toFile());
        } catch (IOException ex) {
            logger.warn("Could not access default sessions directory, falling back to user home.", ex);
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }

        fileChooser.setDialogTitle("Open a Saved Replay Session to View Journal");
        fileChooser.setFileFilter(new FileNameExtensionFilter("Replay Session (*.json)", "json"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToLoad = fileChooser.getSelectedFile();
            try {
                ReplaySessionState state = SessionManager.getInstance().loadSession(fileToLoad);
                loadSessionData(state.tradeHistory());

            } catch (IOException ex) {
                logger.error("Failed to load session file for journal.", ex);
                JOptionPane.showMessageDialog(this, "Failed to load session: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    public void loadSessionData(List<Trade> trades) {
        if (trades == null) {
            trades = Collections.emptyList();
        }

        this.dailyAnalysisResults = analysisService.analyzeTradesByDay(trades);
        this.weeklyAnalysisResults = analysisService.analyzeTradesByWeek(trades);

        if (trades.isEmpty()) {
            this.currentYearMonth = YearMonth.now();
        } else {
            analysisService.getLastTradeDate(trades).ifPresent(lastDate -> {
                this.currentYearMonth = YearMonth.from(lastDate);
            });
        }

        this.minDate = null;
        this.maxDate = null;
        analysisService.getDateRange(trades).ifPresent(range -> {
            this.minDate = range.minDate();
            this.maxDate = range.maxDate();
        });

        updateCalendar();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Trading Calendar");
        title.setFont(UIManager.getFont("app.font.subheading").deriveFont(24f));
        title.setForeground(UIManager.getColor("Label.foreground"));
        header.add(title, BorderLayout.WEST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setOpaque(false);
        JToggleButton planButton = new JToggleButton("Plan");
        JToggleButton pnlButton = new JToggleButton("P&L", true);
        styleToggleButton(planButton);
        styleToggleButton(pnlButton);
        ButtonGroup viewToggleGroup = new ButtonGroup();
        viewToggleGroup.add(planButton);
        viewToggleGroup.add(pnlButton);
        controls.add(planButton);
        controls.add(pnlButton);
        planButton.addActionListener(e -> setViewMode(DayCellPanel.ViewMode.PLAN));
        pnlButton.addActionListener(e -> setViewMode(DayCellPanel.ViewMode.PNL));
        controls.add(Box.createHorizontalStrut(10));

        prevButton = new JButton(UITheme.getIcon(UITheme.Icons.ARROW_LEFT, 20, 20));
        styleNavButton(prevButton);
        prevButton.addActionListener(e -> {
            YearMonth nextMonth = currentYearMonth.minusMonths(1);
            if (minDate != null && nextMonth.isBefore(YearMonth.from(minDate))) {
                return;
            }
            currentYearMonth = nextMonth;
            updateCalendar();
        });
        controls.add(prevButton);

        headerMonthLabel = new JLabel();
        headerMonthLabel.setFont(UIManager.getFont("app.font.widget_title"));
        headerMonthLabel.setForeground(UIManager.getColor("Label.foreground"));
        controls.add(headerMonthLabel);

        nextButton = new JButton(UITheme.getIcon(UITheme.Icons.ARROW_RIGHT, 20, 20));
        styleNavButton(nextButton);
        nextButton.addActionListener(e -> {
            YearMonth nextMonth = currentYearMonth.plusMonths(1);
            if (maxDate != null && nextMonth.isAfter(YearMonth.from(maxDate))) {
                return;
            }
            currentYearMonth = nextMonth;
            updateCalendar();
        });
        controls.add(nextButton);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JPanel createCalendarView() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        DayOfWeek[] days = {DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
        gbc.gridy = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int c = 0; c < 7; c++) {
            gbc.gridx = c;
            gbc.weightx = 1.0;
            JLabel dayLabel = new JLabel(days[c].getDisplayName(TextStyle.SHORT, Locale.US), SwingConstants.CENTER);
            dayLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
            dayLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            container.add(dayLabel, gbc);
        }
        gbc.gridx = 7;
        gbc.weightx = 1.8;
        JPanel weeklyHeader = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        weeklyHeader.setOpaque(false);
        JLabel weeklyLabel = new JLabel("Weekly");
        weeklyLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        weeklyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        weeklyHeader.add(weeklyLabel);
        weeklyHeader.add(new JLabel(UITheme.getIcon(UITheme.Icons.INFO, 16, 16, UIManager.getColor("Label.disabledForeground"))));
        container.add(weeklyHeader, gbc);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        for (int r = 0; r < 6; r++) {
            gbc.gridy = r + 1;
            for (int c = 0; c < 7; c++) {
                gbc.gridx = c;
                gbc.weightx = 1.0;
                dayCells[r][c] = new DayCellPanel(1);
                container.add(dayCells[r][c], gbc);
            }
            gbc.gridx = 7;
            gbc.weightx = 1.8;
            weeklySummaryPanels[r] = new WeeklySummaryPanel();
            container.add(weeklySummaryPanels[r], gbc);
        }
        return container;
    }

    private void updateCalendar() {
        headerMonthLabel.setText(currentYearMonth.format(HEADER_FORMATTER));
        LocalDate date = currentYearMonth.atDay(1);
        int dayOfWeekValue = date.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : date.getDayOfWeek().getValue();
        for (int r = 0, dayOfMonth = 1; r < 6; r++) {
            boolean weekUpdated = false;
            for (int c = 0; c < 7; c++) {
                if (r == 0 && c < dayOfWeekValue) {
                    dayCells[r][c].setVisible(false);
                } else if (dayOfMonth <= currentYearMonth.lengthOfMonth()) {
                    dayCells[r][c].setVisible(true);
                    LocalDate currentDate = currentYearMonth.atDay(dayOfMonth);
                    DailyStats stats = (dailyAnalysisResults != null) ? dailyAnalysisResults.get(currentDate) : null;
                    dayCells[r][c].updateData(stats, currentViewMode);
                    ((JLabel) dayCells[r][c].getComponent(0)).setText(String.valueOf(dayOfMonth));
                    if (!weekUpdated) {
                        updateWeeklySummaryForRow(r, currentDate);
                        weekUpdated = true;
                    }
                    dayOfMonth++;
                } else {
                    dayCells[r][c].setVisible(false);
                }
            }
            if (!weekUpdated) {
                weeklySummaryPanels[r].clearData();
            }
        }
        updateNavButtonState();
    }

    private void updateWeeklySummaryForRow(int rowIndex, LocalDate dateInWeek) {
        if (weeklyAnalysisResults == null) {
            weeklySummaryPanels[rowIndex].clearData();
            return;
        }
        WeekFields weekFields = WeekFields.of(Locale.US);
        int weekOfYear = dateInWeek.get(weekFields.weekOfWeekBasedYear());
        WeeklyStats stats = weeklyAnalysisResults.get(weekOfYear);
        if (stats != null) {
            weeklySummaryPanels[rowIndex].updateData(stats, currentViewMode);
        } else {
            weeklySummaryPanels[rowIndex].clearData();
        }
    }

    private void setViewMode(DayCellPanel.ViewMode mode) {
        if (this.currentViewMode != mode) {
            this.currentViewMode = mode;
            updateLegend();
            updateCalendar();
        }
    }

    private void updateNavButtonState() {
        if (prevButton == null || nextButton == null) return;

        boolean canGoBack = (minDate == null) || !currentYearMonth.minusMonths(1).isBefore(YearMonth.from(minDate));
        boolean canGoForward = (maxDate == null) || !currentYearMonth.plusMonths(1).isAfter(YearMonth.from(maxDate));

        prevButton.setEnabled(canGoBack);
        nextButton.setEnabled(canGoForward);
    }

    private JPanel createLegend() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        panel.setOpaque(false);
        return panel;
    }

    private void updateLegend() {
        legendPanel.removeAll();
        if (currentViewMode == DayCellPanel.ViewMode.PNL) {
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.profit"), "Profitable"));
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.breakeven"), "Breakeven"));
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.loss"), "Loss"));
            legendPanel.add(createLegendItem(UIManager.getColor("Component.borderColor"), "No Trades"));
        } else {
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.plan.good"), "≥70% Plan"));
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.plan.ok"), "50-69% Plan"));
            legendPanel.add(createLegendItem(UIManager.getColor("app.journal.plan.bad"), "<50% Plan"));
            legendPanel.add(createLegendItem(UIManager.getColor("Component.borderColor"), "No Trades"));
        }
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    private JPanel createLegendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        item.setOpaque(false);
        JPanel colorDot = new JPanel();
        colorDot.setBackground(color);
        colorDot.setPreferredSize(new Dimension(12, 12));
        colorDot.setBorder(BorderFactory.createLineBorder(color.brighter()));
        JLabel label = new JLabel(text);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        item.add(colorDot);
        item.add(label);
        return item;
    }

    private void styleToggleButton(JToggleButton button) {
        button.setFocusPainted(false);
        button.setForeground(UIManager.getColor("Button.foreground"));
        button.setBackground(UIManager.getColor("Button.background"));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
    }

    private void styleNavButton(JButton button) {
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}