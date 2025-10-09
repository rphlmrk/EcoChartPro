package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WeeklyDetailViewPanel extends JPanel {
    private LocalDate currentWeek;
    private final Map<LocalDate, JournalAnalysisService.DailyStats> dailyStats;
    private final List<Trade> allTrades;
    private final Consumer<String> backCallback;

    private final JPanel weekContainer;
    private final DefaultListModel<Trade> tradeListModel = new DefaultListModel<>();
    private final JTextArea notesArea = new JTextArea();
    private final ButtonGroup dayButtonGroup = new ButtonGroup();

    public WeeklyDetailViewPanel(List<Trade> allTrades, Map<LocalDate, JournalAnalysisService.DailyStats> dailyStats, LocalDate initialDate, Consumer<String> backCallback) {
        this.allTrades = allTrades;
        this.dailyStats = dailyStats;
        this.backCallback = backCallback;
        this.currentWeek = initialDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));

        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setOpaque(false);
        mainSplitPane.setBorder(null);
        mainSplitPane.setResizeWeight(0.55);

        weekContainer = new JPanel();
        weekContainer.setOpaque(false);
        weekContainer.setLayout(new BoxLayout(weekContainer, BoxLayout.Y_AXIS));

        mainSplitPane.setLeftComponent(createLeftPanel());
        mainSplitPane.setRightComponent(createRightPanel());

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);

        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) navigateWeeks(-1);
            else navigateWeeks(1);
        });

        buildWeekView();
        showDetailsForDay(initialDate);
    }

    private void navigateWeeks(int direction) {
        currentWeek = currentWeek.plusWeeks(direction);
        buildWeekView();

        ButtonModel selection = dayButtonGroup.getSelection();
        LocalDate dayToSelect = currentWeek.plusDays(3); // Default to middle of the week
        if (selection != null) {
            int dayIndex = Integer.parseInt(selection.getActionCommand());
            LocalDate sameDayNextWeek = currentWeek.plusDays(dayIndex);
            if (dailyStats.containsKey(sameDayNextWeek)) {
                dayToSelect = sameDayNextWeek;
            }
        }
        showDetailsForDay(dayToSelect);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JButton backButton = new JButton("Back to Calendar", UITheme.getIcon(UITheme.Icons.ARROW_LEFT, 16, 16));
        backButton.addActionListener(e -> backCallback.accept("CALENDAR_VIEW"));
        header.add(backButton, BorderLayout.WEST);
        return header;
    }

    private JComponent createLeftPanel() {
        JPanel weekScrollerPanel = new JPanel(new BorderLayout());
        weekScrollerPanel.setOpaque(false);

        JButton upButton = createNavButton(true);
        upButton.addActionListener(e -> navigateWeeks(-1));
        JButton downButton = createNavButton(false);
        downButton.addActionListener(e -> navigateWeeks(1));
        
        weekScrollerPanel.add(upButton, BorderLayout.NORTH);
        weekScrollerPanel.add(weekContainer, BorderLayout.CENTER);
        weekScrollerPanel.add(downButton, BorderLayout.SOUTH);

        JList<Trade> tradeList = new JList<>(tradeListModel);
        tradeList.setCellRenderer(new HistoryTreeCellRenderer());
        tradeList.setOpaque(false);
        JScrollPane tradeListScroller = new JScrollPane(tradeList);
        tradeListScroller.setOpaque(false);
        tradeListScroller.getViewport().setOpaque(false);
        TitledContentPanel tradeListPanel = new TitledContentPanel("Trades on Day", tradeListScroller);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, weekScrollerPanel, tradeListPanel);
        leftSplit.setOpaque(false);
        leftSplit.setBorder(null);
        leftSplit.setResizeWeight(0.4);
        return leftSplit;
    }

    private void buildWeekView() {
        weekContainer.removeAll();
        weekContainer.add(createWeekPanel(currentWeek, true));
        weekContainer.revalidate();
        weekContainer.repaint();
    }

    private JPanel createWeekPanel(LocalDate weekStartDate, boolean isCenter) {
        JPanel weekPanel = new JPanel(new GridLayout(1, 7, 8, 8));
        weekPanel.setOpaque(false);
        weekPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0)); // Further reduced vertical padding

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStartDate.plusDays(i);
            JToggleButton dayButton = createDayButton(date);
            if (isCenter) {
                dayButtonGroup.add(dayButton);
                dayButton.setActionCommand(String.valueOf(i));
                dayButton.addActionListener(e -> showDetailsForDay(date));
            }
            weekPanel.add(dayButton);
        }
        return weekPanel;
    }

    private JToggleButton createDayButton(LocalDate date) {
        String text = String.format("<html><center>%s<br><font size='+1'>%d</font></center></html>",
            date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US).toUpperCase(),
            date.getDayOfMonth());
        JToggleButton button = new JToggleButton(text);
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 5, 2, 5));
        button.setPreferredSize(new Dimension(0, 20)); // Further reduced height

        JournalAnalysisService.DailyStats dayData = dailyStats.get(date);
        if (dayData != null) {
            BigDecimal pnl = dayData.totalPnl();
            Color bgColor = (pnl.signum() > 0) ? UIManager.getColor("app.journal.profit")
                : (pnl.signum() < 0) ? UIManager.getColor("app.journal.loss")
                : UIManager.getColor("app.journal.breakeven");
            button.setBackground(bgColor);

            double luminance = (0.299 * bgColor.getRed() + 0.587 * bgColor.getGreen() + 0.114 * bgColor.getBlue()) / 255;
            button.setForeground((luminance > 0.5) ? Color.BLACK : Color.WHITE);
        } else {
            button.setBackground(UIManager.getColor("Component.borderColor"));
            button.setForeground(UIManager.getColor("Label.disabledForeground"));
            button.setEnabled(false);
        }
        return button;
    }

    private JComponent createRightPanel() {
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setEditable(false);
        notesArea.setOpaque(false);
        JScrollPane notesScroller = new JScrollPane(notesArea);
        notesScroller.setOpaque(false);
        notesScroller.getViewport().setOpaque(false);
        return new TitledContentPanel("Aggregated Notes", notesScroller);
    }

    private void showDetailsForDay(LocalDate date) {
        tradeListModel.clear();
        if (dailyStats.containsKey(date)) {
            tradeListModel.addAll(allTrades.stream()
                .filter(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate().equals(date))
                .collect(Collectors.toList()));
            
            for(AbstractButton btn : Collections.list(dayButtonGroup.getElements())) {
                int dayIndex = Integer.parseInt(btn.getActionCommand());
                if(currentWeek.plusDays(dayIndex).equals(date)) {
                    btn.setSelected(true);
                    break;
                }
            }
        }

        String aggregatedNotes = Collections.list(tradeListModel.elements()).stream()
            .map(Trade::notes)
            .filter(note -> note != null && !note.isBlank())
            .collect(Collectors.joining("\n- - - - -\n"));
        notesArea.setText(aggregatedNotes.isEmpty() ? "No notes recorded for trades on this day." : aggregatedNotes);
        notesArea.setCaretPosition(0);
    }
    
    private JButton createNavButton(boolean isUp) {
        Icon icon = isUp ? new RotatedIcon(UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 24, 24), RotatedIcon.Rotate.UP)
                         : UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 24, 24);
        
        Icon rollover = isUp ? new RotatedIcon(UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 24, 24, UIManager.getColor("app.color.accent")), RotatedIcon.Rotate.UP)
                             : UITheme.getIcon(UITheme.Icons.CHEVRON_DOWN, 24, 24, UIManager.getColor("app.color.accent"));

        JButton button = new JButton(icon);
        button.setRolloverIcon(rollover);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    
    private static class RotatedIcon implements Icon {
        public enum Rotate { UP, DOWN }
        private final Icon icon;
        private final Rotate rotate;

        public RotatedIcon(Icon icon, Rotate rotate) { this.icon = icon; this.rotate = rotate; }
        @Override public int getIconWidth() { return icon.getIconWidth(); }
        @Override public int getIconHeight() { return icon.getIconHeight(); }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            if (rotate == Rotate.UP) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(x + getIconWidth(), y + getIconHeight());
                g2.rotate(Math.toRadians(180));
                icon.paintIcon(c, g2, 0, 0);
                g2.dispose();
            } else {
                icon.paintIcon(c, g, x, y);
            }
        }
    }
}