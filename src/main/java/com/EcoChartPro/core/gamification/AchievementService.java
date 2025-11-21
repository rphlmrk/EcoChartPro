package com.EcoChartPro.core.gamification;

import com.EcoChartPro.core.state.AchievementState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.NotificationService;
import com.EcoChartPro.ui.home.theme.UITheme;
import com.EcoChartPro.utils.AppDataManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A singleton service responsible for managing all game-like achievements.
 * It holds the master list of all possible achievements and tracks which ones
 * the user has unlocked.
 */
public final class AchievementService {

    /**
     * A data transfer object holding the key statistics from GamificationService,
     * used to check if any achievements have been unlocked.
     */
    public record GamificationStats(
        int currentPositiveStreak,
        int totalTrades,
        double winRate, // as a value from 0.0 to 1.0
        boolean hadProfitableDay,
        long criticalMistakeFreeSequence,
        int professionalStreak,
        int clockworkStreak,
        int sessionAdherenceStreak
    ) {}

    private static volatile AchievementService instance;
    private static final Logger logger = LoggerFactory.getLogger(AchievementService.class);
    private final ObjectMapper objectMapper;
    private static final String STATE_FILE_NAME = "achievements_state.json";

    private final Map<String, Achievement> allAchievements = new LinkedHashMap<>();
    private final Set<String> unlockedAchievementIds = new HashSet<>();

    private AchievementService() {
        this.objectMapper = new ObjectMapper();
        initializeAchievements();
        loadState();
    }

    public static AchievementService getInstance() {
        if (instance == null) {
            synchronized (AchievementService.class) {
                if (instance == null) {
                    instance = new AchievementService();
                }
            }
        }
        return instance;
    }

    /**
     * Defines and populates the master list of all achievements available in the application.
     */
    private void initializeAchievements() {
        // Streaks
        allAchievements.put("streak_3", new Achievement("streak_3", "3-Day Streak", "Maintain a positive discipline score for 3 consecutive days.", UITheme.Icons.TROPHY, false));
        allAchievements.put("streak_7", new Achievement("streak_7", "7-Day Streak (Bronze)", "Maintain a positive discipline score for 7 consecutive days.", UITheme.Icons.TROPHY, false));
        allAchievements.put("streak_14", new Achievement("streak_14", "14-Day Streak (Silver)", "Maintain a positive discipline score for 14 consecutive days.", UITheme.Icons.TROPHY, false));
        allAchievements.put("streak_30", new Achievement("streak_30", "30-Day Streak (Gold)", "Maintain a positive discipline score for 30 consecutive days.", UITheme.Icons.TROPHY, false));

        // Milestones
        allAchievements.put("first_profit_day", new Achievement("first_profit_day", "First Profitable Day", "Complete your first day with a net positive P&L.", UITheme.Icons.CHECKMARK, false));
        allAchievements.put("first_10_trades", new Achievement("first_10_trades", "First 10 Trades Logged", "Complete and log 10 trades in any session.", UITheme.Icons.HISTORY, false));

        // Discipline
        allAchievements.put("the_rock", new Achievement("the_rock", "The Rock", "Complete 10 trades in a row without any critical mistakes.", UITheme.Icons.PROTECTED_LEVEL, false));
        allAchievements.put("discipline_pro_5", new Achievement("discipline_pro_5", "The Professional", "Stick to your optimal trade count for 5 consecutive days.", UITheme.Icons.PROTECTED_LEVEL, false));
        allAchievements.put("discipline_clockwork_7", new Achievement("discipline_clockwork_7", "Clockwork", "Trade only during your peak performance hours for an entire week.", UITheme.Icons.CLOCK, false));
        allAchievements.put("session_sentinel_7", new Achievement("session_sentinel_7", "Session Sentinel", "Trade only within your preferred sessions for 7 consecutive days.", UITheme.Icons.CLOCK, false));


        // Performance
        allAchievements.put("sharpshooter", new Achievement("sharpshooter", "Sharpshooter", "Achieve a win rate over 60% across 20 or more trades.", UITheme.Icons.CROSSHAIR, false));
    }

    /**
     * Unlocks a specific achievement, adds it to the user's set, triggers a notification, and saves state.
     * @param achievementId The ID of the achievement to unlock.
     */
    private void unlock(String achievementId) {
        if (achievementId == null || isUnlocked(achievementId)) {
            return;
        }

        Achievement achievement = allAchievements.get(achievementId);
        if (achievement != null) {
            unlockedAchievementIds.add(achievementId);
            logger.info("Achievement Unlocked: {}", achievement.title());
            NotificationService.getInstance().showAchievementUnlocked(achievement);
            saveState();
        }
    }

    /**
     * Checks the player's current stats against all defined achievements and unlocks
     * any that have been earned.
     * @param stats The current gamification stats to evaluate.
     */
    public void checkAndUnlockAchievements(GamificationStats stats) {
        if (stats == null) return;

        // Check streak achievements
        if (stats.currentPositiveStreak() >= 3) unlock("streak_3");
        if (stats.currentPositiveStreak() >= 7) unlock("streak_7");
        if (stats.currentPositiveStreak() >= 14) unlock("streak_14");
        if (stats.currentPositiveStreak() >= 30) unlock("streak_30");

        // Check milestone achievements
        if (stats.hadProfitableDay()) unlock("first_profit_day");
        if (stats.totalTrades() >= 10) unlock("first_10_trades");

        // Check discipline achievements
        if (stats.criticalMistakeFreeSequence() >= 10) unlock("the_rock");
        if (stats.professionalStreak() >= 5) unlock("discipline_pro_5");
        if (stats.clockworkStreak() >= 7) unlock("discipline_clockwork_7");
        if (stats.sessionAdherenceStreak() >= 7) unlock("session_sentinel_7");

        // Check performance achievements
        if (stats.totalTrades() >= 20 && stats.winRate() > 0.60) unlock("sharpshooter");
    }
    
    /**
     * Saves the current set of unlocked achievement IDs to a JSON file.
     */
    public void saveState() {
        Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(STATE_FILE_NAME);
        if (filePathOpt.isPresent()) {
            try {
                AchievementState state = new AchievementState(new HashSet<>(unlockedAchievementIds));
                byte[] jsonData = objectMapper.writeValueAsBytes(state);
                Files.write(filePathOpt.get(), jsonData);
                logger.debug("Achievement state saved successfully.");
            } catch (IOException e) {
                logger.error("Failed to save achievement state.", e);
            }
        }
    }

    /**
     * Loads the set of unlocked achievement IDs from a JSON file on startup.
     */
    private void loadState() {
        Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(STATE_FILE_NAME);
        if (filePathOpt.isPresent() && Files.exists(filePathOpt.get())) {
            try {
                byte[] jsonData = Files.readAllBytes(filePathOpt.get());
                AchievementState state = objectMapper.readValue(jsonData, AchievementState.class);
                if (state != null && state.unlockedAchievementIds() != null) {
                    this.unlockedAchievementIds.clear();
                    this.unlockedAchievementIds.addAll(state.unlockedAchievementIds());
                    logger.info("Loaded {} unlocked achievements.", this.unlockedAchievementIds.size());
                }
            } catch (IOException e) {
                logger.error("Failed to load achievement state. Starting fresh.", e);
                this.unlockedAchievementIds.clear();
            }
        } else {
            logger.info("No achievement state file found. Starting fresh.");
        }
    }


    /**
     * @return An ordered list of all defined achievements.
     */
    public List<Achievement> getAllAchievements() {
        return new ArrayList<>(allAchievements.values());
    }

    /**
     * Checks if a specific achievement has been unlocked.
     * @param achievementId The unique ID of the achievement.
     * @return true if the achievement is in the set of unlocked achievements, false otherwise.
     */
    public boolean isUnlocked(String achievementId) {
        return unlockedAchievementIds.contains(achievementId);
    }
    /**
 * Finds the first achievement in the master list that the user has not yet unlocked.
 * This is used to guide the player on what to do next.
 * @return An Optional containing the next locked achievement, or empty if all are unlocked.
 */
public Optional<Achievement> getNextLockedAchievement() {
    // The LinkedHashMap preserves insertion order, so this will find the "next" one.
    for (Achievement achievement : allAchievements.values()) {
        if (!isUnlocked(achievement.id())) {
            return Optional.of(achievement);
        }
    }
    return Optional.empty(); // All achievements unlocked
    }
}