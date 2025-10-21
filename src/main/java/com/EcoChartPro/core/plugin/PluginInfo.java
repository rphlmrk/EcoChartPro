package com.EcoChartPro.core.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PluginInfo(
    String id,
    String name,
    String author,
    String version,
    String description,
    List<String> tags,
    String ecoChartProVersion,
    String sourceUrl,
    String iconUrl
) {}