package com.EcoChartPro.core.gamification;

/**
 * Represents a single achievement or goal within the application.
 * This is an immutable data record.
 *
 * @param id          A unique identifier for the achievement (e.g., "streak_7").
 * @param title       The display title of the achievement (e.g., "7-Day Streak (Bronze)").
 * @param description A brief explanation of how to unlock the achievement.
 * @param iconPath    The path to the SVG icon for this achievement, from UITheme.Icons.
 * @param isSecret    If true, the achievement's details are hidden until it is unlocked.
 */
public record Achievement(
    String id,
    String title,
    String description,
    String iconPath,
    boolean isSecret
) {}