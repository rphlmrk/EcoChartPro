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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Generates a standalone HTML report for a trading session.
 */
public class HtmlReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(HtmlReportGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DecimalFormat PNL_FORMAT = new DecimalFormat("+$#,##0.00;-$#,##0.00");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0'%'");

    /**
     * Generates and saves an HTML report from a replay session state.
     * @param state The session state containing the trade history and other data.
     * @param outputFile The file to save the HTML report to.
     * @throws IOException if an error occurs while writing the file.
     */
    public static void generate(ReplaySessionState state, File outputFile) throws IOException {
        logger.info("Generating HTML report for symbol {} to {}", state.lastActiveSymbol(), outputFile.getAbsolutePath());
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

        String css = generateCss();
        String js = generateJavascript();
        String header = generateHeader(state, stats);
        String statsHtml = generateStatsHtml(stats);
        String chartSvg = generateEquityCurveSvg(stats.equityCurve(), stats.startBalance());
        String perfAnalyticsHtml = generatePerformanceAnalyticsHtml(pnlDistribution);
        String mistakeAnalysisHtml = generateMistakeAnalysisHtml(mistakeAnalysis);
        String coachingHtml = generateCoachingInsightsHtml(insights);
        String tradesTable = generateTradesTableHtml(stats.trades());

        String htmlContent = String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Trading Report: %s</title>
                <style>%s</style>
            </head>
            <body>
                <div class="container">
                    %s
                    <div class="card"><h2>Key Performance Metrics</h2>%s</div>
                    <div class="card"><h2>Equity Curve</h2>%s</div>
                    %s
                    %s
                    %s
                    <div class="card"><h2>Trade History</h2>%s</div>
                </div>
                %s
            </body>
            </html>
            """, state.lastActiveSymbol(), css, header, statsHtml, chartSvg, perfAnalyticsHtml, mistakeAnalysisHtml, coachingHtml, tradesTable, js);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(htmlContent);
        }
        logger.info("HTML report generated successfully.");
    }

    private static String generateCss() {
        return """
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f7f9; color: #333; }
            .container { max-width: 1200px; margin: auto; }
            .card { background-color: #fff; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 20px; padding: 20px; }
            h1, h2 { color: #2c3e50; }
            h1 { text-align: center; margin-bottom: 0; }
            h2 { padding-bottom: 10px; border-bottom: 2px solid #ecf0f1; }
            .card h3 { margin-top: 5px; padding-bottom: 8px; color: #34495e; font-size: 1.2em; }
            .subtitle { text-align: center; color: #7f8c8d; margin-top: 5px; margin-bottom: 20px; }
            .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px; }
            .stat { background-color: #ecf0f1; padding: 15px; border-radius: 5px; text-align: center; }
            .stat .label { font-size: 0.9em; color: #7f8c8d; }
            .stat .value { font-size: 1.4em; font-weight: bold; }
            .positive { color: #27ae60; }
            .negative { color: #c0392b; }
            table { width: 100%; border-collapse: collapse; font-size: 0.9em; }
            th, td { padding: 10px; border-bottom: 1px solid #ddd; text-align: left; }
            th { background-color: #f2f2f2; }
            tr:hover { background-color: #f9f9f9; }
            td.number, th.number { text-align: right; }
            .svg-chart { width: 100%; height: auto; }
            /* --- Collapsible Styles --- */
            .collapsible-header { display: flex; align-items: center; cursor: pointer; padding: 10px 0; margin-top: 15px; border-bottom: 1px solid #dde4e9; user-select: none; }
            .collapsible-header h3 { margin: 0; }
            .collapsible-header:first-of-type { margin-top: 5px; }
            .arrow { display: inline-block; width: 8px; height: 8px; border-top: 2px solid #34495e; border-right: 2px solid #34495e; margin-right: 15px; transition: transform 0.2s ease-in-out; }
            .collapsible-container.collapsed .arrow { transform: rotate(45deg); } /* Right arrow */
            .collapsible-container .arrow { transform: rotate(135deg); } /* Down arrow */
            .collapsible-content { overflow: hidden; max-height: 10000px; /* Large value for transition */ transition: max-height 0.5s ease-in-out; }
            .collapsible-container.collapsed .collapsible-content { max-height: 0; }
            /* --- Bar Chart Styles --- */
            .bar-chart-container { display: flex; justify-content: space-around; align-items: flex-end; height: 200px; border-bottom: 1px solid #ccc; padding: 10px 0; }
            .bar-group { display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; text-align: center; flex: 1; }
            .bar-value { font-size: 0.8em; font-weight: bold; margin-bottom: 3px; }
            .bar { width: 80%; background-color: #3498db; border-radius: 3px 3px 0 0; }
            .bar.positive { background-color: #27ae60; }
            .bar.negative { background-color: #c0392b; }
            .bar-label { font-size: 0.7em; color: #7f8c8d; margin-top: 5px; }
            /* --- Coaching Insights Styles --- */
            .coaching-list { list-style: none; padding-left: 0; }
            .coaching-list li { margin-bottom: 15px; }
            .coaching-list strong { color: #2c3e50; }
            """;
    }
    
    private static String generateJavascript() {
        return """
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    const headers = document.querySelectorAll('.collapsible-header');
                    headers.forEach(header => {
                        header.addEventListener('click', function() {
                            this.parentElement.classList.toggle('collapsed');
                        });
                    });
                });
            </script>
            """;
    }

    private static String generateHeader(ReplaySessionState state, OverallStats stats) {
        String dateRange = "N/A";
        if (!stats.trades().isEmpty()) {
            Instant firstTrade = stats.trades().get(0).entryTime();
            Instant lastTrade = stats.trades().get(stats.trades().size() - 1).exitTime();
            dateRange = String.format("%s to %s", DATE_FORMATTER.format(firstTrade), DATE_FORMATTER.format(lastTrade));
        }
        return String.format("""
            <h1>Trading Performance Report</h1>
            <p class="subtitle">Session Context: <strong>%s</strong> | Period: <strong>%s</strong></p>
            """, state.lastActiveSymbol(), dateRange);
    }

    private static String generateStatsHtml(OverallStats stats) {
        return String.format("""
            <div class="stats-grid">
                <div class="stat"><div class="label">Total Net P&L</div><div class="value %s">%s</div></div>
                <div class="stat"><div class="label">Win Rate</div><div class="value">%s</div></div>
                <div class="stat"><div class="label">Profit Factor</div><div class="value %s">%s</div></div>
                <div class="stat"><div class="label">Avg. RR</div><div class="value %s">%s</div></div>
                <div class="stat"><div class="label">Expectancy</div><div class="value %s">%s</div></div>
                <div class="stat"><div class="label">Total Trades</div><div class="value">%d</div></div>
            </div>
            """,
            stats.totalPnl().signum() >= 0 ? "positive" : "negative", PNL_FORMAT.format(stats.totalPnl()),
            PERCENT_FORMAT.format(stats.winRate() * 100),
            stats.profitFactor().compareTo(BigDecimal.ONE) >= 0 ? "positive" : "negative", DECIMAL_FORMAT.format(stats.profitFactor()),
            stats.avgRiskReward() >= 1.0 ? "positive" : "negative", DECIMAL_FORMAT.format(stats.avgRiskReward()),
            stats.expectancy().signum() >= 0 ? "positive" : "negative", PNL_FORMAT.format(stats.expectancy()),
            stats.totalTrades());
    }

    private static String generatePerformanceAnalyticsHtml(List<PnlDistributionBin> pnlDistribution) {
        if (pnlDistribution.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("<div class='card'><h2>Performance Analytics</h2>");
        sb.append("<h3>P&L Distribution (# of Trades)</h3>");
        sb.append("<div class='bar-chart-container'>");

        int maxCount = pnlDistribution.stream().mapToInt(PnlDistributionBin::count).max().orElse(1);

        for (PnlDistributionBin bin : pnlDistribution) {
            double heightPercent = (double) bin.count() / maxCount * 100;
            String colorClass = "";
            if (bin.upperBound().signum() <= 0) colorClass = "negative";
            else if (bin.lowerBound().signum() >= 0) colorClass = "positive";
            
            String label = bin.label().replace(" to ", "<br>");

            sb.append(String.format("""
                <div class="bar-group">
                    <div class="bar-value">%d</div>
                    <div class="bar %s" style="height: %.2f%%;" title="%s"></div>
                    <div class="bar-label">%s</div>
                </div>
                """, bin.count(), colorClass, heightPercent, bin.label(), label));
        }

        sb.append("</div></div>");
        return sb.toString();
    }

    private static String generateMistakeAnalysisHtml(Map<String, MistakeStats> mistakeAnalysis) {
        if (mistakeAnalysis.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("<div class='card'><h2>Mistake Analysis</h2>");
        sb.append("""
            <table>
                <thead><tr>
                    <th>Mistake</th><th class="number">Frequency</th><th class="number">Total P&L</th><th class="number">Avg P&L</th>
                </tr></thead>
                <tbody>
            """);

        mistakeAnalysis.values().stream()
            .sorted(Comparator.comparing(MistakeStats::totalPnl))
            .forEach(mistake -> sb.append(String.format("""
                <tr>
                    <td>%s</td>
                    <td class="number">%d</td>
                    <td class="number %s">%s</td>
                    <td class="number %s">%s</td>
                </tr>
                """,
                mistake.mistakeName(),
                mistake.frequency(),
                mistake.totalPnl().signum() >= 0 ? "positive" : "negative", PNL_FORMAT.format(mistake.totalPnl()),
                mistake.averagePnl().signum() >= 0 ? "positive" : "negative", PNL_FORMAT.format(mistake.averagePnl())
            )));
        
        sb.append("</tbody></table></div>");
        return sb.toString();
    }
    
    private static String generateCoachingInsightsHtml(List<CoachingInsight> insights) {
        if (insights.isEmpty()) return "";
        
        StringBuilder sb = new StringBuilder("<div class='card'><h2>Coaching Insights</h2><ul class='coaching-list'>");
        
        for (CoachingInsight insight : insights) {
            sb.append(String.format("<li><strong>%s:</strong> %s</li>", insight.title(), insight.description()));
        }

        sb.append("</ul></div>");
        return sb.toString();
    }
    
    private static String generateTradesTableHtml(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return "<p>No trades were recorded for this session.</p>";
        }

        Map<YearMonth, List<Trade>> tradesByMonth = trades.stream()
                .collect(Collectors.groupingBy(
                        trade -> YearMonth.from(trade.exitTime().atZone(ZoneOffset.UTC)),
                        TreeMap::new,
                        Collectors.toList()
                ));

        StringBuilder sb = new StringBuilder();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy");
        int globalTradeNum = 1;

        for (Map.Entry<YearMonth, List<Trade>> entry : tradesByMonth.entrySet()) {
            YearMonth month = entry.getKey();
            List<Trade> tradesForMonth = entry.getValue();

            sb.append("<div class='collapsible-container collapsed'>");
            sb.append("<div class='collapsible-header'>");
            sb.append("<span class='arrow'></span>");
            sb.append("<h3>").append(month.format(monthFormatter)).append("</h3>");
            sb.append("</div>");

            sb.append("<div class='collapsible-content'>");
            sb.append("""
                <table>
                    <thead>
                        <tr>
                            <th>#</th><th>Symbol</th><th>Side</th><th>Entry Time</th><th>Exit Time</th><th>Duration</th><th class="number">P&L</th>
                        </tr>
                    </thead>
                    <tbody>
                """);

            for (Trade trade : tradesForMonth) {
                Duration duration = Duration.between(trade.entryTime(), trade.exitTime());
                String durStr = String.format("%d:%02d:%02d", duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
                sb.append(String.format("""
                    <tr>
                        <td>%d</td><td>%s</td><td class="%s">%s</td><td>%s</td><td>%s</td><td>%s</td><td class="number %s">%s</td>
                    </tr>
                    """,
                    globalTradeNum++,
                    trade.symbol().name(),
                    trade.direction().toString().equalsIgnoreCase("LONG") ? "positive" : "negative", trade.direction(),
                    DATETIME_FORMATTER.format(trade.entryTime()),
                    DATETIME_FORMATTER.format(trade.exitTime()),
                    durStr,
                    trade.profitAndLoss().signum() >= 0 ? "positive" : "negative", PNL_FORMAT.format(trade.profitAndLoss())
                ));
            }
            sb.append("</tbody></table></div></div>");
        }
        return sb.toString();
    }

    private static String generateEquityCurveSvg(List<EquityPoint> equityCurve, BigDecimal startBalance) {
        if (equityCurve.size() < 2) return "<p>Not enough data for chart.</p>";

        int width = 1100, height = 400;
        int p = 50; // padding

        BigDecimal minBalance = equityCurve.stream().map(EquityPoint::cumulativeBalance).min(Comparator.naturalOrder()).orElse(startBalance);
        BigDecimal maxBalance = equityCurve.stream().map(EquityPoint::cumulativeBalance).max(Comparator.naturalOrder()).orElse(startBalance);
        long minTime = equityCurve.get(0).timestamp().getEpochSecond();
        long maxTime = equityCurve.get(equityCurve.size() - 1).timestamp().getEpochSecond();
        
        if (minBalance.compareTo(maxBalance) == 0) {
            minBalance = minBalance.subtract(BigDecimal.ONE);
            maxBalance = maxBalance.add(BigDecimal.ONE);
        }
        if (minTime == maxTime) {
            minTime -= 1;
            maxTime += 1;
        }

        final BigDecimal finalMinBalance = minBalance;
        final BigDecimal finalMaxBalance = maxBalance;
        final long finalMinTime = minTime;
        final long finalMaxTime = maxTime;

        String points = equityCurve.stream().map(pnt -> {
            double x = p + ((double) pnt.timestamp().getEpochSecond() - finalMinTime) / (finalMaxTime - finalMinTime) * (width - 2 * p);
            double y = (height - p) - (pnt.cumulativeBalance().subtract(finalMinBalance)).doubleValue()
                    / (finalMaxBalance.subtract(finalMinBalance)).doubleValue() * (height - 2 * p);
            return String.format("%.2f,%.2f", x, y);
        }).collect(Collectors.joining(" "));

        double startBalanceY = (height - p) - (startBalance.subtract(finalMinBalance)).doubleValue()
                / (finalMaxBalance.subtract(finalMinBalance)).doubleValue() * (height - 2 * p);

        return String.format("""
            <svg width="%d" height="%d" class="svg-chart">
                <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#ccc"/> <!-- Y-Axis -->
                <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#ccc"/> <!-- X-Axis -->
                <line x1="%d" y1="%.2f" x2="%d" y2="%.2f" stroke="#e74c3c" stroke-dasharray="4"/> <!-- Start Balance Line -->
                <polyline points="%s" fill="none" stroke="#3498db" stroke-width="2"/>
                <text x="%d" y="%.2f" font-size="12" fill="#e74c3c" dy="-4">Start Balance</text>
                <text x="%d" y="15" font-size="12" fill="#555">Balance: %s</text>
                <text x="%d" y="%d" text-anchor="end" font-size="12" fill="#555">End Balance: %s</text>
            </svg>
            """,
            width, height,
            p, p, p, height - p,
            p, height - p, width - p, height - p,
            p, startBalanceY, width - p, startBalanceY,
            points,
            p + 5, startBalanceY,
            p, PNL_FORMAT.format(finalMaxBalance),
            width - p, 15, PNL_FORMAT.format(equityCurve.get(equityCurve.size() - 1).cumulativeBalance()));
    }
}