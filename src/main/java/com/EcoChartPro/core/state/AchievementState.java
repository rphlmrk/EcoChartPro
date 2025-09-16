package com.EcoChartPro.core.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Set;

/**
 * A simple, serializable record to hold the persistent state of the AchievementService.
 * This object is used for saving to and loading from a JSON file.
 *
 * @param unlockedAchievementIds The set of unique string IDs for all unlocked achievements.
 */
public record AchievementState(
    Set<String> unlockedAchievementIds
) implements Serializable {

    // JSON creator for robust deserialization with Jackson
    @JsonCreator
    public AchievementState(
        @JsonProperty("unlockedAchievementIds") Set<String> unlockedAchievementIds
    ) {
        this.unlockedAchievementIds = unlockedAchievementIds;
    }
}