package com.EcoChartPro.ui.Analysis;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.CoachingInsightRenderer;
import com.EcoChartPro.ui.dialogs.PerformanceAnalyticsPanel;
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

/**
 * A comprehensive main view panel that provides deep insights into trading performance.
 * Replaces the popup InsightsDialog.
 */
public class AnalysisMainPanel extends JPanel {

    private final JTabbedPane tabbedPane;
    private final PerformanceAnalyticsPanel performancePanel;
    private final MistakeAnalysisPanel mistakePanel;
    private final JournalViewPanel journalPanel;
    private final ComparativeAnalysisPanel comparativePanel;
    private final TradeExplorerPanel explorerPanel;
    private final JPanel insightsContainer;
    
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentCardPanel; // Holds Loading vs Content
    private final JPanel loadingPanel;
    private final JLabel loadingLabel;

    private ReplaySessionState currentSessionState; 

    // --- [PHASE 1] Caching State Fields ---
    private int lastAnalyzedTradeCount = -1;
    private String lastAnalyzedSymbol = "";
    
    // Note: cachedReportData could be stored if we wanted to prevent re-rendering, 
    // but just preventing re-calculation via the trade count check is sufficient.

    public AnalysisMainPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        // --- 1. Card Layout for Loading State ---
        contentCardPanel = new JPanel(cardLayout);
        contentCardPanel.setOpaque(false);

        // --- 2. Loading Screen ---
        loadingPanel = new JPanel(new GridBagLayout());
        loadingPanel.setOpaque(false);
        loadingLabel = new JLabel("Select a session or wait for analysis...");
        loadingLabel.setFont(UIManager.getFont("app.font.heading"));
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);

        // --- 3. Main Content Layer (Tabs + Floating Button) ---
        JLayeredPane layeredContentPane = new JLayeredPane();
        
        // Coaching Insights Tab (Scrollable)
        insightsContainer = new JPanel();
        insightsContainer.setLayout(new BoxLayout(insightsContainer, BoxLayout.Y_AXIS));
        insightsContainer.setOpaque(false);
        JScrollPane insightsScrollPane = new JScrollPane(insightsContainer);
        insightsScrollPane.setOpaque(false);
        insightsScrollPane.getViewport().setOpaque(false);
        insightsScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        insightsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Initialize Sub-Panels
        tabbedPane = new JTabbedPane();
        performancePanel = new PerformanceAnalyticsPanel();
        mistakePanel = new MistakeAnalysisPanel();
        journalPanel = new JournalViewPanel();
        comparativePanel = new ComparativeAnalysisPanel();
        explorerPanel = new TradeExplorerPanel();

        // Add Tabs
        tabbedPane.addTab("Coaching Insights", insightsScrollPane);
        tabbedPane.addTab("Performance Analytics", performancePanel);
        tabbedPane.addTab("Trade Explorer", explorerPanel);
        tabbedPane.addTab("Trading Journal", journalPanel);
        tabbedPane.addTab("Mistake Analysis", mistakePanel);
        tabbedPane.addTab("Comparative Analysis", comparativePanel);

        // Floating Export Button
        JButton exportButton = new JButton("Export Report", UITheme.getIcon(UITheme.Icons.EXPORT, 16, 16));
        exportButton.setToolTipText("Export full report to HTML or PDF");
        exportButton.setOpaque(false);
        exportButton.setContentAreaFilled(false);
        exportButton.setBorderPainted(false);
        exportButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        exportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportButton.setForeground(UIManager.getColor("Label.foreground"));
        exportButton.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD));

        JPopupMenu exportMenu = new JPopupMenu();
        exportMenu.add(new JMenuItem("Export to HTML")).addActionListener(e -> exportReport("html"));
        exportMenu.add(new JMenuItem("Export to PDF")).addActionListener(e -> exportReport("pdf"));
        
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));
        
        // Add to Layered Pane
        layeredContentPane.add(tabbedPane, JLayeredPane.DEFAULT_LAYER);
        layeredContentPane.add(exportButton, JLayeredPane.PALETTE_LAYER);

        // Layout Logic for Floating Button
        layeredContentPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                tabbedPane.setBounds(0, 0, layeredContentPane.getWidth(), layeredContentPane.getHeight());
                Dimension size = exportButton.getPreferredSize();
                // Position top-right, aligned with tab bar
                int x = layeredContentPane.getWidth() - size.width - 10;
                int y = 2; 
                exportButton.setBounds(x, y, size.width, size.height);
            }
        });

        contentCardPanel.add(loadingPanel, "loading");
        contentCardPanel.add(layeredContentPane, "content"); 
        
        add(contentCardPanel, BorderLayout.CENTER);
        
        // Default state
        cardLayout.show(contentCardPanel, "loading");
    }

    /**
     * [NEW] Forces a re-calculation of the report data, invalidating the local trade-count cache.
     * Call this when trades are modified (e.g., editing notes) but the total count hasn't changed.
     */
    public void forceRefresh() {
        this.lastAnalyzedTradeCount = -1;
        this.lastAnalyzedSymbol = "";
        if (this.currentSessionState != null) {
            loadSessionData(this.currentSessionState);
        }
    }

    /**
     * Triggers the asynchronous loading of session data.
     * @param state The session state to analyze. If null, shows a placeholder message.
     */
    public void loadSessionData(ReplaySessionState state) {
        if (state == null) {
            loadingLabel.setText("No active session data to analyze.");
            cardLayout.show(contentCardPanel, "loading");
            lastAnalyzedTradeCount = -1;
            lastAnalyzedSymbol = "";
            return;
        }

        // --- [PHASE 1] Smart Caching Check ---
        // 1. Calculate current state fingerprint
        int currentTradeCount = 0;
        if (state.symbolStates() != null) {
            for (SymbolSessionState s : state.symbolStates().values()) {
                if (s.tradeHistory() != null) {
                    currentTradeCount += s.tradeHistory().size();
                }
            }
        }
        String currentSymbol = state.lastActiveSymbol() != null ? state.lastActiveSymbol() : "";

        // 2. Check if data has actually changed
        if (this.lastAnalyzedTradeCount == currentTradeCount && 
            this.lastAnalyzedSymbol.equals(currentSymbol)) {
            
            // Update reference just in case (for exports), but skip heavy calculation
            this.currentSessionState = state;
            
            // Ensure content is visible immediately
            if (!contentCardPanel.isVisible() || !contentCardPanel.isShowing()) {
                cardLayout.show(contentCardPanel, "content");
            }
            return; // EXIT EARLY
        }
        
        // 3. Capture values for the Worker callback
        final int finalTradeCount = currentTradeCount;
        final String finalSymbol = currentSymbol;

        this.currentSessionState = state;
        loadingLabel.setText("Analyzing Performance Data...");
        cardLayout.show(contentCardPanel, "loading");
        
        SwingWorker<ReportData, Void> worker = new SwingWorker<>() {
            @Override
            protected ReportData doInBackground() throws Exception {
                // Execute heavy analysis in background
                return ReportDataAggregator.prepareReportData(state);
            }

            @Override
            protected void done() {
                try {
                    ReportData result = get(); // Retrieves result or throws exception

                    // Update UI on EDT
                    try {
                        List<Trade> allTrades = result.stats().trades();
                        
                        // Populate Coaching Insights
                        insightsContainer.removeAll();
                        if (result.insights().isEmpty()) {
                            insightsContainer.add(new CoachingInsightRenderer(new CoachingInsight(
                                "NO_INSIGHTS", 
                                "Great Work!", 
                                "No critical performance issues were detected in this session. Keep up the disciplined trading.", 
                                com.EcoChartPro.core.coaching.InsightSeverity.LOW, 
                                com.EcoChartPro.core.coaching.InsightType.SEQUENCE_BASED
                            )));
                        } else {
                            for (CoachingInsight insight : result.insights()) {
                                insightsContainer.add(new CoachingInsightRenderer(insight));
                                insightsContainer.add(Box.createVerticalStrut(10));
                            }
                        }
                        insightsContainer.revalidate();
                        insightsContainer.repaint();

                        // Populate Sub-Panels
                        performancePanel.loadSessionData(state);
                        mistakePanel.updateData(result.mistakeAnalysis());
                        journalPanel.loadSessionData(allTrades);
                        comparativePanel.loadData(allTrades);
                        explorerPanel.loadData(allTrades);

                        // --- [PHASE 1] Update Cache State ---
                        lastAnalyzedTradeCount = finalTradeCount;
                        lastAnalyzedSymbol = finalSymbol;

                        // Switch View
                        cardLayout.show(contentCardPanel, "content");

                    } catch (Exception uiEx) {
                        uiEx.printStackTrace();
                        loadingLabel.setText("Error building analysis view.");
                        JOptionPane.showMessageDialog(AnalysisMainPanel.this, 
                            "Analysis completed, but an error occurred while displaying the results:\n" + uiEx.getMessage(),
                            "Display Error", JOptionPane.ERROR_MESSAGE);
                    }

                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    loadingLabel.setText("Analysis Failed.");
                    JOptionPane.showMessageDialog(AnalysisMainPanel.this, 
                        "Failed to generate report data.\nError: " + errorMsg, 
                        "Analysis Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }

    private void exportReport(String format) {
        if (this.currentSessionState == null) {
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
                this.currentSessionState.lastActiveSymbol() != null ? this.currentSessionState.lastActiveSymbol() : "Session", 
                LocalDate.now().toString(), fileExt);
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
                    try {
                        get();
                        int choice = JOptionPane.showConfirmDialog(AnalysisMainPanel.this,
                                "Report successfully exported to:\n" + finalFile.getAbsolutePath() + "\n\nDo you want to open it now?",
                                "Export Successful",
                                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (choice == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(finalFile);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(AnalysisMainPanel.this,
                                "An error occurred while exporting the report:\n" + ex.getMessage(),
                                "Export Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        }
    }
}