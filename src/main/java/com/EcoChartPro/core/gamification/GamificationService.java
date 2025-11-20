package com.EcoChartPro.core.gamification;

import com.EcoChartPro.core.coaching.Challenge;
import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.AchievementService.GamificationStats;
import com.EcoChartPro.core.state.GamificationState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.NotificationService;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.utils.AppDataManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class GamificationService {

    private static final Logger logger = LoggerFactory.getLogger(GamificationService.class);
    private static volatile GamificationService instance;

    public record XpProgress(long currentXpInLevel, long requiredXpForLevel) {
    }

    private final ObjectMapper objectMapper;
    private static final String STATE_FILE_NAME = "gamification_state.json";
    private static final int NO_MISTAKES_BONUS = 10;
    public static final Map<String, Integer> XP_SCORES;
    private static final Set<String> CRITICAL_MISTAKES;
    private final transient Map<String, Predicate<List<Trade>>> challengePredicates;

    static {
        XP_SCORES = Map.ofEntries(
                Map.entry("Exited Too Early", -2),
                Map.entry("Undersized Position", -2),
                Map.entry("Oversized Position", -3),
                Map.entry("Chased Entry", -3),
                Map.entry("Moved Stop Loss", -5),
                Map.entry("Held a Loser Too Long", -5),
                Map.entry("FOMO (Fear of Missing Out)", -5),
                Map.entry("Revenge Trading", -10),
                Map.entry("Ignored Trading Plan", -10),
                Map.entry("System Not Followed", -10));
        CRITICAL_MISTAKES = XP_SCORES.entrySet().stream()
                .filter(entry -> entry.getValue() <= -10).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private int currentPositiveStreak;
    private int bestPositiveStreak;
    private int lastDayXp;
    private String mostFrequentMistake;
    private BigDecimal pnlOnMistakeDays;
    private long totalXp;
    private int currentLevel;
    private Challenge activeDailyChallenge;
    private LocalDate lastTradeDate;
    private boolean streakWasPaused = false;
    private int optimalTradeCount;
    private List<Integer> peakPerformanceHours;
    private int professionalStreak;
    private int clockworkStreak;
    private int sessionAdherenceStreak;

    private GamificationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.challengePredicates = new HashMap<>();
        initializeChallengeLogic();

        loadState();
        this.lastDayXp = 0;
        this.mostFrequentMistake = "None";
        this.pnlOnMistakeDays = BigDecimal.ZERO;
    }

    public static GamificationService getInstance() {
        if (instance == null) {
            synchronized (GamificationService.class) {
                if (instance == null) {
                    instance = new GamificationService();
                }
            }
        }
        return instance;
    }

    private void initializeChallengeLogic() {
        challengePredicates.put("CHLG_NO_FOMO", trades -> trades.stream()
                .noneMatch(t -> t.identifiedMistakes() != null
                        && t.identifiedMistakes().contains("FOMO (Fear of Missing Out)")));

        challengePredicates.put("CHLG_PROFITABLE_DAY", trades -> trades.stream()
                .map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0);

        challengePredicates.put("CHLG_TRADE_LIMIT", trades -> trades.size() <= this.optimalTradeCount);

        challengePredicates.put("CHLG_EARLY_BIRD", trades -> {
            if (peakPerformanceHours == null || peakPerformanceHours.isEmpty())
                return true;
            int latestHour = Collections.max(peakPerformanceHours);
            return trades.stream().allMatch(t -> t.entryTime().atZone(ZoneOffset.UTC).getHour() <= latestHour);
        });

        challengePredicates.put("CHLG_PREFERRED_HOURS_ONLY", trades -> trades.stream()
                .noneMatch(t -> t.tags() != null && t.tags().contains("Out-Side-Trading-Hours")));
    }

    /**
     * [NEW] Generates a View Model for the Gamification Widget (Streak / Next
     * Achievement).
     * This logic determines whether to show the current streak status OR the next
     * achievement goal.
     */
    public ProgressCardViewModel getLatestProgressViewModel() {
        // 1. If streak was broken/paused recently, show that.
        if (streakWasPaused) {
            return new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.STREAK_PAUSED, "Streak Paused",
                    "0 Days", "Consistency is key!", 0.0, "",
                    "Don't give up. Start a new streak today.");
        }

        // 2. If active streak exists, prioritize showing it.
        if (currentPositiveStreak > 0) {
            int[] streakGoals = { 3, 7, 14, 30, 60, 90 };
            int goal = 3;
            for (int g : streakGoals) {
                if (currentPositiveStreak < g) {
                    goal = g;
                    break;
                }
            }
            if (currentPositiveStreak >= 90)
                goal = currentPositiveStreak + 10;

            return new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.POSITIVE_STREAK, "Discipline Streak",
                    currentPositiveStreak + " Days", "Best: " + bestPositiveStreak + " Days",
                    (double) currentPositiveStreak / goal, "Goal: " + goal + " Days",
                    "Excellent discipline builds confidence.");
        }

        // 3. If no streak, show the Next Achievement to unlock.
        Optional<Achievement> next = AchievementService.getInstance().getNextLockedAchievement();
        if (next.isPresent()) {
            return new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.NEXT_ACHIEVEMENT, "Next Goal",
                    next.get().title(), "Unlock Your Next Achievement",
                    0.0, "",
                    next.get().description());
        }

        // 4. Fallback
        return new ProgressCardViewModel(
                ProgressCardViewModel.CardType.EMPTY, "Daily Discipline",
                "No Active Streak", "Start Fresh Today",
                0.0, "",
                "Focus on execution.");
    }

    public void saveState() {
        GamificationState state = new GamificationState(
                this.bestPositiveStreak, this.currentPositiveStreak, this.mostFrequentMistake,
                this.pnlOnMistakeDays, this.totalXp, this.currentLevel, this.activeDailyChallenge, this.lastTradeDate,
                this.optimalTradeCount, this.peakPerformanceHours, this.professionalStreak, this.clockworkStreak,
                this.sessionAdherenceStreak);
        Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(STATE_FILE_NAME);
        if (filePathOpt.isPresent()) {
            try {
                byte[] jsonData = objectMapper.writeValueAsBytes(state);
                Files.write(filePathOpt.get(), jsonData);
            } catch (IOException e) {
                logger.error("Failed to save gamification state.", e);
            }
        }
    }

    private void loadState() {
        Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(STATE_FILE_NAME);
        if (filePathOpt.isPresent() && Files.exists(filePathOpt.get())) {
            try {
                byte[] jsonData = Files.readAllBytes(filePathOpt.get());
                GamificationState state = objectMapper.readValue(jsonData, GamificationState.class);
                this.bestPositiveStreak = state.bestPositiveStreak();
                this.currentPositiveStreak = state.currentPositiveStreak();
                this.mostFrequentMistake = state.mostFrequentMistake();
                this.pnlOnMistakeDays = state.pnlOnMistakeDays();
                this.totalXp = state.totalXp();
                this.currentLevel = state.currentLevel();
                this.activeDailyChallenge = state.activeDailyChallenge();
                this.lastTradeDate = state.lastTradeDate();
                this.optimalTradeCount = state.optimalTradeCount();
                this.peakPerformanceHours = new ArrayList<>(state.peakPerformanceHours());
                this.professionalStreak = state.professionalStreak();
                this.clockworkStreak = state.clockworkStreak();
                this.sessionAdherenceStreak = state.sessionAdherenceStreak();
            } catch (IOException e) {
                logger.error("Failed to load gamification state. Using defaults.", e);
                initializeDefaultState();
            }
        } else {
            initializeDefaultState();
        }
    }

    private void initializeDefaultState() {
        this.currentPositiveStreak = 0;
        this.bestPositiveStreak = 0;
        this.mostFrequentMistake = "None";
        this.pnlOnMistakeDays = BigDecimal.ZERO;
        this.totalXp = 0L;
        this.currentLevel = 1;
        this.activeDailyChallenge = null;
        this.lastTradeDate = null;
        this.optimalTradeCount = 5;
        this.peakPerformanceHours = new ArrayList<>();
        this.professionalStreak = 0;
        this.clockworkStreak = 0;
        this.sessionAdherenceStreak = 0;
    }

    public void updateProgression(List<Trade> allTrades) {
        if (allTrades == null || allTrades.isEmpty()) {
            this.lastDayXp = 0;
            this.mostFrequentMistake = "None";
            this.pnlOnMistakeDays = BigDecimal.ZERO;
            return;
        }

        updateTradingDisciplineMetrics(allTrades);
        this.streakWasPaused = false;
        generateDailyChallenge(allTrades);

        TreeMap<LocalDate, List<Trade>> sortedTradesByDay = allTrades.stream()
                .collect(Collectors.groupingBy(
                        trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new, Collectors.toList()));

        LocalDate firstTradeDate = sortedTradesByDay.firstKey();
        if (lastTradeDate != null) {
            long daysBetween = ChronoUnit.DAYS.between(lastTradeDate, firstTradeDate);
            if (daysBetween > 3) {
                this.currentPositiveStreak = 0;
                this.streakWasPaused = true;
            }
        }

        int calculatedStreak = this.currentPositiveStreak;
        int calculatedBestStreak = this.bestPositiveStreak;
        int calculatedProfessionalStreak = this.professionalStreak;
        int calculatedClockworkStreak = this.clockworkStreak;
        int calculatedSessionAdherenceStreak = this.sessionAdherenceStreak;
        int calculatedLastDayXp = 0;
        long calculatedTotalXp = 0L;
        boolean hadProfitableDay = false;

        for (Map.Entry<LocalDate, List<Trade>> dayEntry : sortedTradesByDay.entrySet()) {
            List<Trade> tradesForDay = dayEntry.getValue();
            int dayScore = calculateDayScore(tradesForDay);
            int xpGainedThisDay = Math.max(0, dayScore);
            calculatedTotalXp += xpGainedThisDay;

            if (dayScore >= 0) {
                calculatedStreak++;
            } else {
                calculatedStreak = 0;
            }
            calculatedBestStreak = Math.max(calculatedBestStreak, calculatedStreak);

            if (tradesForDay.size() <= this.optimalTradeCount)
                calculatedProfessionalStreak++;
            else
                calculatedProfessionalStreak = 0;

            if (peakPerformanceHours != null && !peakPerformanceHours.isEmpty()) {
                boolean allInPeak = tradesForDay.stream()
                        .allMatch(t -> peakPerformanceHours.contains(t.entryTime().atZone(ZoneOffset.UTC).getHour()));
                if (allInPeak)
                    calculatedClockworkStreak++;
                else
                    calculatedClockworkStreak = 0;
            } else {
                calculatedClockworkStreak = 0;
            }

            boolean allInPreferredHours = tradesForDay.stream()
                    .noneMatch(t -> t.tags() != null && t.tags().contains("Out-Side-Trading-Hours"));
            if (allInPreferredHours)
                calculatedSessionAdherenceStreak++;
            else
                calculatedSessionAdherenceStreak = 0;

            calculatedLastDayXp = xpGainedThisDay;
            if (tradesForDay.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add).signum() > 0) {
                hadProfitableDay = true;
            }
        }

        evaluateDailyChallengeCompletion(allTrades);

        this.lastTradeDate = sortedTradesByDay.lastKey();
        this.currentPositiveStreak = calculatedStreak;
        this.bestPositiveStreak = calculatedBestStreak;
        this.professionalStreak = calculatedProfessionalStreak;
        this.clockworkStreak = calculatedClockworkStreak;
        this.sessionAdherenceStreak = calculatedSessionAdherenceStreak;
        this.lastDayXp = calculatedLastDayXp;
        this.totalXp = calculatedTotalXp;

        int oldLevel = this.currentLevel;
        int newLevel = 1;
        while (getXpForLevelStart(newLevel + 1) <= this.totalXp) {
            newLevel++;
        }
        if (newLevel > oldLevel) {
            for (int i = oldLevel + 1; i <= newLevel; i++) {
                notifyLevelUp(i);
            }
        }
        this.currentLevel = newLevel;

        analyzeMistakePatterns(allTrades);
        checkAchievements(allTrades, hadProfitableDay);
        saveState(); // Save changes
    }

    private void evaluateDailyChallengeCompletion(List<Trade> allTrades) {
        if (activeDailyChallenge == null || activeDailyChallenge.isComplete()
                || !activeDailyChallenge.dateAssigned().equals(LocalDate.now())) {
            return;
        }
        Optional<LocalDate> lastDayOfTrades = allTrades.stream()
                .map(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate())
                .max(LocalDate::compareTo);

        if (lastDayOfTrades.isEmpty())
            return;

        List<Trade> tradesForLastDay = allTrades.stream()
                .filter(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate().equals(lastDayOfTrades.get()))
                .collect(Collectors.toList());

        Predicate<List<Trade>> completionLogic = challengePredicates.get(activeDailyChallenge.id());
        if (completionLogic != null && completionLogic.test(tradesForLastDay)) {
            this.totalXp += activeDailyChallenge.xpReward();
            this.activeDailyChallenge = activeDailyChallenge.asCompleted();
            notifyChallengeComplete(activeDailyChallenge);
        }
    }

    private void updateTradingDisciplineMetrics(List<Trade> allTrades) {
        long distinctDays = allTrades.stream()
                .map(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate())
                .distinct()
                .count();

        if (distinctDays < 10)
            return;

        com.EcoChartPro.core.journal.JournalAnalysisService analysisService = new com.EcoChartPro.core.journal.JournalAnalysisService();

        Map<Integer, com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByTradeCount> perfByCount = analysisService
                .analyzePerformanceByTradeCount(allTrades);
        if (!perfByCount.isEmpty()) {
            Optional<com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByTradeCount> bestCount = perfByCount
                    .values().stream()
                    .max(Comparator.comparing(
                            com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByTradeCount::expectancy));

            bestCount.ifPresent(count -> this.optimalTradeCount = count.tradesPerDay());
        }

        Map<Integer, com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByHour> perfByHour = analysisService
                .analyzePerformanceByTimeOfDay(allTrades);
        if (!perfByHour.isEmpty()) {
            final int MIN_TRADES_PER_HOUR = 3;
            List<Integer> newPeakHours = perfByHour.values().stream()
                    .filter(h -> h.tradeCount() >= MIN_TRADES_PER_HOUR && h.expectancy().signum() > 0)
                    .sorted(Comparator
                            .comparing(
                                    com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByHour::expectancy)
                            .reversed())
                    .map(com.EcoChartPro.core.journal.JournalAnalysisService.PerformanceByHour::hourOfDay)
                    .collect(Collectors.toList());

            if (!this.peakPerformanceHours.equals(newPeakHours)) {
                this.peakPerformanceHours = newPeakHours;
            }
        }
    }

    private void generateDailyChallenge(List<Trade> allTrades) {
        if (activeDailyChallenge != null && activeDailyChallenge.dateAssigned().equals(LocalDate.now())) {
            return;
        }

        List<CoachingInsight> insights = CoachingService.getInstance().analyze(allTrades, this.optimalTradeCount,
                this.peakPerformanceHours, Optional.empty());

        Optional<CoachingInsight> overtrainingInsight = insights.stream()
                .filter(i -> "OVERTRAINING_DIMINISHING_RETURNS".equals(i.id())).findFirst();
        if (overtrainingInsight.isPresent()) {
            String description = String.format(
                    "Stick to your proven edge. Do not take more than your optimal %d trades today.",
                    this.optimalTradeCount);
            activeDailyChallenge = new Challenge("CHLG_TRADE_LIMIT", "Disciplined Finisher", description, 75);
            return;
        }

        Optional<CoachingInsight> fatigueInsight = insights.stream().filter(i -> "FATIGUE_END_OF_DAY".equals(i.id()))
                .findFirst();
        if (fatigueInsight.isPresent() && !peakPerformanceHours.isEmpty()) {
            int latestHour = Collections.max(peakPerformanceHours);
            String timeString = LocalTime.of(latestHour, 0).plusHours(1).format(DateTimeFormatter.ofPattern("h:00 a"));
            String description = String
                    .format("Capitalize on your peak focus. Execute all of your trades before %s today.", timeString);
            activeDailyChallenge = new Challenge("CHLG_EARLY_BIRD", "Early Bird Profits", description, 60);
            return;
        }

        Optional<CoachingInsight> fomoInsight = insights.stream().filter(i -> i.id().contains("FOMO")).findFirst();
        if (fomoInsight.isPresent()) {
            activeDailyChallenge = new Challenge("CHLG_NO_FOMO", "Patience Practice",
                    "Complete today with zero 'FOMO' trades.", 50);
            return;
        }

        activeDailyChallenge = new Challenge("CHLG_PROFITABLE_DAY", "Profitable Day",
                "Finish today with a positive P&L.", 30);
    }

    private void notifyChallengeComplete(Challenge challenge) {
        Achievement challengeAchievement = new Achievement(challenge.id(), "Challenge Complete!", challenge.title(),
                UITheme.Icons.CHECKLISTS, false);
        NotificationService.getInstance().showAchievementUnlocked(challengeAchievement);
    }

    private void notifyLevelUp(int newLevel) {
        Achievement levelUpAchievement = new Achievement("level_" + newLevel, "Level " + newLevel + " Reached!",
                "You've gained enough experience to advance to the next level.", UITheme.Icons.TROPHY, false);
        NotificationService.getInstance().showAchievementUnlocked(levelUpAchievement);
    }

    private void checkAchievements(List<Trade> allTrades, boolean hadProfitableDay) {
        int totalTrades = allTrades.size();
        long winCount = allTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
        double winRate = (totalTrades > 0) ? (double) winCount / totalTrades : 0.0;

        long criticalMistakeFreeSequence = 0;
        List<Trade> reversedTrades = new ArrayList<>(allTrades);
        Collections.reverse(reversedTrades);
        for (Trade trade : reversedTrades) {
            boolean hasCriticalMistake = trade.identifiedMistakes() != null &&
                    trade.identifiedMistakes().stream().anyMatch(GamificationService::isCriticalMistake);
            if (hasCriticalMistake) {
                break;
            }
            criticalMistakeFreeSequence++;
        }

        GamificationStats stats = new GamificationStats(
                this.currentPositiveStreak, totalTrades, winRate, hadProfitableDay, criticalMistakeFreeSequence,
                this.professionalStreak, this.clockworkStreak, this.sessionAdherenceStreak);

        AchievementService.getInstance().checkAndUnlockAchievements(stats);
    }

    private void analyzeMistakePatterns(List<Trade> allTrades) {
        Map<String, Long> mistakeCounts = allTrades.stream()
                .filter(trade -> trade.identifiedMistakes() != null)
                .flatMap(trade -> trade.identifiedMistakes().stream())
                .filter(mistake -> !"No Mistakes Made".equalsIgnoreCase(mistake))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (mistakeCounts.isEmpty()) {
            this.mostFrequentMistake = "None";
            this.pnlOnMistakeDays = BigDecimal.ZERO;
            return;
        }

        String topMistake = Collections.max(mistakeCounts.entrySet(), Map.Entry.comparingByValue()).getKey();
        this.mostFrequentMistake = topMistake;
        this.pnlOnMistakeDays = allTrades.stream()
                .filter(trade -> trade.identifiedMistakes() != null && trade.identifiedMistakes().contains(topMistake))
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int calculateDayScore(List<Trade> tradesForOneDay) {
        if (tradesForOneDay == null || tradesForOneDay.isEmpty())
            return 0;
        int dailyScore = 0;
        boolean mistakesFound = false;
        for (Trade trade : tradesForOneDay) {
            if (trade.identifiedMistakes() != null && !trade.identifiedMistakes().isEmpty()) {
                for (String mistake : trade.identifiedMistakes()) {
                    if ("No Mistakes Made".equalsIgnoreCase(mistake))
                        continue;
                    mistakesFound = true;
                    dailyScore += XP_SCORES.getOrDefault(mistake, 0);
                }
            }
        }
        if (!mistakesFound)
            dailyScore += NO_MISTAKES_BONUS;
        return dailyScore;
    }

    public long getXpForNextLevel(int level) {
        if (level < 1)
            return 100;
        return (long) (100 * Math.pow(1.15, level - 1));
    }

    public long getXpForLevelStart(int level) {
        if (level <= 1)
            return 0;
        long total = 0;
        for (int i = 1; i < level; i++)
            total += getXpForNextLevel(i);
        return total;
    }

    public static boolean isCriticalMistake(String mistake) {
        return mistake != null && CRITICAL_MISTAKES.contains(mistake);
    }

    public String getLevelTitle(int level) {
        if (level < 5)
            return "Novice Analyst";
        if (level < 10)
            return "Apprentice Trader";
        if (level < 15)
            return "Journeyman Trader";
        if (level < 20)
            return "Disciplined Executor";
        if (level < 25)
            return "Seasoned Professional";
        if (level < 30)
            return "Chart Master";
        return "Market Veteran";
    }

    public XpProgress getCurrentLevelXpProgress() {
        long startXp = getXpForLevelStart(currentLevel);
        long requiredForLevel = getXpForNextLevel(currentLevel);
        long currentInLevel = totalXp - startXp;
        return new XpProgress(currentInLevel, requiredForLevel);
    }

    // Getters
    public int getCurrentPositiveStreak() {
        return currentPositiveStreak;
    }

    public int getBestPositiveStreak() {
        return bestPositiveStreak;
    }

    public int getLastDayXp() {
        return lastDayXp;
    }

    public String getMostFrequentMistake() {
        return mostFrequentMistake;
    }

    public BigDecimal getPnlOnMistakeDays() {
        return pnlOnMistakeDays;
    }

    public long getTotalXp() {
        return totalXp;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getOptimalTradeCount() {
        return optimalTradeCount;
    }

    public List<Integer> getPeakPerformanceHours() {
        return peakPerformanceHours;
    }

    public Optional<Challenge> getActiveDailyChallenge() {
        return Optional.ofNullable(activeDailyChallenge);
    }

    public boolean wasStreakPaused() {
        return streakWasPaused;
    }
}