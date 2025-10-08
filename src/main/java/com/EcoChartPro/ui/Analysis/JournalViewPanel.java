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
import java.awt.event.MouseAdapter; // [FIX] Import MouseAdapter
import java.awt.event.MouseEvent;   // [FIX] Import MouseEvent
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
    private final JournalAnalysisService analysisService;
    private List<Trade> allTrades = Collections.emptyList();
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

    // --- New components for Two-Level View ---
    private final CardLayout mainCardLayout;
    private final JPanel mainCardPanel;
    private final JButton[] weekDayButtons = new JButton[7];
    private final DefaultListModel<Trade> tradeListModel;
    private final JTextArea notesArea;
    private LocalDate currentlySelectedDate;

    public JournalViewPanel() {
        this.analysisService = new JournalAnalysisService();
        this.currentYearMonth = YearMonth.now();
        setOpaque(false);
        setLayout(new BorderLayout(20, 15));
        setBorder(BorderFactory.createEmptyBorder(10, 25, 20, 25));

        // --- Main Layout ---
        mainCardLayout = new CardLayout();
        mainCardPanel = new JPanel(mainCardLayout);
        mainCardPanel.setOpaque(false);
        
        tradeListModel = new DefaultListModel<>();
        notesArea = new JTextArea();

        mainCardPanel.add(createCalendarView(), "MONTH_VIEW");
        mainCardPanel.add(createWeeklyDetailView(), "WEEKLY_DETAIL_VIEW");

        add(createHeader(), BorderLayout.NORTH);
        add(mainCardPanel, BorderLayout.CENTER);

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
            this.allTrades = Collections.emptyList();
        } else {
            this.allTrades = trades;
        }

        this.dailyAnalysisResults = analysisService.analyzeTradesByDay(this.allTrades);
        this.weeklyAnalysisResults = analysisService.analyzeTradesByWeek(this.allTrades);

        if (this.allTrades.isEmpty()) {
            this.currentYearMonth = YearMonth.now();
        } else {
            analysisService.getLastTradeDate(this.allTrades).ifPresent(lastDate -> {
                this.currentYearMonth = YearMonth.from(lastDate);
            });
        }

        this.minDate = null;
        this.maxDate = null;
        analysisService.getDateRange(this.allTrades).ifPresent(range -> {
            this.minDate = range.minDate();
            this.maxDate = range.maxDate();
        });

        mainCardLayout.show(mainCardPanel, "MONTH_VIEW");
        updateCalendar();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Trading Journal");
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
    
    private JSplitPane createWeeklyDetailView() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setOpaque(false);
        splitPane.setBorder(null);
        splitPane.setResizeWeight(0.4);

        // --- Left Panel (Week Selector & Trade List) ---
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.setOpaque(false);

        JPanel weekSelectorPanel = new JPanel(new GridLayout(1, 7, 5, 5));
        weekSelectorPanel.setOpaque(false);
        for(int i = 0; i < 7; i++) {
            weekDayButtons[i] = new JButton();
            final int dayIndex = i;
            weekDayButtons[i].addActionListener(e -> {
                LocalDate date = (LocalDate) weekDayButtons[dayIndex].getClientProperty("date");
                if (date != null) showDetailsForDay(date);
            });
            weekSelectorPanel.add(weekDayButtons[i]);
        }
        
        JList<Trade> tradeList = new JList<>(tradeListModel);
        tradeList.setCellRenderer(new HistoryTreeCellRenderer());
        tradeList.setOpaque(false);
        JScrollPane tradeListScroller = new JScrollPane(tradeList);
        tradeListScroller.setOpaque(false);
        tradeListScroller.getViewport().setOpaque(false);
        
        JButton backButton = new JButton("Back to Month View", UITheme.getIcon(UITheme.Icons.ARROW_LEFT, 16, 16));
        backButton.addActionListener(e -> mainCardLayout.show(mainCardPanel, "MONTH_VIEW"));

        leftPanel.add(weekSelectorPanel, BorderLayout.NORTH);
        leftPanel.add(tradeListScroller, BorderLayout.CENTER);
        leftPanel.add(backButton, BorderLayout.SOUTH);

        // --- Right Panel (Notes) ---
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setEditable(false); // Read-only for now
        notesArea.setOpaque(false);
        JScrollPane notesScroller = new JScrollPane(notesArea);
        notesScroller.setOpaque(false);
        notesScroller.getViewport().setOpaque(false);
        TitledContentPanel notesPanel = new TitledContentPanel("Aggregated Notes for Day", notesScroller);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(notesPanel);
        return splitPane;
    }

    private void updateCalendar() {
        headerMonthLabel.setText(currentYearMonth.format(HEADER_FORMATTER));
        LocalDate date = currentYearMonth.atDay(1);
        int dayOfWeekValue = date.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : date.getDayOfWeek().getValue();
        for (int r = 0, dayOfMonth = 1; r < 6; r++) {
            boolean weekUpdated = false;
            for (int c = 0; c < 7; c++) {
                DayCellPanel cell = dayCells[r][c];
                cell.putClientProperty("date", null);
                cell.setCursor(Cursor.getDefaultCursor());
                if (cell.getMouseListeners().length > 1) { // 1 is the default
                     cell.removeMouseListener(cell.getMouseListeners()[1]);
                }

                if (r == 0 && c < dayOfWeekValue) {
                    cell.setVisible(false);
                } else if (dayOfMonth <= currentYearMonth.lengthOfMonth()) {
                    cell.setVisible(true);
                    LocalDate currentDate = currentYearMonth.atDay(dayOfMonth);
                    cell.putClientProperty("date", currentDate);

                    DailyStats stats = (dailyAnalysisResults != null) ? dailyAnalysisResults.get(currentDate) : null;
                    cell.updateData(stats, currentViewMode);
                    ((JLabel) cell.getComponent(0)).setText(String.valueOf(dayOfMonth));

                    if (stats != null) {
                        cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        cell.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                LocalDate clickedDate = (LocalDate) ((JComponent)e.getSource()).getClientProperty("date");
                                if (clickedDate != null) {
                                    updateWeeklyDetailView(clickedDate);
                                    mainCardLayout.show(mainCardPanel, "WEEKLY_DETAIL_VIEW");
                                }
                            }
                        });
                    }

                    if (!weekUpdated) {
                        updateWeeklySummaryForRow(r, currentDate);
                        weekUpdated = true;
                    }
                    dayOfMonth++;
                } else {
                    cell.setVisible(false);
                }
            }
            if (!weekUpdated) {
                weeklySummaryPanels[r].clearData();
            }
        }
        updateNavButtonState();
    }
    
    private void updateWeeklyDetailView(LocalDate selectedDate) {
        this.currentlySelectedDate = selectedDate;
        LocalDate startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        
        for (int i=0; i < 7; i++) {
            LocalDate day = startOfWeek.plusDays(i);
            JButton button = weekDayButtons[i];
            button.putClientProperty("date", day);
            String buttonText = String.format("<html><center>%s<br>%d</center></html>",
                day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US),
                day.getDayOfMonth());
            button.setText(buttonText);
            
            // Check if there are trades on this day to enable/disable button
            button.setEnabled(dailyAnalysisResults.containsKey(day));
        }
        
        showDetailsForDay(selectedDate);
    }
    
    private void showDetailsForDay(LocalDate date) {
        this.currentlySelectedDate = date;

        // Update button highlighting
        for (JButton button : weekDayButtons) {
            LocalDate buttonDate = (LocalDate) button.getClientProperty("date");
            button.setSelected(date.equals(buttonDate));
        }

        // Update trade list
        tradeListModel.clear();
        List<Trade> tradesForDay = allTrades.stream()
            .filter(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate().equals(date))
            .collect(Collectors.toList());
        tradeListModel.addAll(tradesForDay);

        // Update notes area
        String aggregatedNotes = tradesForDay.stream()
            .map(Trade::notes)
            .filter(note -> note != null && !note.isBlank())
            .collect(Collectors.joining("\n- - - - -\n"));
        
        notesArea.setText(aggregatedNotes.isEmpty() ? "No notes recorded for trades on this day." : aggregatedNotes);
        notesArea.setCaretPosition(0);
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