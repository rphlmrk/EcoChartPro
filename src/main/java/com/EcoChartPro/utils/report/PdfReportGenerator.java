package com.EcoChartPro.utils.report;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.EquityPoint;
import com.EcoChartPro.core.journal.JournalAnalysisService.OverallStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.PnlDistributionBin;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.DataSourceManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.awt.RenderingHints;

public class PdfReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(PdfReportGenerator.class);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_MONO = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0'%'");

    private PDDocument document;
    private PDPageContentStream contentStream;
    private float yPosition;
    private PDPage currentPage;

    public static void generate(ReplaySessionState state, File outputFile) throws IOException {
        new PdfReportGenerator().createReport(state, outputFile);
    }

    private void createReport(ReplaySessionState state, File outputFile) throws IOException {
        JournalAnalysisService service = new JournalAnalysisService();

        // [FIXED] Collect all trades from all symbols for a comprehensive report
        List<Trade> allTrades = new ArrayList<>();
        if (state.symbolStates() != null) {
            state.symbolStates().values().forEach(s -> {
                if (s.tradeHistory() != null) {
                    allTrades.addAll(s.tradeHistory());
                }
            });
        }
        
        BigDecimal totalPnl = allTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = state.accountBalance().subtract(totalPnl);
        OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);

        // --- Pre-calculate all necessary data ---
        List<PnlDistributionBin> pnlDistribution = service.getPnlDistribution(stats.trades(), 15);
        Map<String, MistakeStats> mistakeAnalysis = service.analyzeMistakes(stats.trades());
        
        GamificationService gs = GamificationService.getInstance();
        Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();
        List<CoachingInsight> insights = CoachingService.getInstance().analyze(stats.trades(), gs.getOptimalTradeCount(), gs.getPeakPerformanceHours(), sourceOpt);

        try (PDDocument doc = new PDDocument()) {
            this.document = doc;
            startNewPage();

            drawHeader(state, stats);
            drawKeyMetrics(stats);
            drawEquityCurveChart(stats);
            drawPerformanceAnalytics(service, stats.trades(), pnlDistribution);
            drawMistakeAnalysis(mistakeAnalysis);
            drawCoachingInsights(insights);
            drawTradeHistory(stats.trades());

            this.contentStream.close();
            document.save(outputFile);
            logger.info("PDF report generated successfully at {}", outputFile.getAbsolutePath());
        }
    }

    private void startNewPage() throws IOException {
        if (this.contentStream != null) {
            this.contentStream.close();
        }
        currentPage = new PDPage(PDRectangle.A4);
        document.addPage(currentPage);
        contentStream = new PDPageContentStream(document, currentPage);
        yPosition = currentPage.getMediaBox().getHeight() - 50;
    }

    private void drawHeader(ReplaySessionState state, OverallStats stats) throws IOException {
        String dateRange = "N/A";
        if (!stats.trades().isEmpty()) {
            Instant first = stats.trades().get(0).entryTime();
            Instant last = stats.trades().get(stats.trades().size() - 1).exitTime();
            dateRange = String.format("%s to %s", DATE_FORMATTER.format(first), DATE_FORMATTER.format(last));
        }

        drawText(FONT_BOLD, 18, 50, yPosition, "Trading Performance Report");
        yPosition -= 20;
        drawText(FONT_NORMAL, 12, 50, yPosition, String.format("Session Context: %s  |  Period: %s", state.lastActiveSymbol(), dateRange));
        yPosition -= 30;
    }

    private void drawKeyMetrics(OverallStats stats) throws IOException {
        drawSectionHeader("Key Performance Metrics");
        float x = 60;
        float yStart = yPosition;
        float colWidth = 250;

        drawMetric(x, yStart, "Total Net P&L:", PNL_FORMAT.format(stats.totalPnl()));
        drawMetric(x, yStart - 20, "Win Rate:", PERCENT_FORMAT.format(stats.winRate() * 100));
        drawMetric(x, yStart - 40, "Profit Factor:", DECIMAL_FORMAT.format(stats.profitFactor()));
        drawMetric(x + colWidth, yStart, "Avg. Risk/Reward:", DECIMAL_FORMAT.format(stats.avgRiskReward()));
        drawMetric(x + colWidth, yStart - 20, "Expectancy:", PNL_FORMAT.format(stats.expectancy()));
        drawMetric(x + colWidth, yStart - 40, "Total Trades:", String.valueOf(stats.totalTrades()));
        yPosition -= 70;
    }

    private void drawEquityCurveChart(OverallStats stats) throws IOException {
        drawSectionHeader("Equity Curve");
        int chartWidth = 500;
        int chartHeight = 250;

        if (stats.equityCurve().size() < 2) {
            drawText(FONT_NORMAL, 10, 60, yPosition, "Not enough data to draw chart.");
            yPosition -= 20;
            return;
        }

        BufferedImage chartImage = createEquityCurveImage(stats, chartWidth, chartHeight);
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, chartImage);
        contentStream.drawImage(pdImage, 50, yPosition - chartHeight, chartWidth, chartHeight);
        yPosition -= (chartHeight + 20);
    }
    
    private void drawPerformanceAnalytics(JournalAnalysisService service, List<Trade> trades, List<PnlDistributionBin> pnlDistribution) throws IOException {
        if (checkPageBreak(300)) startNewPage();
        drawSectionHeader("Performance Analytics");

        // P&L Distribution
        drawSubSectionHeader("P&L Distribution (# of Trades)");
        drawHistogram(pnlDistribution, 50, yPosition, 500, 150);
        yPosition -= 180;
    }

    private void drawMistakeAnalysis(Map<String, MistakeStats> mistakeAnalysis) throws IOException {
        if (mistakeAnalysis.isEmpty()) return;
        
        if (checkPageBreak(80)) startNewPage();
        drawSectionHeader("Mistake Analysis");

        float[] colWidths = {200, 70, 90, 90};
        String[] headers = {"Mistake", "Frequency", "Total P&L", "Avg P&L"};
        
        // Header
        float x = 50;
        for (int i = 0; i < headers.length; i++) {
            drawText(FONT_BOLD, 9, x + 5, yPosition - 10, headers[i]);
            x += colWidths[i];
        }
        yPosition -= 15;

        // Rows
        List<MistakeStats> sortedMistakes = mistakeAnalysis.values().stream()
            .sorted(Comparator.comparing(MistakeStats::totalPnl)).collect(Collectors.toList());

        for (MistakeStats mistake : sortedMistakes) {
            if (checkPageBreak(15)) {
                startNewPageWithHeader("Mistake Analysis (Continued)");
                x = 50;
                for (int i = 0; i < headers.length; i++) {
                    drawText(FONT_BOLD, 9, x + 5, yPosition - 10, headers[i]);
                    x += colWidths[i];
                }
                yPosition -= 15;
            }
            
            x = 50;
            String[] rowData = {
                mistake.mistakeName(),
                String.valueOf(mistake.frequency()),
                PNL_FORMAT.format(mistake.totalPnl()),
                PNL_FORMAT.format(mistake.averagePnl())
            };
            for (int i = 0; i < rowData.length; i++) {
                drawText(FONT_NORMAL, 9, x + 5, yPosition - 10, rowData[i]);
                x += colWidths[i];
            }
            yPosition -= 15;
        }
        yPosition -= 15;
    }

    private void drawCoachingInsights(List<CoachingInsight> insights) throws IOException {
        if (insights.isEmpty()) return;

        if (checkPageBreak(80)) startNewPage();
        drawSectionHeader("Coaching Insights");

        for (CoachingInsight insight : insights) {
            float requiredHeight = 20 + (getLines(insight.description(), FONT_NORMAL, 9, 480).size() * 10);
            if (checkPageBreak(requiredHeight)) startNewPageWithHeader("Coaching Insights (Continued)");

            drawText(FONT_BOLD, 10, 60, yPosition, "• " + insight.title());
            yPosition -= 12;

            List<String> lines = getLines(insight.description(), FONT_NORMAL, 9, 480);
            for (String line : lines) {
                drawText(FONT_NORMAL, 9, 70, yPosition, line);
                yPosition -= 11;
            }
            yPosition -= 8;
        }
        yPosition -= 15;
    }

    private void drawTradeHistory(List<Trade> trades) throws IOException {
        if (checkPageBreak(80)) startNewPageWithHeader("Trade History");
        else drawSectionHeader("Trade History");

        Map<YearMonth, List<Trade>> tradesByMonth = trades.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.exitTime().atZone(ZoneOffset.UTC)), TreeMap::new, Collectors.toList()));
        
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy");
        float[] colWidths = {30, 80, 50, 100, 100, 60, 70};
        String[] headers = {"#", "Symbol", "Side", "Entry Time", "Exit Time", "Duration", "P&L"};
        
        int globalTradeNum = 1;
        for(Map.Entry<YearMonth, List<Trade>> entry : tradesByMonth.entrySet()) {
            if (checkPageBreak(40)) startNewPageWithHeader("Trade History (Continued)");

            yPosition -= 15;
            drawText(FONT_BOLD, 11, 55, yPosition, entry.getKey().format(monthFormatter));
            yPosition -= 5;
            
            drawTable(entry.getValue(), headers, colWidths, globalTradeNum);
            globalTradeNum += entry.getValue().size();
        }
    }

    // --- Drawing Helpers ---
    
    private void drawSubSectionHeader(String title) throws IOException {
        drawText(FONT_BOLD, 11, 55, yPosition, title);
        yPosition -= 15;
    }

    private void drawHistogram(List<PnlDistributionBin> data, float x, float y, float width, float height) throws IOException {
        if (data.isEmpty()) return;

        int maxCount = data.stream().mapToInt(PnlDistributionBin::count).max().orElse(1);
        float barGap = 4;
        float barWidth = (width - (data.size() - 1) * barGap) / data.size();

        for (int i = 0; i < data.size(); i++) {
            PnlDistributionBin bin = data.get(i);
            float barHeight = (float) bin.count() / maxCount * (height - 20); // leave space for text
            float barX = x + i * (barWidth + barGap);
            float barY = y - height;

            if (bin.upperBound().signum() <= 0) contentStream.setStrokingColor(Color.RED);
            else if (bin.lowerBound().signum() >= 0) contentStream.setStrokingColor(new Color(0, 150, 0));
            else contentStream.setStrokingColor(Color.BLUE);
            contentStream.addRect(barX, barY, barWidth, barHeight);
            contentStream.stroke();

            drawText(FONT_NORMAL, 7, barX + 2, barY - 10, String.valueOf(bin.count()));
        }
    }

    private void drawSectionHeader(String title) throws IOException {
        contentStream.setStrokingColor(Color.LIGHT_GRAY);
        contentStream.moveTo(50, yPosition);
        contentStream.lineTo(545, yPosition);
        contentStream.stroke();
        yPosition -= 15;
        drawText(FONT_BOLD, 14, 50, yPosition, title);
        yPosition -= 20;
    }

    private void drawMetric(float x, float y, String label, String value) throws IOException {
        drawText(FONT_BOLD, 10, x, y, label);
        drawText(FONT_NORMAL, 10, x + 100, y, value);
    }

    private void drawText(PDType1Font font, float size, float x, float y, String text) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, size);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
    }
    
    private void drawTable(List<Trade> trades, String[] headers, float[] colWidths, int startNum) throws IOException {
        float rowHeight = 15;
        float cellMargin = 5;
        float x = 50;

        // Draw header
        for (int i = 0; i < headers.length; i++) {
            drawText(FONT_BOLD, 9, x + cellMargin, yPosition - 10, headers[i]);
            x += colWidths[i];
        }
        yPosition -= rowHeight;
        
        // Draw rows
        int tradeNum = startNum;
        for (Trade trade : trades) {
            if (checkPageBreak(rowHeight)) {
                 startNewPageWithHeader("Trade History (Continued)");
                x = 50;
                // Redraw header on new page
                for (int i = 0; i < headers.length; i++) {
                    drawText(FONT_BOLD, 9, x + cellMargin, yPosition - 10, headers[i]);
                    x += colWidths[i];
                }
                 yPosition -= rowHeight;
            }
            
            x = 50;
            Duration d = Duration.between(trade.entryTime(), trade.exitTime());
            String durStr = String.format("%d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
            
            String[] rowData = {
                String.valueOf(tradeNum++),
                trade.symbol().name(),
                trade.direction().toString(),
                DATETIME_FORMATTER.format(trade.entryTime()),
                DATETIME_FORMATTER.format(trade.exitTime()),
                durStr,
                PNL_FORMAT.format(trade.profitAndLoss())
            };
            
            for(int i=0; i < rowData.length; i++) {
                drawText(FONT_NORMAL, 9, x + cellMargin, yPosition - 10, rowData[i]);
                x += colWidths[i];
            }
            yPosition -= rowHeight;
        }
    }

    private boolean checkPageBreak(float requiredHeight) {
        return yPosition - requiredHeight < 50;
    }
    
    private void startNewPageWithHeader(String title) throws IOException {
        startNewPage();
        drawText(FONT_BOLD, 14, 50, yPosition, title);
        yPosition -= 30;
    }

    private List<String> getLines(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            float size = fontSize * font.getStringWidth(line + " " + word) / 1000;
            if (size > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) {
                    line.append(" ");
                }
                line.append(word);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    private BufferedImage createEquityCurveImage(OverallStats stats, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);

        List<EquityPoint> curve = stats.equityCurve();
        int p = 30; // padding

        BigDecimal minBalance = curve.stream().map(EquityPoint::cumulativeBalance).min(Comparator.naturalOrder()).orElse(stats.startBalance());
        BigDecimal maxBalance = curve.stream().map(EquityPoint::cumulativeBalance).max(Comparator.naturalOrder()).orElse(stats.startBalance());
        if (minBalance.compareTo(maxBalance) == 0) {
            minBalance = minBalance.subtract(BigDecimal.ONE);
            maxBalance = maxBalance.add(BigDecimal.ONE);
        }
        final BigDecimal range = maxBalance.subtract(minBalance);

        long minTime = curve.get(0).timestamp().toEpochMilli();
        long maxTime = curve.get(curve.size() - 1).timestamp().toEpochMilli();
        final long timeRange = (maxTime - minTime == 0) ? 1 : maxTime - minTime;
        
        g2.setColor(Color.LIGHT_GRAY);
        g2.drawLine(p, p, p, height - p); // Y-axis
        g2.drawLine(p, height - p, width - p, height - p); // X-axis
        
        g2.setColor(Color.BLUE);
        Polygon poly = new Polygon();
        for (EquityPoint point : curve) {
            int x = p + (int) (((double)(point.timestamp().toEpochMilli() - minTime) / timeRange) * (width - 2 * p));
            int y = (height - p) - (int) (point.cumulativeBalance().subtract(minBalance).doubleValue() / range.doubleValue() * (height - 2 * p));
            poly.addPoint(x, y);
        }
        g2.drawPolyline(poly.xpoints, poly.ypoints, poly.npoints);
        
        g2.dispose();
        return image;
    }
}