package com.EcoChartPro.core.state;

import com.EcoChartPro.core.coaching.Challenge;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple, serializable record to hold the persistent state of the GamificationService.
 * This object is used for saving to and loading from a JSON file.
 */
public record GamificationState(
    int bestPositiveStreak,
    int currentPositiveStreak,
    String mostFrequentMistake,
    BigDecimal pnlOnMistakeDays,
    long totalXp,
    int currentLevel,
    Challenge activeDailyChallenge,
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonSerialize(using = LocalDateSerializer.class)
    LocalDate lastTradeDate,
    int optimalTradeCount,
    List<Integer> peakPerformanceHours,
    int professionalStreak,
    int clockworkStreak,
    int sessionAdherenceStreak
) implements Serializable {

    // Default constructor for creating an empty/initial state
    public GamificationState() {
        this(0, 0, "None", BigDecimal.ZERO, 0L, 1, null, null, 5, Collections.emptyList(), 0, 0, 0);
    }

    // JSON creator for robust deserialization with Jackson
    @JsonCreator
    public GamificationState(
        @JsonProperty("bestPositiveStreak") int bestPositiveStreak,
        @JsonProperty("currentPositiveStreak") int currentPositiveStreak,
        @JsonProperty("mostFrequentMistake") String mostFrequentMistake,
        @JsonProperty("pnlOnMistakeDays") BigDecimal pnlOnMistakeDays,
        @JsonProperty("totalXp") long totalXp,
        @JsonProperty("currentLevel") int currentLevel,
        @JsonProperty("activeDailyChallenge") Challenge activeDailyChallenge,
        @JsonProperty("lastTradeDate") LocalDate lastTradeDate,
        @JsonProperty("optimalTradeCount") Integer optimalTradeCount,
        @JsonProperty("peakPerformanceHours") List<Integer> peakPerformanceHours,
        @JsonProperty("professionalStreak") Integer professionalStreak,
        @JsonProperty("clockworkStreak") Integer clockworkStreak,
        @JsonProperty("sessionAdherenceStreak") Integer sessionAdherenceStreak
    ) {
        this(
            bestPositiveStreak,
            currentPositiveStreak,
            mostFrequentMistake,
            pnlOnMistakeDays,
            totalXp,
            Math.max(1, currentLevel),
            activeDailyChallenge,
            lastTradeDate,
            optimalTradeCount != null ? optimalTradeCount : 5,
            peakPerformanceHours != null ? peakPerformanceHours : Collections.emptyList(),
            professionalStreak != null ? professionalStreak : 0,
            clockworkStreak != null ? clockworkStreak : 0,
            sessionAdherenceStreak != null ? sessionAdherenceStreak : 0
        );
    }
}