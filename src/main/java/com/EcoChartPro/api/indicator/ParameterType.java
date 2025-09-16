package com.EcoChartPro.api.indicator;

/**
 * Defines the type of a user-configurable parameter for an indicator.
 * This determines which UI component is shown in the settings dialog.
 */
public enum ParameterType {
    INTEGER,
    DECIMAL,
    CHOICE,
    BOOLEAN, // NEW
    COLOR    // NEW
}