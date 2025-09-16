package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.settings.Checklist;
import com.EcoChartPro.core.settings.ChecklistManager;
import com.EcoChartPro.model.EmotionalState;
import com.EcoChartPro.model.PlanAdherence;
import com.EcoChartPro.model.SetupQuality;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.Analysis.TotalSummaryView;
import com.EcoChartPro.ui.trading.JournalEntryDialog;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HistoryViewPanel extends JPanel {
    private final JTree historyTree;
    private final DefaultTreeModel treeModel;
    private final TotalSummaryView totalSummaryView;
    private List<Trade> allTrades = new ArrayList<>();
    private final JournalAnalysisService analysisService = new JournalAnalysisService();

    private final JComboBox<String> tagComboBox;
    private final JComboBox<String> directionComboBox;
    private final JComboBox<String> outcomeComboBox;
    private final JComboBox<String> groupByComboBox;
    private final JComboBox<String> mistakeComboBox;
    private final JComboBox<Object> checklistComboBox;


    public record YearNode(int year) {}
    public record MonthNode(YearMonth yearMonth) {}
    public record DateNode(LocalDate date) {}
    public record StrategyNode(String tagName, int tradeCount, BigDecimal totalPnl, double winRate) {}
    public record DayOfWeekNode(DayOfWeek day, int tradeCount, BigDecimal totalPnl, double winRate) {}
    public record EmotionalStateNode(EmotionalState state, int tradeCount, BigDecimal totalPnl, double winRate) {}
    public record PlanAdherenceNode(PlanAdherence adherence, int tradeCount, BigDecimal totalPnl, double winRate) {}
    public record MistakeNode(String mistake, int tradeCount, BigDecimal totalPnl, double winRate) {}
    public record SetupQualityNode(SetupQuality quality, int tradeCount, BigDecimal totalPnl, double winRate) {}

    public HistoryViewPanel() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);

        this.tagComboBox = new JComboBox<>(new String[]{"All Tags"});
        this.directionComboBox = new JComboBox<>(new String[]{"All", "Long", "Short"});
        this.outcomeComboBox = new JComboBox<>(new String[]{"All", "Wins", "Losses"});
        this.groupByComboBox = new JComboBox<>(new String[]{
            "Date", "Strategy (Tag)", "Day of Week", "Emotional State", "Plan Adherence", "Setup Quality", "Common Mistake"
        });
        this.mistakeComboBox = new JComboBox<>(new String[]{"All Mistakes"});
        this.checklistComboBox = new JComboBox<>();
        JPanel filterPanel = createFilterPanel();

        DefaultMutableTreeNode root = new DefaultMutableTreeNode("History");
        treeModel = new DefaultTreeModel(root);
        historyTree = new JTree(treeModel);
        historyTree.setOpaque(false);
        historyTree.setBackground(UIManager.getColor("Tree.background"));
        historyTree.setCellRenderer(new HistoryTreeCellRenderer());
        historyTree.setRootVisible(false);
        historyTree.setShowsRootHandles(true);
        historyTree.setToggleClickCount(1);
        historyTree.addTreeSelectionListener(e -> updateSummaryForSelection());

        JScrollPane scrollPane = new JScrollPane(historyTree);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel scrollWrapper = new JPanel(new BorderLayout());
        scrollWrapper.setOpaque(false);
        scrollWrapper.add(scrollPane, BorderLayout.CENTER);
        
        totalSummaryView = new TotalSummaryView();

        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setOpaque(false);
        centerContainer.add(filterPanel, BorderLayout.NORTH);
        centerContainer.add(scrollWrapper, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);
        add(totalSummaryView, BorderLayout.SOUTH);
        updateTradeHistory(null);
    }

    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        GridBagConstraints gbc = new GridBagConstraints();

        // --- Row 0: Tags Filter (spans all columns) ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 4; // Span across 4 columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 8, 0); // Add extra bottom margin
        
        JPanel tagFilterPanel = new JPanel(new BorderLayout(8, 0));
        tagFilterPanel.setOpaque(false);
        tagFilterPanel.add(new JLabel("Tags:"), BorderLayout.WEST);
        tagFilterPanel.add(tagComboBox, BorderLayout.CENTER);
        panel.add(tagFilterPanel, gbc);
        
        tagComboBox.addActionListener(e -> rebuildView());

        // --- Reset constraints for subsequent rows ---
        gbc.gridwidth = 1;
        gbc.insets = new Insets(2, 0, 2, 5); // Right margin for labels
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // --- Row 1: Group By and Direction ---
        gbc.gridy = 1;
        gbc.gridx = 0; panel.add(new JLabel("Group by:"), gbc);
        gbc.gridx = 2; panel.add(new JLabel("Direction:"), gbc);

        gbc.insets = new Insets(2, 0, 2, 10); // Right margin for components
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        
        gbc.gridx = 1; panel.add(groupByComboBox, gbc);
        groupByComboBox.addActionListener(e -> rebuildView());
        
        gbc.gridx = 3; panel.add(directionComboBox, gbc);
        directionComboBox.addActionListener(e -> rebuildView());

        // --- Row 2: Outcome and Mistake ---
        gbc.gridy = 2;
        gbc.insets = new Insets(2, 0, 2, 5);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        
        gbc.gridx = 0; panel.add(new JLabel("Outcome:"), gbc);
        gbc.gridx = 2; panel.add(new JLabel("Mistake:"), gbc);

        gbc.insets = new Insets(2, 0, 2, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;

        gbc.gridx = 1; panel.add(outcomeComboBox, gbc);
        outcomeComboBox.addActionListener(e -> rebuildView());

        gbc.gridx = 3; panel.add(mistakeComboBox, gbc);
        mistakeComboBox.addActionListener(e -> rebuildView());

        // --- Row 3: Checklist ---
        gbc.gridy = 3;
        gbc.insets = new Insets(2, 0, 2, 5);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.gridwidth = 1;
        
        gbc.gridx = 0; panel.add(new JLabel("Checklist:"), gbc);

        gbc.insets = new Insets(2, 0, 2, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.5;
        gbc.gridwidth = 3;
        gbc.gridx = 1; panel.add(checklistComboBox, gbc);
        checklistComboBox.addActionListener(e -> rebuildView());

        return panel;
    }

    public List<Trade> getAllTrades() {
        return this.allTrades;
    }

    public JTree getHistoryTree() {
        return historyTree;
    }

    private void expandAllNodes() {
        for (int i = 0; i < historyTree.getRowCount(); i++) {
            historyTree.expandRow(i);
        }
    }

    public void updateTradeHistory(List<Trade> trades) {
        this.allTrades = (trades != null) ? new ArrayList<>(trades) : new ArrayList<>();
        populateMistakeFilter();
        populateTagFilter();
        populateChecklistFilter();
        rebuildView();
    }
    
    private void populateTagFilter() {
        Object selected = tagComboBox.getSelectedItem();
    
        Set<String> uniqueTags = new HashSet<>();
        for (Trade trade : allTrades) {
            if (trade.tags() != null) {
                uniqueTags.addAll(trade.tags());
            }
        }
        
        List<String> sortedTags = new ArrayList<>(uniqueTags);
        Collections.sort(sortedTags);
    
        tagComboBox.removeAllItems();
        tagComboBox.addItem("All Tags");
        for (String tag : sortedTags) {
            tagComboBox.addItem(tag);
        }
    
        tagComboBox.setSelectedItem(selected);
    }

    private void populateMistakeFilter() {
        Object selected = mistakeComboBox.getSelectedItem();

        Set<String> uniqueMistakes = new HashSet<>();
        for (Trade trade : allTrades) {
            if (trade.identifiedMistakes() != null) {
                uniqueMistakes.addAll(trade.identifiedMistakes());
            }
        }
        
        List<String> sortedMistakes = new ArrayList<>(uniqueMistakes);
        Collections.sort(sortedMistakes);

        mistakeComboBox.removeAllItems();
        mistakeComboBox.addItem("All Mistakes");
        for (String mistake : sortedMistakes) {
            mistakeComboBox.addItem(mistake);
        }

        mistakeComboBox.setSelectedItem(selected);
    }
    
    private void populateChecklistFilter() {
        Object selected = checklistComboBox.getSelectedItem();

        // Find all unique checklist IDs used in the trades
        Set<UUID> usedChecklistIds = allTrades.stream()
            .map(Trade::checklistId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // Get all available checklists from the manager for lookup
        Map<UUID, Checklist> availableChecklists = ChecklistManager.getInstance().getChecklists().stream()
            .collect(Collectors.toMap(Checklist::id, Function.identity()));
        
        List<Checklist> usedAndAvailable = usedChecklistIds.stream()
            .map(availableChecklists::get) // Look up the full Checklist object
            .filter(Objects::nonNull) // Filter out any that might have been deleted
            .sorted(Comparator.comparing(Checklist::name))
            .collect(Collectors.toList());

        checklistComboBox.removeAllItems();
        checklistComboBox.addItem("All");
        checklistComboBox.addItem("With Checklist");
        checklistComboBox.addItem("Without Checklist");
        
        // Add the specific, used checklists to the dropdown
        if (!usedAndAvailable.isEmpty()) {
            checklistComboBox.addItem(new JSeparator()); // This will not render but acts as a placeholder
            for (Checklist checklist : usedAndAvailable) {
                checklistComboBox.addItem(checklist);
            }
        }

        checklistComboBox.setSelectedItem(selected);
    }

    private void rebuildView() {
        String selectedTag = (String) tagComboBox.getSelectedItem();
        String selectedDirection = (String) directionComboBox.getSelectedItem();
        String selectedOutcome = (String) outcomeComboBox.getSelectedItem();
        String selectedGrouping = (String) groupByComboBox.getSelectedItem();
        String selectedMistake = (String) mistakeComboBox.getSelectedItem();
        Object selectedChecklist = checklistComboBox.getSelectedItem();

        List<Trade> filteredTrades = allTrades.stream()
            .filter(trade -> filterByTags(trade, selectedTag))
            .filter(trade -> filterByDirection(trade, selectedDirection))
            .filter(trade -> filterByOutcome(trade, selectedOutcome))
            .filter(trade -> filterByMistake(trade, selectedMistake))
            .filter(trade -> filterByChecklist(trade, selectedChecklist))
            .collect(Collectors.toList());
        
        if ("Strategy (Tag)".equals(selectedGrouping)) {
            buildTreeByStrategy(filteredTrades);
        } else if ("Day of Week".equals(selectedGrouping)) {
            buildTreeByDayOfWeek(filteredTrades);
        } else if ("Emotional State".equals(selectedGrouping)) {
            buildTreeByEmotionalState(filteredTrades);
        } else if ("Plan Adherence".equals(selectedGrouping)) {
            buildTreeByPlanAdherence(filteredTrades);
        } else if ("Setup Quality".equals(selectedGrouping)) {
            buildTreeBySetupQuality(filteredTrades);
        } else if ("Common Mistake".equals(selectedGrouping)) {
            buildTreeByMistake(filteredTrades);
        } else { // Default to "Date"
            buildTreeByDate(filteredTrades);
        }

        treeModel.reload();
        expandAllNodes();
        updateSummaryForSelection();

        firePropertyChange("filteredTradesChanged", null, filteredTrades);
    }

    private boolean filterByTags(Trade trade, String selectedTag) {
        if (selectedTag == null || "All Tags".equals(selectedTag)) {
            return true;
        }
        if (trade.tags() == null || trade.tags().isEmpty()) {
            return false;
        }
        return trade.tags().contains(selectedTag);
    }
    
    private boolean filterByDirection(Trade trade, String selection) {
        if ("Long".equals(selection)) return trade.direction() == TradeDirection.LONG;
        if ("Short".equals(selection)) return trade.direction() == TradeDirection.SHORT;
        return true; // "All"
    }

    private boolean filterByOutcome(Trade trade, String selection) {
        if ("Wins".equals(selection)) return trade.profitAndLoss().signum() > 0;
        if ("Losses".equals(selection)) return trade.profitAndLoss().signum() < 0;
        return true; // "All"
    }
    
    private boolean filterByChecklist(Trade trade, Object selection) {
        if (selection == null || "All".equals(selection)) {
            return true;
        }
        if (selection instanceof String) {
            String selStr = (String) selection;
            if ("With Checklist".equals(selStr)) {
                return trade.checklistId() != null;
            }
            if ("Without Checklist".equals(selStr)) {
                return trade.checklistId() == null;
            }
        }
        if (selection instanceof Checklist) {
            Checklist selectedChecklist = (Checklist) selection;
            return selectedChecklist.id().equals(trade.checklistId());
        }
        return true; // Fallback for separators or unexpected items
    }

    private boolean filterByMistake(Trade trade, String selection) {
        if (selection == null || "All Mistakes".equals(selection)) {
            return true;
        }
        if (trade.identifiedMistakes() == null || trade.identifiedMistakes().isEmpty()) {
            return false;
        }
        return trade.identifiedMistakes().contains(selection);
    }
    
    private void buildTreeByDate(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades == null || trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }

        trades.sort(Comparator.comparing(Trade::exitTime).reversed());
        Map<Integer, Map<YearMonth, Map<LocalDate, List<Trade>>>> groupedTrades = trades.stream()
            .collect(Collectors.groupingBy(
                trade -> trade.exitTime().atZone(ZoneOffset.UTC).getYear(),
                Collectors.groupingBy(
                    trade -> YearMonth.from(trade.exitTime().atZone(ZoneOffset.UTC)),
                    Collectors.groupingBy(
                        trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate()
                    )
                )
            ));

        groupedTrades.keySet().stream().sorted(Comparator.reverseOrder()).forEach(year -> {
            DefaultMutableTreeNode yearNode = new DefaultMutableTreeNode(new YearNode(year));
            root.add(yearNode);
            Map<YearMonth, Map<LocalDate, List<Trade>>> months = groupedTrades.get(year);
            months.keySet().stream().sorted(Comparator.reverseOrder()).forEach(month -> {
                DefaultMutableTreeNode monthNode = new DefaultMutableTreeNode(new MonthNode(month));
                yearNode.add(monthNode);
                Map<LocalDate, List<Trade>> days = months.get(month);
                days.keySet().stream().sorted(Comparator.reverseOrder()).forEach(day -> {
                    DefaultMutableTreeNode dayNode = new DefaultMutableTreeNode(new DateNode(day));
                    monthNode.add(dayNode);
                    List<Trade> dayTrades = days.get(day);
                    dayTrades.forEach(trade -> {
                        DefaultMutableTreeNode tradeNode = new DefaultMutableTreeNode(trade);
                        dayNode.add(tradeNode);
                    });
                });
            });
        });
    }

    private void buildTreeByStrategy(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades == null || trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }

        Map<String, List<Trade>> tradesByTag = new TreeMap<>();
        for (Trade trade : trades) {
            if (trade.tags() != null && !trade.tags().isEmpty()) {
                for (String tag : trade.tags()) {
                    tradesByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(trade);
                }
            } else {
                tradesByTag.computeIfAbsent("Untagged", k -> new ArrayList<>()).add(trade);
            }
        }

        for (Map.Entry<String, List<Trade>> entry : tradesByTag.entrySet()) {
            List<Trade> tagTrades = entry.getValue();
            BigDecimal totalPnl = tagTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
            long winCount = tagTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
            double winRate = (tagTrades.size() > 0) ? (double) winCount / tagTrades.size() : 0.0;

            StrategyNode strategyNodeData = new StrategyNode(entry.getKey(), tagTrades.size(), totalPnl, winRate * 100);
            DefaultMutableTreeNode strategyNode = new DefaultMutableTreeNode(strategyNodeData);
            root.add(strategyNode);

            tagTrades.stream()
                .sorted(Comparator.comparing(Trade::exitTime).reversed())
                .forEach(trade -> strategyNode.add(new DefaultMutableTreeNode(trade)));
        }
    }

    private void buildTreeByDayOfWeek(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades == null || trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }

        Map<DayOfWeek, List<Trade>> tradesByDay = trades.stream()
            .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).getDayOfWeek()));

        for (DayOfWeek day : DayOfWeek.values()) { // Iterate in enum order
            if (tradesByDay.containsKey(day)) {
                List<Trade> dayTrades = tradesByDay.get(day);
                BigDecimal totalPnl = dayTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
                long winCount = dayTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
                double winRate = (dayTrades.size() > 0) ? (double) winCount / dayTrades.size() : 0.0;
                
                DayOfWeekNode dayNodeData = new DayOfWeekNode(day, dayTrades.size(), totalPnl, winRate * 100);
                DefaultMutableTreeNode dayNode = new DefaultMutableTreeNode(dayNodeData);
                root.add(dayNode);

                dayTrades.stream()
                    .sorted(Comparator.comparing(Trade::exitTime).reversed())
                    .forEach(trade -> dayNode.add(new DefaultMutableTreeNode(trade)));
            }
        }
    }

    private void buildTreeByEmotionalState(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }
        Map<EmotionalState, List<Trade>> tradesByState = trades.stream()
            .collect(Collectors.groupingBy(Trade::emotionalState));

        for (EmotionalState state : EmotionalState.values()) {
            if (tradesByState.containsKey(state)) {
                List<Trade> stateTrades = tradesByState.get(state);
                BigDecimal totalPnl = stateTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
                long winCount = stateTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
                double winRate = (stateTrades.size() > 0) ? (double) winCount / stateTrades.size() : 0.0;
                
                EmotionalStateNode stateNodeData = new EmotionalStateNode(state, stateTrades.size(), totalPnl, winRate * 100);
                DefaultMutableTreeNode stateNode = new DefaultMutableTreeNode(stateNodeData);
                root.add(stateNode);
                stateTrades.stream()
                    .sorted(Comparator.comparing(Trade::exitTime).reversed())
                    .forEach(trade -> stateNode.add(new DefaultMutableTreeNode(trade)));
            }
        }
    }

    private void buildTreeByPlanAdherence(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }
        Map<PlanAdherence, List<Trade>> tradesByAdherence = trades.stream()
            .collect(Collectors.groupingBy(Trade::planAdherence));

        for (PlanAdherence adherence : PlanAdherence.values()) {
            if (tradesByAdherence.containsKey(adherence)) {
                List<Trade> adherenceTrades = tradesByAdherence.get(adherence);
                BigDecimal totalPnl = adherenceTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
                long winCount = adherenceTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
                double winRate = (adherenceTrades.size() > 0) ? (double) winCount / adherenceTrades.size() : 0.0;
                
                PlanAdherenceNode adherenceNodeData = new PlanAdherenceNode(adherence, adherenceTrades.size(), totalPnl, winRate * 100);
                DefaultMutableTreeNode adherenceNode = new DefaultMutableTreeNode(adherenceNodeData);
                root.add(adherenceNode);
                adherenceTrades.stream()
                    .sorted(Comparator.comparing(Trade::exitTime).reversed())
                    .forEach(trade -> adherenceNode.add(new DefaultMutableTreeNode(trade)));
            }
        }
    }

    private void buildTreeBySetupQuality(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }
        Map<SetupQuality, List<Trade>> tradesByQuality = trades.stream()
            .collect(Collectors.groupingBy(trade -> trade.setupQuality() != null ? trade.setupQuality() : SetupQuality.NOT_RATED));

        for (SetupQuality quality : SetupQuality.values()) { // Iterate in enum order for consistent display
            if (tradesByQuality.containsKey(quality)) {
                List<Trade> qualityTrades = tradesByQuality.get(quality);
                BigDecimal totalPnl = qualityTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
                long winCount = qualityTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
                double winRate = (qualityTrades.size() > 0) ? (double) winCount / qualityTrades.size() : 0.0;
                
                SetupQualityNode qualityNodeData = new SetupQualityNode(quality, qualityTrades.size(), totalPnl, winRate * 100);
                DefaultMutableTreeNode qualityNode = new DefaultMutableTreeNode(qualityNodeData);
                root.add(qualityNode);
                qualityTrades.stream()
                    .sorted(Comparator.comparing(Trade::exitTime).reversed())
                    .forEach(trade -> qualityNode.add(new DefaultMutableTreeNode(trade)));
            }
        }
    }

    private void buildTreeByMistake(List<Trade> trades) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        if (trades.isEmpty()) {
            root.add(new DefaultMutableTreeNode("No trades match the current filters."));
            return;
        }
        Map<String, List<Trade>> tradesByMistake = new TreeMap<>();
        for (Trade trade : trades) {
            if (trade.identifiedMistakes() != null && !trade.identifiedMistakes().isEmpty()) {
                for (String mistake : trade.identifiedMistakes()) {
                    tradesByMistake.computeIfAbsent(mistake, k -> new ArrayList<>()).add(trade);
                }
            }
        }

        for (Map.Entry<String, List<Trade>> entry : tradesByMistake.entrySet()) {
            List<Trade> mistakeTrades = entry.getValue();
            BigDecimal totalPnl = mistakeTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
            long winCount = mistakeTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
            double winRate = (mistakeTrades.size() > 0) ? (double) winCount / mistakeTrades.size() : 0.0;

            MistakeNode mistakeNodeData = new MistakeNode(entry.getKey(), mistakeTrades.size(), totalPnl, winRate * 100);
            DefaultMutableTreeNode mistakeNode = new DefaultMutableTreeNode(mistakeNodeData);
            root.add(mistakeNode);
            mistakeTrades.stream()
                .sorted(Comparator.comparing(Trade::exitTime).reversed())
                .forEach(trade -> mistakeNode.add(new DefaultMutableTreeNode(trade)));
        }
    }

    private void updateSummaryForSelection() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) historyTree.getLastSelectedPathComponent();
        List<Trade> tradesForSummary;

        if (selectedNode == null) {
            tradesForSummary = getTradesFromNode((DefaultMutableTreeNode) treeModel.getRoot());
        } else {
            if (selectedNode.isLeaf() && selectedNode.getUserObject() instanceof Trade) {
                selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
            }
            tradesForSummary = getTradesFromNode(selectedNode);
        }

        JournalAnalysisService.OverallStats stats = analysisService.analyzeOverallPerformance(tradesForSummary, new BigDecimal("100000"));
        totalSummaryView.updateStats(stats);
    }

    private List<Trade> getTradesFromNode(DefaultMutableTreeNode node) {
        List<Trade> trades = new ArrayList<>();
        if (node == null) {
            return trades;
        }

        Object userObject = node.getUserObject();
        if (userObject instanceof Trade) {
            trades.add((Trade) userObject);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            trades.addAll(getTradesFromNode(childNode));
        }
        return trades;
    }
}