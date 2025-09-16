package com.EcoChartPro.utils;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font; 
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

/**
 * A singleton service responsible for saving and loading ReplaySessionState
 * objects to and from JSON files. This class handles the complexities of
 * serializing Java and AWT objects.
 */
public final class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static volatile SessionManager instance;
    private final ObjectMapper objectMapper;
    private static final String SESSIONS_DIR_NAME = "sessions";
    private static final String LAST_SESSION_PATH_KEY = "lastSessionPath";

    private SessionManager() {
        this.objectMapper = new ObjectMapper();
        // Configure for pretty printing JSON output
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Don't fail on unknown properties during deserialization
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // Register module for Java 8 Time types (e.g., Instant)
        this.objectMapper.registerModule(new JavaTimeModule());
        // Register our custom module for serializing/deserializing AWT types
        this.objectMapper.registerModule(createAwtModule());
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Saves the given session state to the specified file as JSON.
     * Also records this file as the most recently saved session.
     *
     * @param state The ReplaySessionState object to save.
     * @param file  The file to save the session to.
     * @throws IOException if an error occurs during file writing or serialization.
     */
    public void saveSession(ReplaySessionState state, File file) throws IOException {
        try {
            objectMapper.writeValue(file, state);
            logger.info("Replay session successfully saved to: {}", file.getAbsolutePath());
            setLastSessionPath(file.toPath());
        } catch (IOException e) {
            logger.error("Failed to save replay session to file: {}", file.getAbsolutePath(), e);
            throw e; // Re-throw to allow the caller (UI) to handle it.
        }
    }

    /**
     * Loads a session state from the specified JSON file.
     *
     * @param file The file to load the session from.
     * @return The deserialized ReplaySessionState object.
     * @throws IOException if an error occurs during file reading or deserialization.
     */
    public ReplaySessionState loadSession(File file) throws IOException {
        try {
            ReplaySessionState state = objectMapper.readValue(file, ReplaySessionState.class);
            logger.info("Replay session successfully loaded from: {}", file.getAbsolutePath());
            return state;
        } catch (IOException e) {
            logger.error("Failed to load replay session from file: {}", file.getAbsolutePath(), e);
            throw e; // Re-throw to allow the caller to handle it.
        }
    }

    /**
     * Gets the dedicated directory for storing saved session files.
     * It creates the directory if it doesn't already exist.
     *
     * @return A Path object pointing to the sessions directory.
     * @throws IOException if the directory cannot be created.
     */
    public Path getSessionsDirectory() throws IOException {
        Path appDataDir = AppDataManager.getAppDataDirectory();
        Path sessionsDir = appDataDir.resolve(SESSIONS_DIR_NAME);
        if (Files.notExists(sessionsDir)) {
            Files.createDirectories(sessionsDir);
            logger.info("Created session save directory at: {}", sessionsDir.toAbsolutePath());
        }
        return sessionsDir;
    }

    /**
     * New method to save the last session path to a properties file.
     * @param path The path of the session file that was just saved.
     */
    public void setLastSessionPath(Path path) {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isEmpty()) {
            logger.error("Cannot set last session path, config file path is not available.");
            return;
        }
        Path configPath = configPathOpt.get();
        Properties props = new Properties();

        // Read existing properties first to not overwrite others
        if (Files.exists(configPath)) {
            try (FileInputStream in = new FileInputStream(configPath.toFile())) {
                props.load(in);
            } catch (IOException e) {
                logger.warn("Could not read existing properties file before writing. A new one will be created.", e);
            }
        }

        props.setProperty(LAST_SESSION_PATH_KEY, path.toAbsolutePath().toString());

        try (FileOutputStream out = new FileOutputStream(configPath.toFile())) {
            props.store(out, "Eco Chart Pro Application State");
            logger.info("Set last session path to: {}", path.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save application state to properties file.", e);
        }
    }

    /**
     * New method to get the last session path from the properties file.
     * @return An Optional containing the path if it exists and is valid, otherwise empty.
     */
    public Optional<Path> getLastSessionPath() {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isEmpty() || Files.notExists(configPathOpt.get())) {
            logger.info("No config file found, cannot get last session path.");
            return Optional.empty();
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPathOpt.get().toFile())) {
            props.load(in);
            String pathStr = props.getProperty(LAST_SESSION_PATH_KEY);
            if (pathStr != null && !pathStr.trim().isEmpty()) {
                Path sessionPath = Paths.get(pathStr);
                if (Files.exists(sessionPath)) {
                    logger.info("Found last session path: {}", sessionPath);
                    return Optional.of(sessionPath);
                } else {
                    logger.warn("Last session path found in config, but file no longer exists: {}", sessionPath);
                    return Optional.empty();
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load application state from properties file.", e);
        }
        return Optional.empty();
    }
    
    /**
     * New method to delete the auto-save file.
     */
    public void deleteAutoSaveFile() {
        AppDataManager.getAutoSaveFilePath().ifPresent(path -> {
            try {
                if (Files.exists(path)) {
                    Files.delete(path);
                    logger.info("Auto-save session file deleted successfully.");
                }
            } catch (IOException e) {
                logger.error("Failed to delete auto-save session file at: {}", path, e);
            }
        });
    }

    /**
     * Creates a custom Jackson module to handle non-standard types from AWT.
     */
    private SimpleModule createAwtModule() {
        SimpleModule module = new SimpleModule("AwtModule");
        module.addSerializer(Color.class, new ColorSerializer());
        module.addDeserializer(Color.class, new ColorDeserializer());
        module.addSerializer(BasicStroke.class, new BasicStrokeSerializer());
        module.addDeserializer(BasicStroke.class, new BasicStrokeDeserializer());
        module.addSerializer(Font.class, new FontSerializer());
        module.addDeserializer(Font.class, new FontDeserializer());
        return module;
    }

    // --- Custom Serializer/Deserializer Implementations ---

    public static class ColorSerializer extends JsonSerializer<Color> {
        @Override
        public void serialize(Color value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeString(String.format("#%02x%02x%02x%02x", value.getRed(), value.getGreen(), value.getBlue(), value.getAlpha()));
        }
    }

    public static class ColorDeserializer extends JsonDeserializer<Color> {
        @Override
        public Color deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String hex = p.getText().substring(1);
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = hex.length() > 6 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;
            return new Color(r, g, b, a);
        }
    }

    public static class BasicStrokeSerializer extends JsonSerializer<BasicStroke> {
        @Override
        public void serialize(BasicStroke value, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeStartObject();
            gen.writeNumberField("width", value.getLineWidth());
            gen.writeNumberField("endCap", value.getEndCap());
            gen.writeNumberField("lineJoin", value.getLineJoin());
            gen.writeNumberField("miterLimit", value.getMiterLimit());
            gen.writeArrayFieldStart("dashArray");
            if (value.getDashArray() != null) {
                for (float f : value.getDashArray()) gen.writeNumber(f);
            }
            gen.writeEndArray();
            gen.writeNumberField("dashPhase", value.getDashPhase());
            gen.writeEndObject();
        }
    }

    public static class BasicStrokeDeserializer extends JsonDeserializer<BasicStroke> {
        @Override
        public BasicStroke deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            float width = (float) node.get("width").asDouble();
            int cap = node.get("endCap").asInt();
            int join = node.get("lineJoin").asInt();
            float miterLimit = (float) node.get("miterLimit").asDouble();
            float dashPhase = (float) node.get("dashPhase").asDouble();
            JsonNode dashNode = node.get("dashArray");
            float[] dashArray = null;
            if (dashNode.isArray() && dashNode.size() > 0) {
                dashArray = new float[dashNode.size()];
                for (int i = 0; i < dashNode.size(); i++) dashArray[i] = (float) dashNode.get(i).asDouble();
            }
            return new BasicStroke(width, cap, join, miterLimit, dashArray, dashPhase);
        }
    }

    // New custom serializer for java.awt.Font ---
    public static class FontSerializer extends JsonSerializer<Font> {
        @Override
        public void serialize(Font font, JsonGenerator gen, SerializerProvider sp) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("name", font.getName());
            gen.writeNumberField("style", font.getStyle());
            gen.writeNumberField("size", font.getSize());
            gen.writeEndObject();
        }
    }

    // New custom deserializer for java.awt.Font ---
    public static class FontDeserializer extends JsonDeserializer<Font> {
        @Override
        public Font deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            String name = node.get("name").asText();
            int style = node.get("style").asInt();
            int size = node.get("size").asInt();
            return new Font(name, style, size);
        }
    }
}