package com.EcoChartPro.utils;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdKeyDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String LAST_REVIEWED_MONTH_KEY = "lastReviewedMonth";
    private static final String LIVE_AUTO_SAVE_FILE_NAME = "live_autosave.json";

    /**
     * [NEW] A wrapper class to embed session type information into saved JSON files,
     * specifically for differentiating Live sessions from Replay sessions.
     */
    public static class SessionWrapper {
        public SessionType sessionType;
        public ReplaySessionState state;

        public SessionWrapper() {} // For Jackson deserialization

        public SessionWrapper(SessionType sessionType, ReplaySessionState state) {
            this.sessionType = sessionType;
            this.state = state;
        }
    }


    private SessionManager() {
        this.objectMapper = new ObjectMapper();

        this.objectMapper.getFactory().setStreamReadConstraints(
            StreamReadConstraints.builder().maxNumberLength(Integer.MAX_VALUE).build()
        );

        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.objectMapper.registerModule(new JavaTimeModule());

        SimpleModule ecoChartProModule = createAwtModule();
        ecoChartProModule.addKeyDeserializer(Timeframe.class, new TimeframeKeyDeserializer());
        
        this.objectMapper.registerModule(ecoChartProModule);
    }
    
    private static class TimeframeKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            Timeframe tf = Timeframe.fromString(key);
            if (tf == null) {
                throw ctxt.weirdKeyException(Timeframe.class, key, "Not a valid Timeframe representation");
            }
            return tf;
        }
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
    
    public Optional<ReplaySessionState> getLatestSessionState() {
        ReplaySessionState latestState = null;
        File latestFile = null;

        Optional<File> lastManualFileOpt = getLastSessionPath().map(Path::toFile);
        Optional<File> autoSaveFileOpt = AppDataManager.getAutoSaveFilePath().filter(Files::exists).map(Path::toFile);
        
        if (lastManualFileOpt.isPresent() && autoSaveFileOpt.isPresent()) {
            latestFile = lastManualFileOpt.get().lastModified() > autoSaveFileOpt.get().lastModified()
                         ? lastManualFileOpt.get() : autoSaveFileOpt.get();
        } else if (lastManualFileOpt.isPresent()) {
            latestFile = lastManualFileOpt.get();
        } else if (autoSaveFileOpt.isPresent()) {
            latestFile = autoSaveFileOpt.get();
        }

        if (latestFile != null) {
            try {
                latestState = loadSession(latestFile);
            } catch (IOException e) {
                logger.error("Failed to load latest session file: {}", latestFile.getAbsolutePath(), e);
            }
        }
        
        return Optional.ofNullable(latestState);
    }
    
    public void saveSession(ReplaySessionState state, File file, boolean isReplayMode) throws IOException {
        try {
            Object objectToSave;
            if (isReplayMode) {
                objectToSave = state; // Save raw state for replay files for backward compatibility
            } else {
                objectToSave = new SessionWrapper(SessionType.LIVE, state); // Wrap for live files
            }
            objectMapper.writeValue(file, objectToSave);

            if (!file.getName().equals("autosave.json")) {
                String sessionType = isReplayMode ? "Replay" : "Live";
                logger.info("{} session successfully saved to: {}", sessionType, file.getAbsolutePath());
            }
            setLastSessionPath(file.toPath());

            if (state.symbolStates() != null) {
                state.symbolStates().forEach(SymbolProgressCache.getInstance()::updateProgressForSymbol);
            }

        } catch (IOException e) {
            String sessionType = isReplayMode ? "replay" : "live";
            logger.error("Failed to save {} session to file: {}", sessionType, file.getAbsolutePath(), e);
            throw e; 
        }
    }

    public ReplaySessionState loadSession(File file) throws IOException {
        try {
            JsonNode rootNode = objectMapper.readTree(file);

            if (rootNode.has("sessionType") && rootNode.has("state")) {
                String typeStr = rootNode.get("sessionType").asText();
                if (SessionType.LIVE.name().equals(typeStr)) {
                    throw new IOException("This is a Live session file. It cannot be loaded as a Replay session.");
                }
                // If type is REPLAY, parse the inner state.
                JsonNode stateNode = rootNode.get("state");
                return objectMapper.treeToValue(stateNode, ReplaySessionState.class);
            } else {
                // This is an unwrapped file, either an old format or a modern replay format.
                if (rootNode.has("dataSourceSymbol")) {
                    logger.info("Detected old session file format. Converting to new multi-symbol format...");
                    return convertOldStateToNew(rootNode);
                } else {
                    return objectMapper.treeToValue(rootNode, ReplaySessionState.class);
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to load or parse replay session from file: {}", file.getAbsolutePath(), e);
            throw new IOException("The file format is invalid or corrupted: " + file.getName(), e);
        }
    }
    
    /**
     * [NEW] Loads a session state specifically from a file saved in Live mode.
     * @param file The file to load.
     * @return The ReplaySessionState contained within the file.
     * @throws IOException if the file is not a valid Live session file or is corrupted.
     */
    public ReplaySessionState loadStateFromLiveFile(File file) throws IOException {
        try {
            SessionWrapper wrapper = objectMapper.readValue(file, SessionWrapper.class);
            if (wrapper.sessionType != SessionType.LIVE) {
                throw new IOException("This is not a Live session file.");
            }
            return wrapper.state;
        } catch (MismatchedInputException e) {
            throw new IOException("The selected file is not a valid Live session file. It may be a Replay session file.", e);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse live session wrapper from file: {}", file.getAbsolutePath(), e);
            throw new IOException("The file format is invalid or corrupted: " + file.getName(), e);
        }
    }

    private ReplaySessionState convertOldStateToNew(JsonNode oldNode) throws IOException {
        String symbol = oldNode.path("dataSourceSymbol").asText();
        int headIndex = oldNode.path("replayHeadIndex").asInt();
        BigDecimal balance = new BigDecimal(oldNode.path("accountBalance").asText("0"));
        Instant lastTimestamp = objectMapper.treeToValue(oldNode.path("lastTimestamp"), Instant.class);

        List<Position> openPositions = objectMapper.convertValue(oldNode.path("openPositions"), new TypeReference<>() {});
        List<Order> pendingOrders = objectMapper.convertValue(oldNode.path("pendingOrders"), new TypeReference<>() {});
        List<Trade> tradeHistory = objectMapper.convertValue(oldNode.path("tradeHistory"), new TypeReference<>() {});
        List<DrawingObject> drawings = objectMapper.convertValue(oldNode.path("drawings"), new TypeReference<>() {});
        
        SymbolSessionState symbolState = new SymbolSessionState(
            headIndex,
            openPositions != null ? openPositions : Collections.emptyList(),
            pendingOrders != null ? pendingOrders : Collections.emptyList(),
            tradeHistory != null ? tradeHistory : Collections.emptyList(),
            drawings != null ? drawings : Collections.emptyList(),
            lastTimestamp
        );

        Map<String, SymbolSessionState> symbolStatesMap = new HashMap<>();
        symbolStatesMap.put(symbol, symbolState);

        return new ReplaySessionState(balance, symbol, symbolStatesMap);
    }

    public Path getSessionsDirectory() throws IOException {
        Path appDataDir = AppDataManager.getAppDataDirectory();
        Path sessionsDir = appDataDir.resolve(SESSIONS_DIR_NAME);
        if (Files.notExists(sessionsDir)) {
            Files.createDirectories(sessionsDir);
            logger.info("Created session save directory at: {}", sessionsDir.toAbsolutePath());
        }
        return sessionsDir;
    }
    
    public void setLastSessionPath(Path path) {
        updateProperty(LAST_SESSION_PATH_KEY, path.toAbsolutePath().toString());
    }
    
    public void setLastReviewedMonth(YearMonth month) {
        updateProperty(LAST_REVIEWED_MONTH_KEY, month.toString());
    }
    
    public Optional<Path> getLastSessionPath() {
        Optional<String> pathStrOpt = readProperty(LAST_SESSION_PATH_KEY);
        if (pathStrOpt.isPresent()) {
            Path sessionPath = Paths.get(pathStrOpt.get());
            if (Files.exists(sessionPath)) {
                return Optional.of(sessionPath);
            } else {
                logger.warn("Last session path found in config, but file no longer exists: {}", sessionPath);
            }
        }
        return Optional.empty();
    }
    
    public Optional<YearMonth> getLastReviewedMonth() {
        return readProperty(LAST_REVIEWED_MONTH_KEY).map(YearMonth::parse);
    }
    
    private void updateProperty(String key, String value) {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isEmpty()) {
            logger.error("Cannot set property '{}', config file path is not available.", key);
            return;
        }
        Path configPath = configPathOpt.get();
        Properties props = new Properties();

        if (Files.exists(configPath)) {
            try (FileInputStream in = new FileInputStream(configPath.toFile())) {
                props.load(in);
            } catch (IOException e) {
                logger.warn("Could not read existing properties file before writing. A new one will be created.", e);
            }
        }

        props.setProperty(key, value);

        try (FileOutputStream out = new FileOutputStream(configPath.toFile())) {
            props.store(out, "Eco Chart Pro Application State");
            if (!key.equals(LAST_SESSION_PATH_KEY)) { 
                logger.info("Updated property '{}' to: {}", key, value);
            }
        } catch (IOException e) {
            logger.error("Failed to save application state to properties file.", e);
        }
    }

    private Optional<String> readProperty(String key) {
        Optional<Path> configPathOpt = AppDataManager.getAppConfigPath();
        if (configPathOpt.isEmpty() || Files.notExists(configPathOpt.get())) {
            return Optional.empty();
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(configPathOpt.get().toFile())) {
            props.load(in);
            String value = props.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                return Optional.of(value);
            }
        } catch (IOException e) {
            logger.error("Failed to load application state from properties file.", e);
        }
        return Optional.empty();
    }
    
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

    public Optional<File> getLiveAutoSaveFilePath() {
        try {
            return Optional.of(getSessionsDirectory().resolve(LIVE_AUTO_SAVE_FILE_NAME).toFile());
        } catch (IOException e) {
            logger.error("Could not get live auto-save file path", e);
            return Optional.empty();
        }
    }

    public void saveLiveSession(ReplaySessionState state) throws IOException {
        Optional<File> liveSaveFile = getLiveAutoSaveFilePath();
        if (liveSaveFile.isPresent()) {
            objectMapper.writeValue(liveSaveFile.get(), state);
        } else {
            throw new IOException("Could not determine path for live session auto-save.");
        }
    }

    public ReplaySessionState loadLiveSession() throws IOException {
        Optional<File> liveSaveFile = getLiveAutoSaveFilePath();
        if (liveSaveFile.isPresent() && liveSaveFile.get().exists()) {
            return objectMapper.readValue(liveSaveFile.get(), ReplaySessionState.class);
        } else {
            throw new IOException("No live session file found to load.");
        }
    }

    public void deleteLiveAutoSaveFile() {
        getLiveAutoSaveFilePath().ifPresent(file -> {
            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Live auto-save session file deleted successfully.");
                } else {
                    logger.error("Failed to delete live auto-save session file.");
                }
            }
        });
    }

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