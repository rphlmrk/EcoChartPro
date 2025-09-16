package com.EcoChartPro.api.indicator;

/**
 * A public API record that defines a user-configurable setting for a custom indicator.
 * This is used to dynamically generate settings dialogs.
 * This is part of the stable API for custom indicator plugins.
 *
 * @param key          The unique key for this parameter (e.g., "period").
 * @param type         The data type of the parameter (e.g., INTEGER, CHOICE).
 * @param defaultValue The default value for the parameter.
 * @param choices      For the CHOICE type, this is the list of available options.
 */
public record Parameter(
    String key,
    ParameterType type,
    Object defaultValue,
    String... choices
) {}