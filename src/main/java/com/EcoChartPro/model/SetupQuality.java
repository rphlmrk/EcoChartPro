package com.EcoChartPro.model;

/**
 * Represents a subjective rating for the quality of a trade setup.
 * This is determined by the trader at the time of journaling.
 */
public enum SetupQuality {
    NOT_RATED("Not Rated"),
    A_PLUS("A+ Setup"),
    A("A Setup"),
    B("B Setup"),
    C("C Setup"),
    F("F Setup");

    private final String displayName;

    SetupQuality(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}