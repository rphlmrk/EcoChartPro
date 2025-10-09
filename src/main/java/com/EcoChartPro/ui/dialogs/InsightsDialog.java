package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.ui.Analysis.ComparativeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.JournalViewPanel;
import com.EcoChartPro.ui.Analysis.MistakeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.TradeExplorerPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.utils.report.HtmlReportGenerator;
import com.EcoChartPro.utils.report.PdfReportGenerator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * A comprehensive dialog that provides deep insights into trading performance.
 */
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

    private ReplaySessionState currentSessionState; // Store state for exporting

    public InsightsDialog(Frame owner) {
        super(owner, "Performance Insights & Journal", true);
        setSize(1200, 800);
        setLocationRelativeTo(owner);

        mainPanel = new JPanel(cardLayout);
        loadingPanel = new JPanel(new GridBagLayout());
        JLabel loadingLabel = new JLabel("Analyzing Performance Data...");
        loadingLabel.setFont(UIManager.getFont("app.font.heading"));
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);

        // [FIX] Use a JLayeredPane to create a floating button over the content.
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

        // --- Create Export Button for the floating layer ---
        JButton exportButton = new JButton("Export", UITheme.getIcon(UITheme.Icons.EXPORT, 16, 16));
        exportButton.setToolTipText("Export full report to a file");
        exportButton.setOpaque(false);
        exportButton.setContentAreaFilled(false);
        exportButton.setBorderPainted(false);
        exportButton.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        exportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportButton.setForeground(UIManager.getColor("Label.foreground"));
        exportButton.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD));

        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem exportHtmlItem = new JMenuItem("Export to HTML...");
        exportHtmlItem.addActionListener(e -> exportReport("html"));
        JMenuItem exportPdfItem = new JMenuItem("Export to PDF...");
        exportPdfItem.addActionListener(e -> exportReport("pdf"));
        exportMenu.add(exportHtmlItem);
        exportMenu.add(exportPdfItem);
        
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));
        
        // Add components to the layered pane
        layeredContentPane.add(tabbedPane, JLayeredPane.DEFAULT_LAYER);
        layeredContentPane.add(exportButton, JLayeredPane.PALETTE_LAYER);

        // Add a listener to manually manage the layout of the layered components
        layeredContentPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Make the tabbed pane fill the entire space
                tabbedPane.setBounds(0, 0, layeredContentPane.getWidth(), layeredContentPane.getHeight());

                // Position the export button in the top-right corner, inside the tab area
                Dimension buttonSize = exportButton.getPreferredSize();
                int x = layeredContentPane.getWidth() - buttonSize.width - 20;
                int y = 6; // Position it vertically within the tab bar area
                exportButton.setBounds(x, y, buttonSize.width, buttonSize.height);
            }
        });

        mainPanel.add(loadingPanel, "loading");
        mainPanel.add(layeredContentPane, "content"); // Add the new layered pane
        
        setContentPane(mainPanel);
    }
    
    private record AnalysisResult(
        List<CoachingInsight> insights,
        Map<String, MistakeStats> mistakeStats
    ) {}

    public void loadSessionData(ReplaySessionState state) {
        this.currentSessionState = state; // Store state for export functionality
        cardLayout.show(mainPanel, "loading");
        
        SwingWorker<AnalysisResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                // Perform all heavy analysis in the background
                CoachingService coachingService = CoachingService.getInstance();
                com.EcoChartPro.core.journal.JournalAnalysisService journalService = new com.EcoChartPro.core.journal.JournalAnalysisService();
                
                int optimalTrades = com.EcoChartPro.core.gamification.GamificationService.getInstance().getOptimalTradeCount();
                List<Integer> peakHours = com.EcoChartPro.core.gamification.GamificationService.getInstance().getPeakPerformanceHours();
                
                List<CoachingInsight> insights = coachingService.analyze(state.tradeHistory(), optimalTrades, peakHours);
                Map<String, MistakeStats> mistakeStats = journalService.analyzeMistakes(state.tradeHistory());
                
                return new AnalysisResult(insights, mistakeStats);
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    
                    insightsContainer.removeAll();
                    if (result.insights().isEmpty()) {
                        insightsContainer.add(new CoachingInsightRenderer(new CoachingInsight("NO_INSIGHTS", "Great Work!", "No critical performance issues were detected in this session. Keep up the disciplined trading.", com.EcoChartPro.core.coaching.InsightSeverity.LOW, com.EcoChartPro.core.coaching.InsightType.SEQUENCE_BASED)));
                    } else {
                        for (CoachingInsight insight : result.insights()) {
                            insightsContainer.add(new CoachingInsightRenderer(insight));
                            insightsContainer.add(Box.createVerticalStrut(10));
                        }
                    }
                    insightsContainer.revalidate();
                    insightsContainer.repaint();

                    performancePanel.loadSessionData(state);
                    mistakePanel.updateData(result.mistakeStats());
                    journalPanel.loadSessionData(state.tradeHistory());
                    comparativePanel.loadData(state.tradeHistory());
                    explorerPanel.loadData(state.tradeHistory());

                    cardLayout.show(mainPanel, "content");
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    cardLayout.show(mainPanel, "loading");
                    ((JLabel)loadingPanel.getComponent(0)).setText("Error loading analysis.");
                }
            }
        };
        
        worker.execute();
    }

    private void exportReport(String format) {
        if (this.currentSessionState == null || this.currentSessionState.tradeHistory().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session data available to export.", "Export Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        boolean isHtml = "html".equalsIgnoreCase(format);
        String fileExt = isHtml ? "html" : "pdf";
        String fileDesc = isHtml ? "HTML Files (*.html)" : "PDF Documents (*.pdf)";

        fileChooser.setDialogTitle("Save " + format.toUpperCase() + " Report");
        fileChooser.setFileFilter(new FileNameExtensionFilter(fileDesc, fileExt));

        String defaultFilename = String.format("EcoChartPro_Report_%s_%s.%s",
                this.currentSessionState.dataSourceSymbol(), LocalDate.now().toString(), fileExt);
        fileChooser.setSelectedFile(new File(defaultFilename));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith("." + fileExt)) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + "." + fileExt);
            }
            final File finalFile = fileToSave;

            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    if (isHtml) {
                        HtmlReportGenerator.generate(currentSessionState, finalFile);
                    } else {
                        PdfReportGenerator.generate(currentSessionState, finalFile);
                    }
                    return null;
                }
                @Override protected void done() {
                    handleExportCompletion(this, finalFile);
                }
            }.execute();
        }
    }
    
    private void handleExportCompletion(SwingWorker<Void, Void> worker, File outputFile) {
        try {
            worker.get(); // check for exceptions
            int choice = JOptionPane.showConfirmDialog(this,
                    "Report successfully exported to:\n" + outputFile.getAbsolutePath() + "\n\nDo you want to open it now?",
                    "Export Successful",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(outputFile);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "An error occurred while exporting the report:\n" + ex.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}