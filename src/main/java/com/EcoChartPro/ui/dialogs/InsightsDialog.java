package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.Analysis.ComparativeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.JournalViewPanel;
import com.EcoChartPro.ui.Analysis.MistakeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.TradeExplorerPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.utils.report.HtmlReportGenerator;
import com.EcoChartPro.utils.report.PdfReportGenerator;
import com.EcoChartPro.utils.report.ReportDataAggregator;
import com.EcoChartPro.utils.report.ReportDataAggregator.ReportData;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class InsightsDialog extends JDialog {

    private final JTabbedPane tabbedPane;
    private final PerformanceAnalyticsPanel performancePanel;
    private final MistakeAnalysisPanel mistakePanel;
    private final JournalViewPanel journalPanel;
    private final ComparativeAnalysisPanel comparativePanel;
    private final TradeExplorerPanel explorerPanel;
    private final JPanel insightsContainer;
    
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel;
    private final JPanel loadingPanel;
    private final JLabel loadingLabel;

    private ReplaySessionState currentSessionState; 

    public InsightsDialog(Frame owner) {
        super(owner, "Performance Insights & Journal", true);
        setSize(1200, 800);
        setLocationRelativeTo(owner);

        mainPanel = new JPanel(cardLayout);
        loadingPanel = new JPanel(new GridBagLayout());
        loadingLabel = new JLabel("Analyzing Performance Data...");
        loadingLabel.setFont(UIManager.getFont("app.font.heading"));
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);

        JLayeredPane layeredContentPane = new JLayeredPane();
        
        insightsContainer = new JPanel();
        insightsContainer.setLayout(new BoxLayout(insightsContainer, BoxLayout.Y_AXIS));
        insightsContainer.setOpaque(false);
        JScrollPane insightsScrollPane = new JScrollPane(insightsContainer);
        insightsScrollPane.setOpaque(false);
        insightsScrollPane.getViewport().setOpaque(false);
        insightsScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        insightsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        tabbedPane = new JTabbedPane();
        performancePanel = new PerformanceAnalyticsPanel();
        mistakePanel = new MistakeAnalysisPanel();
        journalPanel = new JournalViewPanel();
        comparativePanel = new ComparativeAnalysisPanel();
        explorerPanel = new TradeExplorerPanel();

        tabbedPane.addTab("Coaching Insights", insightsScrollPane);
        tabbedPane.addTab("Performance Analytics", performancePanel);
        tabbedPane.addTab("Trade Explorer", explorerPanel);
        tabbedPane.addTab("Trading Journal", journalPanel);
        tabbedPane.addTab("Mistake Analysis", mistakePanel);
        tabbedPane.addTab("Comparative Analysis", comparativePanel);

        JButton exportButton = new JButton("Export", UITheme.getIcon(UITheme.Icons.EXPORT, 16, 16));
        exportButton.setOpaque(false);
        exportButton.setContentAreaFilled(false);
        exportButton.setBorderPainted(false);
        exportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportButton.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD));

        JPopupMenu exportMenu = new JPopupMenu();
        exportMenu.add(new JMenuItem("Export to HTML")).addActionListener(e -> exportReport("html"));
        exportMenu.add(new JMenuItem("Export to PDF")).addActionListener(e -> exportReport("pdf"));
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));
        
        layeredContentPane.add(tabbedPane, JLayeredPane.DEFAULT_LAYER);
        layeredContentPane.add(exportButton, JLayeredPane.PALETTE_LAYER);

        layeredContentPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tabbedPane.setBounds(0, 0, layeredContentPane.getWidth(), layeredContentPane.getHeight());
                Dimension size = exportButton.getPreferredSize();
                exportButton.setBounds(layeredContentPane.getWidth() - size.width - 20, 6, size.width, size.height);
            }
        });

        mainPanel.add(loadingPanel, "loading");
        mainPanel.add(layeredContentPane, "content"); 
        setContentPane(mainPanel);
    }

    public void loadSessionData(ReplaySessionState state) {
        this.currentSessionState = state;
        loadingLabel.setText("Analyzing Performance Data...");
        cardLayout.show(mainPanel, "loading");
        
        SwingWorker<ReportData, Void> worker = new SwingWorker<>() {
            @Override
            protected ReportData doInBackground() throws Exception {
                return ReportDataAggregator.prepareReportData(state);
            }

            @Override
            protected void done() {
                try {
                    ReportData result = get(); 
                    System.out.println("DEBUG: Background Analysis Complete.");

                    List<Trade> allTrades = result.stats().trades();
                    
                    System.out.println("DEBUG: Building Insights UI...");
                    insightsContainer.removeAll();
                    if (result.insights().isEmpty()) {
                        insightsContainer.add(new CoachingInsightRenderer(new com.EcoChartPro.core.coaching.CoachingInsight("NO_INSIGHTS", "Great Work!", "No critical issues found.", com.EcoChartPro.core.coaching.InsightSeverity.LOW, com.EcoChartPro.core.coaching.InsightType.SEQUENCE_BASED)));
                    } else {
                        for (com.EcoChartPro.core.coaching.CoachingInsight insight : result.insights()) {
                            insightsContainer.add(new CoachingInsightRenderer(insight));
                            insightsContainer.add(Box.createVerticalStrut(10));
                        }
                    }
                    insightsContainer.revalidate();

                    System.out.println("DEBUG: Updating Panels...");
                    performancePanel.loadSessionData(state);
                    mistakePanel.updateData(result.mistakeAnalysis());
                    journalPanel.loadSessionData(allTrades);
                    comparativePanel.loadData(allTrades);
                    explorerPanel.loadData(allTrades);

                    System.out.println("DEBUG: Switching View to Content.");
                    cardLayout.show(mainPanel, "content");

                } catch (Exception e) {
                    e.printStackTrace();
                    loadingLabel.setText("Error: " + e.getMessage());
                    JOptionPane.showMessageDialog(InsightsDialog.this, "Error displaying data: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void exportReport(String format) {
        if (currentSessionState == null) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Report." + format));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    if ("html".equals(format)) HtmlReportGenerator.generate(currentSessionState, fc.getSelectedFile());
                    else PdfReportGenerator.generate(currentSessionState, fc.getSelectedFile());
                    return null;
                }
                @Override protected void done() {
                    try { get(); JOptionPane.showMessageDialog(InsightsDialog.this, "Export Complete"); }
                    catch (Exception e) { JOptionPane.showMessageDialog(InsightsDialog.this, "Export Failed: " + e.getMessage()); }
                }
            }.execute();
        }
    }
}