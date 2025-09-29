package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.service.ReviewReminderService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.data.DataResampler;
import com.EcoChartPro.model.*;
import com.EcoChartPro.ui.Analysis.ComparativeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.HistoryViewPanel;
import com.EcoChartPro.ui.Analysis.MistakeAnalysisPanel; 
import com.EcoChartPro.ui.Analysis.TitledContentPanel;
import com.EcoChartPro.ui.Analysis.TradeReplayChartPanel;
import com.EcoChartPro.ui.dashboard.ComprehensiveReportPanel;
import com.EcoChartPro.ui.dashboard.widgets.EquityCurveChart;
import com.EcoChartPro.utils.DatabaseManager;
import com.EcoChartPro.utils.DataSourceManager;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class InsightsDialog extends JDialog implements PropertyChangeListener {

    private final ComprehensiveReportPanel reportPanel;
    private final HistoryViewPanel historyViewPanel;
    private final EquityCurveChart equityCurveChart;
    private final JList<CoachingInsight> coachingInsightsList;
    private final PerformanceAnalyticsPanel performanceAnalyticsPanel;
    private final MistakeAnalysisPanel mistakeAnalysisPanel; 
    private final ComparativeAnalysisPanel comparativeAnalysisPanel;

    private List<Trade> allTrades; // Store trades for reminder service
    private boolean reviewReminderReset = false;


    private final JLabel symbolLabel = createValueLabel();
    private final JLabel sideLabel = createValueLabel();
    private final JLabel entryPriceLabel = createValueLabel();
    private final JLabel exitTimeLabel = createValueLabel();
    private final JLabel entryTimeLabel = createValueLabel();
    private final JLabel pnlLabel = createValueLabel();
    private final JTextField planAdherenceField = new JTextField();
    private final JTextField emotionalStateField = new JTextField();
    private final TradeReplayChartPanel tradeReplayChart;

    private final JLabel tradeDurationLabel = createValueLabel();
    private final JLabel tradeMfeLabel = createValueLabel();
    private final JLabel tradeMaeLabel = createValueLabel();
    private final JLabel tradeEfficiencyLabel = createValueLabel();

    private final JList<String> mistakesList;
    private final DefaultListModel<String> mistakesListModel;
    private final JTextArea lessonsLearnedArea;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");

    public InsightsDialog(Frame owner) {
        super(owner, "Eco Chart Pro - Insights", false);
        setSize(1400, 800);
        setLocationRelativeTo(owner);

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: REPORT ---
        this.reportPanel = new ComprehensiveReportPanel();
        JScrollPane reportScrollPane = new JScrollPane(this.reportPanel);
        reportScrollPane.setBorder(null);
        reportScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tabbedPane.addTab("Report", reportScrollPane);

        // --- TAB 2: TRADE EXPLORER ---
        JPanel tradeExplorerPanel = new JPanel(new BorderLayout(10, 10));
        tradeExplorerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setOpaque(false);
        mainSplitPane.setResizeWeight(0.35);
        mainSplitPane.setBorder(null);

        this.tradeReplayChart = new TradeReplayChartPanel();
        this.mistakesListModel = new DefaultListModel<>();
        this.mistakesList = new JList<>(mistakesListModel);
        this.lessonsLearnedArea = new JTextArea(4, 20);
        this.equityCurveChart = new EquityCurveChart();

        historyViewPanel = new HistoryViewPanel();
        TitledContentPanel historyWrapper = new TitledContentPanel("Trade History", historyViewPanel);
        historyWrapper.setPreferredSize(new Dimension(230, 0));
        historyWrapper.setMinimumSize(new Dimension(220, 0));
        mainSplitPane.setLeftComponent(historyWrapper);

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.weightx = 0.5;
        gbc.weighty = 1.0;

        gbc.gridx = 0; gbc.gridy = 0;
        rightPanel.add(createLeftDetailColumn(), gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        rightPanel.add(createRightDetailColumn(), gbc);

        mainSplitPane.setRightComponent(new JScrollPane(rightPanel));
        tradeExplorerPanel.add(mainSplitPane, BorderLayout.CENTER);

        tabbedPane.addTab("Trade Explorer", tradeExplorerPanel);
        
        // --- TAB 3: COMPARATIVE INSIGHTS ---
        this.comparativeAnalysisPanel = new ComparativeAnalysisPanel();
        tabbedPane.addTab("Comparative Insights", this.comparativeAnalysisPanel);

        // --- TAB 4: PERFORMANCE ANALYTICS ---
        this.performanceAnalyticsPanel = new PerformanceAnalyticsPanel();
        tabbedPane.addTab("Performance Analytics", this.performanceAnalyticsPanel);

        // --- TAB 5: MISTAKE ANALYSIS (NEW) ---
        this.mistakeAnalysisPanel = new MistakeAnalysisPanel();
        tabbedPane.addTab("Mistake Analysis", this.mistakeAnalysisPanel);

        // --- TAB 6: COACHING ---
        this.coachingInsightsList = new JList<>();
        tabbedPane.addTab("Coaching", createCoachingPanel());

        // --- FINAL ASSEMBLY & LISTENERS ---
        setContentPane(tabbedPane);

        tabbedPane.addChangeListener(e -> {
            if (!reviewReminderReset && tabbedPane.getSelectedIndex() == 2) { // 2 is the index for "Comparative Insights"
                ReviewReminderService.getInstance().markReviewComplete(allTrades);
                reviewReminderReset = true;
            }
        });

        historyViewPanel.getHistoryTree().addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) historyViewPanel.getHistoryTree().getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getUserObject() instanceof Trade) {
                updateDetailsView((Trade) selectedNode.getUserObject());
            } else {
                clearDetails();
            }
        });
        tradeReplayChart.addPropertyChangeListener("timeframeChanged", evt -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) historyViewPanel.getHistoryTree().getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getUserObject() instanceof Trade) {
                Trade trade = (Trade) selectedNode.getUserObject();
                fetchAndDisplayChartData(trade, (Timeframe) evt.getNewValue());
            }
        });
        historyViewPanel.addPropertyChangeListener("filteredTradesChanged", this);

        PaperTradingService.getInstance().addPropertyChangeListener(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    // New method to create the Coaching tab panel.
    private JComponent createCoachingPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel headerLabel = new JLabel("Detected Patterns & Insights");
        headerLabel.setFont(UIManager.getFont("app.font.heading"));
        panel.add(headerLabel, BorderLayout.NORTH);

        coachingInsightsList.setCellRenderer(new CoachingInsightRenderer());
        coachingInsightsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Add a listener to potentially filter trades when an insight is clicked (future enhancement)
        coachingInsightsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CoachingInsight selected = coachingInsightsList.getSelectedValue();
                // This is where you could trigger a filter on the Trade Explorer tab
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(coachingInsightsList);
        scrollPane.setBorder(UIManager.getBorder("TextField.border"));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    @Override
    public void dispose() {
        PaperTradingService.getInstance().removePropertyChangeListener(this);
        historyViewPanel.removePropertyChangeListener("filteredTradesChanged", this);
        super.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("tradeHistoryUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                ReplaySessionState state = PaperTradingService.getInstance().getCurrentSessionState();
                if (state != null) {
                    loadSessionData(state);
                }
            });
        }
        if ("filteredTradesChanged".equals(evt.getPropertyName()) && evt.getNewValue() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Trade> filteredTrades = (List<Trade>) evt.getNewValue();
            updateEquityCurveForFilter(filteredTrades);
        }
    }

    private JPanel createLeftDetailColumn() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0,0,15,0);
        gbc.weightx = 1.0;

        gbc.gridy = 0;
        panel.add(createTradeDetailsPanel(), gbc);
        
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new TitledContentPanel("Trade Replay", tradeReplayChart), gbc);
        return panel;
    }

    private JPanel createRightDetailColumn() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0,0,15,0);
        gbc.weightx = 1.0;

        gbc.gridy = 0;
        gbc.weighty = 0;
        panel.add(createPostTradeReflectionPanel(), gbc);
        
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        panel.add(new TitledContentPanel("Filtered Equity Curve", equityCurveChart), gbc);
        return panel;
    }

    private TitledContentPanel createTradeDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 20);
    
        // Column 1
        gbc.gridx = 0; gbc.gridy = 0; panel.add(createLabel("Symbol"), gbc);
        gbc.gridx = 1; panel.add(symbolLabel, gbc);
        gbc.gridy = 1; gbc.gridx = 0; panel.add(createLabel("Side"), gbc);
        gbc.gridx = 1; panel.add(sideLabel, gbc);
        gbc.gridy = 2; gbc.gridx = 0; panel.add(createLabel("Entry price"), gbc);
        gbc.gridx = 1; panel.add(entryPriceLabel, gbc);
        gbc.gridy = 3; gbc.gridx = 0; panel.add(createLabel("P&L"), gbc);
        gbc.gridx = 1; panel.add(pnlLabel, gbc);

        // Column 2
        gbc.gridx = 2; gbc.gridy = 0; panel.add(createLabel("Entry Time"), gbc);
        gbc.gridx = 3; panel.add(entryTimeLabel, gbc);
        gbc.gridy = 1; gbc.gridx = 2; panel.add(createLabel("Exit Time"), gbc);
        gbc.gridx = 3; panel.add(exitTimeLabel, gbc);
        gbc.gridy = 2; gbc.gridx = 2; panel.add(createLabel("Duration"), gbc);
        gbc.gridx = 3; panel.add(tradeDurationLabel, gbc);
        gbc.gridy = 3; gbc.gridx = 2; panel.add(createLabel("Efficiency"), gbc);
        gbc.gridx = 3; panel.add(tradeEfficiencyLabel, gbc);

        // Column 3
        gbc.gridx = 4; gbc.gridy = 0; panel.add(createLabel("MFE"), gbc);
        gbc.gridx = 5; panel.add(tradeMfeLabel, gbc);
        gbc.gridy = 1; gbc.gridx = 4; panel.add(createLabel("MAE"), gbc);
        gbc.gridx = 5; panel.add(tradeMaeLabel, gbc);
    
        return new TitledContentPanel("Trade Details & Analytics", panel);
    }
    
    private TitledContentPanel createPostTradeReflectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 8, 0);
        gbc.weightx = 1.0;
        
        gbc.gridy = 0;
        panel.add(createLabel("Plan Adherence"), gbc);
        gbc.gridy++;
        planAdherenceField.setEditable(false);
        panel.add(planAdherenceField, gbc);

        gbc.gridy++;
        panel.add(createLabel("Emotional State"), gbc);
        gbc.gridy++;
        emotionalStateField.setEditable(false);
        panel.add(emotionalStateField, gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        JSplitPane reflectionSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        reflectionSplit.setOpaque(false);
        reflectionSplit.setResizeWeight(0.5);
        reflectionSplit.setBorder(null);

        mistakesList.setOpaque(false);
        lessonsLearnedArea.setLineWrap(true);
        lessonsLearnedArea.setWrapStyleWord(true);
        lessonsLearnedArea.setEditable(false);
        lessonsLearnedArea.setOpaque(false);

        reflectionSplit.setTopComponent(createTitledScrollPane("Identified Mistakes", mistakesList));
        reflectionSplit.setBottomComponent(createTitledScrollPane("Lessons Learned", lessonsLearnedArea));
        
        panel.add(reflectionSplit, gbc);
        
        return new TitledContentPanel("Post-Trade Reflection", panel);
    }
    
    private JComponent createTitledScrollPane(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel label = createLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        panel.add(label, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void updateDetailsView(Trade trade) {
        if (trade == null) {
            clearDetails();
            return;
        }

        symbolLabel.setText(trade.symbol().name().toUpperCase());
        sideLabel.setText(trade.direction().toString());
        entryPriceLabel.setText(String.format("%.2f", trade.entryPrice()));
        exitTimeLabel.setText(TIME_FORMATTER.format(trade.exitTime()));
        entryTimeLabel.setText(TIME_FORMATTER.format(trade.entryTime()));

        BigDecimal pnl = trade.profitAndLoss();
        pnlLabel.setText(PNL_FORMAT.format(pnl));
        pnlLabel.setForeground(pnl.signum() >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));

        planAdherenceField.setText(trade.planAdherence() != null ? trade.planAdherence().toString() : "N/A");
        emotionalStateField.setText(trade.emotionalState() != null ? trade.emotionalState().toString() : "N/A");
        
        mistakesListModel.clear();
        if (trade.identifiedMistakes() != null && !trade.identifiedMistakes().isEmpty()) {
            mistakesListModel.addAll(trade.identifiedMistakes());
        } else {
            mistakesListModel.addElement("No mistakes recorded.");
        }
        lessonsLearnedArea.setText(trade.lessonsLearned() != null && !trade.lessonsLearned().isEmpty() ? trade.lessonsLearned() : "No lessons recorded.");
        
        fetchAndDisplayChartData(trade, Timeframe.M1);
        updateAnalyticsForTrade(trade);
    }

    private void fetchAndDisplayChartData(Trade trade, Timeframe timeframe) {
        tradeReplayChart.setData(null, Collections.emptyList());
        
        SwingWorker<List<KLine>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<KLine> doInBackground() throws Exception {
                Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                        .filter(s -> s.symbol().equalsIgnoreCase(trade.symbol().name())).findFirst();
                if (sourceOpt.isPresent()) {
                    try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + sourceOpt.get().dbPath().toAbsolutePath())) {
                        Instant startTime = trade.entryTime().minus(Duration.ofMinutes(15));
                        Instant endTime = trade.exitTime().plus(Duration.ofMinutes(15));
                        List<KLine> baseData = db.getKLinesBetween(new Symbol(trade.symbol().name()), "1m", startTime, endTime);

                        if (timeframe != Timeframe.M1) {
                            return DataResampler.resample(baseData, timeframe);
                        }
                        return baseData;
                    }
                }
                return Collections.emptyList();
            }

            @Override
            protected void done() {
                try {
                    tradeReplayChart.setData(trade, get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
    
    private void updateAnalyticsForTrade(Trade trade) {
        tradeDurationLabel.setText(formatDuration(Duration.between(trade.entryTime(), trade.exitTime())));
        tradeMfeLabel.setText("Calculating...");
        tradeMaeLabel.setText("Calculating...");
        tradeEfficiencyLabel.setText("Calculating...");

        SwingWorker<Map<String, BigDecimal>, Void> analyticsWorker = new SwingWorker<>() {
            @Override
            protected Map<String, BigDecimal> doInBackground() throws Exception {
                Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(trade.symbol().name())).findFirst();

                if (sourceOpt.isEmpty()) {
                    return Collections.emptyMap();
                }

                try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + sourceOpt.get().dbPath().toAbsolutePath())) {
                    Instant startTime = trade.entryTime().minus(Duration.ofMinutes(1));
                    Instant endTime = trade.exitTime().plus(Duration.ofMinutes(1));
                    List<KLine> klines = db.getKLinesBetween(new Symbol(trade.symbol().name()), "1m", startTime, endTime);

                    if (klines.isEmpty()) {
                        return Collections.emptyMap();
                    }

                    BigDecimal highestHigh = trade.entryPrice();
                    BigDecimal lowestLow = trade.entryPrice();

                    for (KLine k : klines) {
                        if (!k.timestamp().isBefore(trade.entryTime()) && !k.timestamp().isAfter(trade.exitTime())) {
                            if (k.high().compareTo(highestHigh) > 0) highestHigh = k.high();
                            if (k.low().compareTo(lowestLow) < 0) lowestLow = k.low();
                        }
                    }

                    BigDecimal mfe, mae;
                    if (trade.direction() == TradeDirection.LONG) {
                        mfe = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
                        mae = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
                    } else { // SHORT
                        mfe = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
                        mae = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
                    }

                    Map<String, BigDecimal> results = new java.util.HashMap<>();
                    results.put("mfe", mfe);
                    results.put("mae", mae);
                    return results;
                }
            }

            @Override
            protected void done() {
                try {
                    Map<String, BigDecimal> results = get();
                    BigDecimal mfe = results.get("mfe");
                    BigDecimal mae = results.get("mae");

                    if (mfe != null) {
                        tradeMfeLabel.setText(PNL_FORMAT.format(mfe));
                        tradeMfeLabel.setForeground(UIManager.getColor("app.color.positive"));
                    } else {
                        tradeMfeLabel.setText("N/A");
                    }

                    if (mae != null) {
                        tradeMaeLabel.setText(PNL_FORMAT.format(mae.negate()));
                        tradeMaeLabel.setForeground(UIManager.getColor("app.color.negative"));
                    } else {
                        tradeMaeLabel.setText("N/A");
                    }

                    if (mfe != null && mfe.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal pnl = trade.profitAndLoss();
                        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal efficiency = pnl.divide(mfe, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                            tradeEfficiencyLabel.setText(String.format("%.0f%%", efficiency));
                        } else {
                            tradeEfficiencyLabel.setText("N/A");
                        }
                    } else {
                        tradeEfficiencyLabel.setText("N/A");
                    }

                } catch (Exception e) {
                    tradeMfeLabel.setText("Error");
                    tradeMaeLabel.setText("Error");
                    tradeEfficiencyLabel.setText("Error");
                }
            }
        };
        analyticsWorker.execute();
    }

    private void clearDetails() {
        symbolLabel.setText("-");
        sideLabel.setText("-");
        entryPriceLabel.setText("-");
        exitTimeLabel.setText("-");
        entryTimeLabel.setText("-");
        pnlLabel.setText("-");
        pnlLabel.setForeground(UIManager.getColor("Label.foreground"));
        planAdherenceField.setText("");
        emotionalStateField.setText("");
        mistakesListModel.clear();
        lessonsLearnedArea.setText("");
        if (tradeReplayChart != null) tradeReplayChart.setData(null, null);

        tradeDurationLabel.setText("-");
        tradeMfeLabel.setText("-");
        tradeMaeLabel.setText("-");
        tradeEfficiencyLabel.setText("-");
        tradeMfeLabel.setForeground(UIManager.getColor("Label.foreground"));
        tradeMaeLabel.setForeground(UIManager.getColor("Label.foreground"));
    }
    
    private void updateEquityCurveForFilter(List<Trade> filteredTrades) {
        if (filteredTrades == null || filteredTrades.isEmpty()) {
            equityCurveChart.updateData(Collections.emptyList());
            return;
        }

        List<Trade> sorted = filteredTrades.stream()
            .sorted(Comparator.comparing(Trade::exitTime))
            .collect(Collectors.toList());

        List<JournalAnalysisService.EquityPoint> curve = new ArrayList<>();
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        
        if (!sorted.isEmpty()) {
            curve.add(new JournalAnalysisService.EquityPoint(sorted.get(0).entryTime().minusSeconds(1), BigDecimal.ZERO));
        }

        for (Trade trade : sorted) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            curve.add(new JournalAnalysisService.EquityPoint(trade.exitTime(), cumulativePnl));
        }
        
        equityCurveChart.updateData(curve);
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        return label;
    }
    
    private JLabel createValueLabel() {
        JLabel label = new JLabel("-");
        label.setFont(UIManager.getFont("app.font.widget_content").deriveFont(Font.BOLD));
        return label;
    }

    public void loadSessionData(ReplaySessionState state) {
        if (state == null || state.tradeHistory() == null) {
            this.allTrades = Collections.emptyList();
        } else {
            this.allTrades = state.tradeHistory();
        }

        JournalAnalysisService service = new JournalAnalysisService();
        BigDecimal initialBalance = (state != null) ? state.accountBalance().subtract(allTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add)) : new BigDecimal("100000");
        JournalAnalysisService.OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);
        
        this.reportPanel.updateData(stats, service, state);
        
        historyViewPanel.updateTradeHistory(allTrades);
        updateEquityCurveForFilter(allTrades);
        
        this.comparativeAnalysisPanel.loadData(allTrades);
        
        this.performanceAnalyticsPanel.loadSessionData(state);

        // --- Load data for Mistake Analysis panel ---
        Map<String, MistakeStats> mistakeData = service.analyzeMistakes(allTrades);
        this.mistakeAnalysisPanel.updateData(mistakeData);

        GamificationService gamificationService = GamificationService.getInstance();
        Optional<DataSourceManager.ChartDataSource> sourceOpt = (state != null)
            ? DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.dataSourceSymbol())).findFirst()
            : Optional.empty();

        List<CoachingInsight> insights = CoachingService.getInstance().analyze(
            allTrades,
            gamificationService.getOptimalTradeCount(),
            gamificationService.getPeakPerformanceHours(),
            sourceOpt
        );
        DefaultListModel<CoachingInsight> listModel = new DefaultListModel<>();
        listModel.addAll(insights);
        coachingInsightsList.setModel(listModel);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long absSeconds = Math.abs(seconds);
        return String.format("%d:%02d:%02d",
            TimeUnit.SECONDS.toHours(absSeconds),
            TimeUnit.SECONDS.toMinutes(absSeconds) % 60,
            absSeconds % 60
        );
    }
}