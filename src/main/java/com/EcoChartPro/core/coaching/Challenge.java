package com.EcoChartPro.core.coaching;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * A data record representing a specific, trackable challenge for the user.
 * This object is designed to be serializable. The completion logic is handled
 * externally by the GamificationService.
 *
 * @param id             A unique identifier for the type of challenge (e.g., "CHLG_NO_SL_MOVE_3").
 * @param title          The display title of the challenge (e.g., "Iron Will Challenge").
 * @param description    A brief explanation of how to complete the challenge.
 * @param xpReward       The amount of bonus XP awarded on completion.
 * @param dateAssigned   The date the challenge was issued.
 * @param isComplete     The completion status of the challenge.
 */
public record Challenge(
    String id,
    String title,
    String description,
    int xpReward,
    LocalDate dateAssigned,
    boolean isComplete
) {
    @JsonCreator
    public Challenge(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("xpReward") int xpReward,
        @JsonProperty("dateAssigned") LocalDate dateAssigned,
        @JsonProperty("isComplete") boolean isComplete
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.xpReward = xpReward;
        this.dateAssigned = dateAssigned;
        this.isComplete = isComplete;
    }

    /**
     * Creates a new, uncompleted challenge for today.
     */
    public Challenge(String id, String title, String description, int xpReward) {
        this(id, title, description, xpReward, LocalDate.now(), false);
    }

    /**
     * Returns a new instance of the challenge marked as complete.
     */
    public Challenge asCompleted() {
        return new Challenge(id, title, description, xpReward, dateAssigned, true);
    }
}