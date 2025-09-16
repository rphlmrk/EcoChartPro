package com.EcoChartPro.core.settings;

import com.EcoChartPro.utils.AppDataManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Manages the loading, saving, and editing of a user-defined list of common trading mistakes.
 */
public final class MistakeManager {

    private static final Logger logger = LoggerFactory.getLogger(MistakeManager.class);
    private static volatile MistakeManager instance;
    private final ObjectMapper objectMapper;
    private static final String MISTAKES_FILE_NAME = "mistakes.json";
    private final List<String> mistakes = new ArrayList<>();

    private MistakeManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        List<String> loaded = loadMistakes();
        this.mistakes.addAll(loaded);
    }

    public static MistakeManager getInstance() {
        if (instance == null) {
            synchronized (MistakeManager.class) {
                if (instance == null) {
                    instance = new MistakeManager();
                }
            }
        }
        return instance;
    }

    /**
     * @return A defensive copy of the current list of mistakes.
     */
    public List<String> getMistakes() {
        return new ArrayList<>(mistakes);
    }

    /**
     * Adds a new mistake to the list if it's valid and doesn't already exist.
     * @param mistake The mistake text to add.
     */
    public void addMistake(String mistake) {
        if (mistake != null && !mistake.isBlank() && !mistakes.contains(mistake)) {
            mistakes.add(mistake);
            saveMistakes();
        }
    }

    /**
     * Updates an existing mistake at a specific index.
     * @param index The index of the mistake to update.
     * @param newMistake The new text for the mistake.
     */
    public void updateMistake(int index, String newMistake) {
        if (index >= 0 && index < mistakes.size() && newMistake != null && !newMistake.isBlank()) {
            mistakes.set(index, newMistake);
            saveMistakes();
        }
    }

    /**
     * Deletes a mistake from the list at a specific index.
     * @param index The index of the mistake to delete.
     */
    public void deleteMistake(int index) {
        if (index >= 0 && index < mistakes.size()) {
            mistakes.remove(index);
            saveMistakes();
        }
    }

    private List<String> loadMistakes() {
        try {
            Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(MISTAKES_FILE_NAME);
            if (filePathOpt.isPresent() && Files.exists(filePathOpt.get())) {
                Path filePath = filePathOpt.get();
                byte[] jsonData = Files.readAllBytes(filePath);
                List<String> loaded = objectMapper.readValue(jsonData, new TypeReference<>() {});
                logger.info("Successfully loaded {} custom mistakes from {}", loaded.size(), filePath);
                return loaded;
            }
        } catch (IOException e) {
            logger.error("Failed to load custom mistakes from file.", e);
        }
        
        logger.info("No mistakes file found. Creating default list.");
        // Create a separate list for defaults to avoid modifying the instance field during initialization.
        List<String> defaultMistakes = createDefaultMistakes();
        // Save the defaults so the file exists for next time
        saveMistakesToFile(defaultMistakes);
        return defaultMistakes;
    }

    private void saveMistakes() {
        saveMistakesToFile(this.mistakes);
    }

    // Create a separate method to handle saving any list, which can be called during initialization.
    private void saveMistakesToFile(List<String> mistakesToSave) {
        try {
            Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(MISTAKES_FILE_NAME);
            if (filePathOpt.isPresent()) {
                Path filePath = filePathOpt.get();
                byte[] jsonData = objectMapper.writeValueAsBytes(mistakesToSave);
                Files.write(filePath, jsonData);
                logger.info("Successfully saved {} custom mistakes to {}", mistakesToSave.size(), filePath);
            }
        } catch (IOException e) {
            logger.error("Failed to save custom mistakes to file.", e);
        }
    }

    private List<String> createDefaultMistakes() {
        return new ArrayList<>(Arrays.asList(
            "No Mistakes Made", "Chased Entry", "FOMO (Fear of Missing Out)", "Revenge Trading",
            "Moved Stop Loss", "Exited Too Early", "Held a Loser Too Long",
            "Ignored Trading Plan", "Oversized Position", "Undersized Position",
            "Fat Finger Error", "Distracted Trading", "System Not Followed"
        ));
    }
}