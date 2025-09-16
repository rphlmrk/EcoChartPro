package com.EcoChartPro.core.gamification;

/**
 * A Data Transfer Object (DTO) that carries all necessary information from the
 * GamificationService to the UI components (like ProgressCardPanel).
 * This ensures a clean separation between backend logic and frontend presentation.
 */
public record ProgressCardViewModel(
    CardType cardType,
    String title,
    String primaryValue,
    String secondaryValue,
    double progress, // A value between 0.0 and 1.0 for progress bars/meters
    String goalText,
    String motivationalMessage
) {
    /**
     * Defines the different visual and informational states the ProgressCardPanel can be in.
     */
    public enum CardType {
        POSITIVE_STREAK,
        CRITICAL_MISTAKE,
        EMPTY,
        COACHING_INSIGHT,
        DAILY_CHALLENGE,
        STREAK_PAUSED,
        NEXT_ACHIEVEMENT
    }
}