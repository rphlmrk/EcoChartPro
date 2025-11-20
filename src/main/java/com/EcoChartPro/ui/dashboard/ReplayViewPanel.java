package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class ReplayViewPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(ReplayViewPanel.class);

    private final ComprehensiveReportPanel reportView;

    public ReplayViewPanel() {
        this.reportView = new ComprehensiveReportPanel();
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        add(createHeaderPanel(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(this.reportView);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        // [FIX] Increased unit increment
        scrollPane.getVerticalScrollBar().setUnitIncrement(40);
        scrollPane.getVerticalScrollBar().setUI(new com.formdev.flatlaf.ui.FlatScrollBarUI());

        add(scrollPane, BorderLayout.CENTER);
    }

    public ComprehensiveReportPanel getReportPanel() {
        return reportView;
    }

    public void updateData(ReplaySessionState state) {
        if (state == null || state.symbolStates() == null || state.symbolStates().isEmpty()) {
            return;
        }

        List<Trade> allTradesInSession = state.symbolStates().values().stream()
                .flatMap(symbolState -> {
                    if (symbolState.tradeHistory() != null) {
                        return symbolState.tradeHistory().stream();
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        if (allTradesInSession.isEmpty()) {
            return;
        }

        JournalAnalysisService service = new JournalAnalysisService();
        BigDecimal totalPnl = allTradesInSession.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = state.accountBalance().subtract(totalPnl);

        JournalAnalysisService.OverallStats stats = service.analyzeOverallPerformance(allTradesInSession,
                initialBalance);
        reportView.updateData(stats, service, state);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Replay Mode");
        title.setFont(UIManager.getFont("app.font.heading"));
        title.setForeground(UIManager.getColor("Label.foreground"));
        headerPanel.add(title, BorderLayout.CENTER);

        return headerPanel;
    }
}