package com.EcoChartPro.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Represents how well a trader followed their predefined plan for a trade.
 */
public enum PlanAdherence {
    NOT_RATED("Not Rated"),
    PERFECT_EXECUTION("Perfect Execution"),
    MINOR_DEVIATION("Minor Deviation"),
    MAJOR_DEVIATION("Major Deviation"),
    NO_PLAN("No Plan / Impulse");

    private final String displayName;

    PlanAdherence(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @JsonCreator
    public static PlanAdherence fromString(String text) {
        if (text == null) return NOT_RATED;
        switch (text) {
            case "PERFECT_PLAN": return PERFECT_EXECUTION;
            case "GOOD_PLAN": case "OK_PLAN": return MINOR_DEVIATION;
            case "BAD_PLAN": return MAJOR_DEVIATION;
        }
        for (PlanAdherence pa : values()) {
            if (pa.name().equalsIgnoreCase(text) || pa.toString().equalsIgnoreCase(text)) {
                return pa;
            }
        }
        return NOT_RATED;
    }
}