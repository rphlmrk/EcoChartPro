package com.EcoChartPro.core.coaching;

/**
 * Categorizes the type of pattern detected by the CoachingService.
 */
public enum InsightType {
    /** A pattern related to a specific day of the week or time of day. */
    TIME_BASED,
    /** A pattern related to the sequence of trades (e.g., after a large loss). */
    SEQUENCE_BASED,
    /** A pattern related to a specific trading symbol or asset. */
    ASSET_BASED
}