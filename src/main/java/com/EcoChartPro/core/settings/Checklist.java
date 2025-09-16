package com.EcoChartPro.core.settings;

import java.util.List;
import java.util.UUID;

public record Checklist(UUID id, String name, List<ChecklistItem> items) {
    @Override
    public String toString() {
        return name; // For display in JComboBox
    }
}