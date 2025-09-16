package com.EcoChartPro.core.coaching;

/**
 * Represents the severity or impact of a discovered coaching insight.
 */
public enum InsightSeverity {
    /** A minor pattern or area for small improvement. */
    LOW,
    /** A consistent pattern with a noticeable negative impact. */
    MEDIUM,
    /** A critical, highly frequent, or very costly behavioral pattern. */
    HIGH
}