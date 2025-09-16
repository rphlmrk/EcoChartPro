package com.EcoChartPro.core.coaching;

/**
 * A data record representing a single piece of actionable advice or a pattern
 * discovered by the CoachingService.
 *
 * @param id          A unique identifier for the type of insight (e.g., "FRIDAY_FOMO_LOSS").
 * @param title       A concise summary of the insight (e.g., "Friday FOMO").
 * @param description The detailed finding (e.g., "You lose 80% of your 'FOMO' trades on Fridays.").
 * @param severity    The ranked impact of this pattern.
 * @param type        The category of this pattern.
 */
public record CoachingInsight(
    String id,
    String title,
    String description,
    InsightSeverity severity,
    InsightType type
) {}