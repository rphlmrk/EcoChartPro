package com.EcoChartPro.core.settings.config;

import com.EcoChartPro.model.drawing.FibonacciRetracementObject;
import com.EcoChartPro.model.drawing.TextProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.*;
import java.util.*;
import java.util.List; // Ambiguity resolved with this explicit import
import java.util.concurrent.ConcurrentHashMap;

public class DrawingConfig {
    
    public enum ToolbarPosition { LEFT, RIGHT }

    public record DrawingToolTemplate(
        UUID id,
        String name,
        Color color,
        BasicStroke stroke,
        boolean showPriceLabel,
        Map<String, Object> specificProps
    ) {}

    private ToolbarPosition drawingToolbarPosition = ToolbarPosition.LEFT;
    private int drawingToolbarWidth = 35;
    private int drawingToolbarHeight = 300;
    private int snapRadius = 10;
    private int drawingHitThreshold = 8;
    private int drawingHandleSize = 8;
    private Map<String, List<DrawingToolTemplate>> toolTemplates = new ConcurrentHashMap<>();
    private Map<String, UUID> activeToolTemplates = new ConcurrentHashMap<>();

    public DrawingConfig() {}

    // --- Getters and Setters ---
    public ToolbarPosition getDrawingToolbarPosition() { return drawingToolbarPosition; }
    public void setDrawingToolbarPosition(ToolbarPosition drawingToolbarPosition) { this.drawingToolbarPosition = drawingToolbarPosition; }
    public int getDrawingToolbarWidth() { return drawingToolbarWidth; }
    public void setDrawingToolbarWidth(int drawingToolbarWidth) { this.drawingToolbarWidth = drawingToolbarWidth; }
    public int getDrawingToolbarHeight() { return drawingToolbarHeight; }
    public void setDrawingToolbarHeight(int drawingToolbarHeight) { this.drawingToolbarHeight = drawingToolbarHeight; }
    public int getSnapRadius() { return snapRadius; }
    public void setSnapRadius(int snapRadius) { this.snapRadius = snapRadius; }
    public int getDrawingHitThreshold() { return drawingHitThreshold; }
    public void setDrawingHitThreshold(int drawingHitThreshold) { this.drawingHitThreshold = drawingHitThreshold; }
    public int getDrawingHandleSize() { return drawingHandleSize; }
    public void setDrawingHandleSize(int drawingHandleSize) { this.drawingHandleSize = drawingHandleSize; }
    public Map<String, List<DrawingToolTemplate>> getToolTemplates() { return toolTemplates; }
    public void setToolTemplates(Map<String, List<DrawingToolTemplate>> toolTemplates) { this.toolTemplates = toolTemplates; }
    public Map<String, UUID> getActiveToolTemplates() { return activeToolTemplates; }
    public void setActiveToolTemplates(Map<String, UUID> activeToolTemplates) { this.activeToolTemplates = activeToolTemplates; }

    // --- Template Management Logic ---
    public List<DrawingToolTemplate> getTemplatesForTool(String toolName) {
        return toolTemplates.getOrDefault(toolName, Collections.emptyList());
    }

    public UUID getActiveTemplateId(String toolName) {
        return activeToolTemplates.get(toolName);
    }

    public DrawingToolTemplate getActiveTemplateForTool(String toolName) {
        UUID activeTemplateId = activeToolTemplates.get(toolName);
        List<DrawingToolTemplate> templates = toolTemplates.get(toolName);

        if (templates != null && !templates.isEmpty()) {
            if (activeTemplateId != null) {
                return templates.stream()
                        .filter(t -> t.id().equals(activeTemplateId))
                        .findFirst()
                        .orElse(templates.get(0)); // Fallback to first if ID is invalid
            }
            return templates.get(0);
        }
        return createDefaultTemplateForTool(toolName);
    }

    public void addTemplate(String toolName, DrawingToolTemplate template) {
        toolTemplates.computeIfAbsent(toolName, k -> new ArrayList<>()).add(template);
    }

    public void updateTemplate(String toolName, DrawingToolTemplate template) {
        List<DrawingToolTemplate> templates = toolTemplates.get(toolName);
        if (templates != null) {
            templates.removeIf(t -> t.id().equals(template.id()));
            templates.add(template);
        }
    }

    public void deleteTemplate(String toolName, UUID templateId) {
        List<DrawingToolTemplate> templates = toolTemplates.get(toolName);
        if (templates != null) {
            templates.removeIf(t -> t.id().equals(templateId));
            if (templateId.equals(activeToolTemplates.get(toolName))) {
                activeToolTemplates.remove(toolName);
            }
        }
    }

    public void setActiveTemplate(String toolName, UUID templateId) {
        activeToolTemplates.put(toolName, templateId);
    }

    @SuppressWarnings("unchecked")
    public Map<Double, FibonacciRetracementObject.FibLevelProperties> getFibRetracementDefaultLevels() {
        DrawingToolTemplate template = getActiveTemplateForTool("FibonacciRetracementObject");
        return (Map<Double, FibonacciRetracementObject.FibLevelProperties>) template.specificProps().get("levels");
    }

    @SuppressWarnings("unchecked")
    public Map<Double, FibonacciRetracementObject.FibLevelProperties> getFibExtensionDefaultLevels() {
        DrawingToolTemplate template = getActiveTemplateForTool("FibonacciExtensionObject");
        return (Map<Double, FibonacciRetracementObject.FibLevelProperties>) template.specificProps().get("levels");
    }

    public Font getToolDefaultFont(String toolName, Font fallback) {
        DrawingToolTemplate template = getActiveTemplateForTool(toolName);
        Object fontProp = template.specificProps().get("font");
        if (fontProp instanceof Font) return (Font) fontProp;
        if (fontProp instanceof Map) {
            try { return new ObjectMapper().convertValue(fontProp, Font.class); }
            catch (Exception e) { return fallback; }
        }
        return fallback;
    }

    public TextProperties getToolDefaultTextProperties(String toolName, TextProperties fallback) {
        DrawingToolTemplate template = getActiveTemplateForTool(toolName);
        Object props = template.specificProps().get("textProperties");
        if (props instanceof TextProperties) return (TextProperties) props;
        if (props instanceof Map) {
            try { return new ObjectMapper().convertValue(props, TextProperties.class); }
            catch (Exception e) { return fallback; }
        }
        return fallback;
    }

    private DrawingToolTemplate createDefaultTemplateForTool(String toolName) {
        Color color;
        BasicStroke stroke;
        boolean showPriceLabel = true;
        Map<String, Object> specificProps = new HashMap<>();

        switch (toolName) {
            case "TrendlineObject" -> { color = new Color(255, 140, 40); stroke = new BasicStroke(2); }
            case "RayObject" -> { color = new Color(255, 140, 40); stroke = new BasicStroke(2); showPriceLabel = false; }
            case "HorizontalLineObject", "HorizontalRayObject" -> { color = new Color(33, 150, 243, 180); stroke = new BasicStroke(2); }
            case "VerticalLineObject" -> { color = new Color(33, 150, 243, 180); stroke = new BasicStroke(2); showPriceLabel = false; }
            case "RectangleObject" -> { color = new Color(33, 150, 243); stroke = new BasicStroke(2); }
            case "FibonacciRetracementObject" -> {
                color = new Color(0, 150, 136, 200); stroke = new BasicStroke(1);
                specificProps.put("levels", createDefaultFibRetracementLevels());
            }
            case "FibonacciExtensionObject" -> {
                color = new Color(76, 175, 80, 200); stroke = new BasicStroke(1);
                specificProps.put("levels", createDefaultFibExtensionLevels());
            }
            case "MeasureToolObject" -> { color = new Color(0, 150, 136); stroke = new BasicStroke(1); showPriceLabel = false; }
            case "ProtectedLevelPatternObject" -> { color = new Color(33, 150, 243); stroke = new BasicStroke(2); showPriceLabel = false; }
            case "TextObject" -> {
                color = Color.WHITE; stroke = new BasicStroke(1); showPriceLabel = false;
                specificProps.put("font", new Font("SansSerif", Font.PLAIN, 14));
                specificProps.put("textProperties", new TextProperties(false, new Color(33, 150, 243, 80), false, new Color(33, 150, 243), true, false));
            }
            default -> { color = Color.GRAY; stroke = new BasicStroke(1); }
        }
        return new DrawingToolTemplate(UUID.randomUUID(), "Default", color, stroke, showPriceLabel, specificProps);
    }
    
    private Map<Double, FibonacciRetracementObject.FibLevelProperties> createDefaultFibRetracementLevels() {
        Map<Double, FibonacciRetracementObject.FibLevelProperties> levels = new TreeMap<>();
        Color defaultColor = new Color(0, 150, 136, 200);
        levels.put(-0.618, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor.darker()));
        levels.put(-0.272, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor.darker()));
        levels.put(0.0, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.382, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.5, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.618, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.786, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(1.0, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        return levels;
    }

    private Map<Double, FibonacciRetracementObject.FibLevelProperties> createDefaultFibExtensionLevels() {
        Map<Double, FibonacciRetracementObject.FibLevelProperties> levels = new TreeMap<>();
        Color defaultColor = new Color(76, 175, 80, 200);
        levels.put(0.0, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.382, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(0.618, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor));
        levels.put(1.0, new FibonacciRetracementObject.FibLevelProperties(true, Color.CYAN));
        levels.put(1.618, new FibonacciRetracementObject.FibLevelProperties(true, defaultColor.darker()));
        return levels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DrawingConfig that = (DrawingConfig) o;
        return drawingToolbarWidth == that.drawingToolbarWidth && drawingToolbarHeight == that.drawingToolbarHeight && snapRadius == that.snapRadius && drawingHitThreshold == that.drawingHitThreshold && drawingHandleSize == that.drawingHandleSize && drawingToolbarPosition == that.drawingToolbarPosition && Objects.equals(toolTemplates, that.toolTemplates) && Objects.equals(activeToolTemplates, that.activeToolTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(drawingToolbarPosition, drawingToolbarWidth, drawingToolbarHeight, snapRadius, drawingHitThreshold, drawingHandleSize, toolTemplates, activeToolTemplates);
    }

    @Override
    public String toString() {
        return "DrawingConfig{" + "drawingToolbarPosition=" + drawingToolbarPosition + ", drawingToolbarWidth=" + drawingToolbarWidth + ", drawingToolbarHeight=" + drawingToolbarHeight + ", snapRadius=" + snapRadius + ", drawingHitThreshold=" + drawingHitThreshold + ", drawingHandleSize=" + drawingHandleSize + ", toolTemplates=" + toolTemplates + ", activeToolTemplates=" + activeToolTemplates + '}';
    }
}