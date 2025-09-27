package com.EcoChartPro.model;

import java.math.BigDecimal;

/**
 * A DTO holding aggregated statistics for a specific trading mistake.
 */
public record MistakeStats(
    String mistakeName,
    int frequency,
    BigDecimal totalPnl,
    BigDecimal averagePnl
) {}