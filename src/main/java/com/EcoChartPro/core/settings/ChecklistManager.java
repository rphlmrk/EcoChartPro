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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ChecklistManager {

    private static final Logger logger = LoggerFactory.getLogger(ChecklistManager.class);
    private static volatile ChecklistManager instance;
    private final ObjectMapper objectMapper;
    private static final String CHECKLISTS_FILE_NAME = "checklists.json";
    private final List<Checklist> checklists = new ArrayList<>();
    
    private ChecklistManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        List<Checklist> loadedChecklists = loadChecklists();
        if (loadedChecklists != null) {
            this.checklists.addAll(loadedChecklists);
        }
    }

    public static ChecklistManager getInstance() {
        if (instance == null) {
            synchronized (ChecklistManager.class) {
                if (instance == null) {
                    instance = new ChecklistManager();
                }
            }
        }
        return instance;
    }

    public List<Checklist> getChecklists() {
        return new ArrayList<>(checklists);
    }

    public void addChecklist(Checklist checklist) {
        if (checklist != null) {
            checklists.add(checklist);
            saveChecklists();
        }
    }

    public void updateChecklist(Checklist updatedChecklist) {
        if (updatedChecklist == null) return;
        for (int i = 0; i < checklists.size(); i++) {
            if (checklists.get(i).id().equals(updatedChecklist.id())) {
                checklists.set(i, updatedChecklist);
                saveChecklists();
                return;
            }
        }
    }

    public void deleteChecklist(UUID id) {
        checklists.removeIf(c -> c.id().equals(id));
        saveChecklists();
    }

    private List<Checklist> loadChecklists() {
        try {
            Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(CHECKLISTS_FILE_NAME);
            if (filePathOpt.isPresent() && Files.exists(filePathOpt.get())) {
                Path filePath = filePathOpt.get();
                byte[] jsonData = Files.readAllBytes(filePath);
                List<Checklist> loaded = objectMapper.readValue(jsonData, new TypeReference<>() {});
                logger.info("Successfully loaded {} checklists from {}", loaded.size(), filePath);
                return loaded;
            }
        } catch (IOException e) {
            logger.error("Failed to load checklists from file.", e);
        }
        
        logger.info("No checklists file found. Creating and saving default set.");
        List<Checklist> defaultChecklists = createDefaultChecklists();
        saveChecklistsToFile(defaultChecklists);
        return defaultChecklists;
    }

    private void saveChecklists() {
        saveChecklistsToFile(this.checklists);
    }

    private void saveChecklistsToFile(List<Checklist> checklistsToSave) {
        try {
            Optional<Path> filePathOpt = AppDataManager.getConfigFilePath(CHECKLISTS_FILE_NAME);
            if (filePathOpt.isPresent()) {
                Path filePath = filePathOpt.get();
                byte[] jsonData = objectMapper.writeValueAsBytes(checklistsToSave);
                Files.write(filePath, jsonData);
                logger.info("Successfully saved {} checklists to {}", checklistsToSave.size(), filePath);
            }
        } catch (IOException e) {
            logger.error("Failed to save checklists to file.", e);
        }
    }

    private List<Checklist> createDefaultChecklists() {
        List<Checklist> defaultList = new ArrayList<>();

        defaultList.add(createCoreStrategyChecklist());
        defaultList.add(createBreakoutStrategyChecklist());
        defaultList.add(createReversalStrategyChecklist());
        defaultList.add(createDoubleTopChecklist());
        defaultList.add(createMacdStrategyChecklist());
        defaultList.add(createMaCrossoverStrategyChecklist());
        defaultList.add(createScalpingChecklist());

        return defaultList;
    }

    // --- Helper methods for creating each default checklist ---

    private Checklist createCoreStrategyChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. HTF Direction", "Identify the Higher Timeframe trend (e.g., Daily, 4H). Are we bullish or bearish overall?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Identify Key HTF Level", "Mark a significant HTF support/resistance or supply/demand zone."));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. LTF Change of Character (CHOCH)", "On a Lower Timeframe (e.g., 15m, 5m), did price show a clear break of the recent micro-structure, signaling a potential reversal?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Entry Confirmation", "Enter on a pullback to the order block/breaker block created by the CHOCH."));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Stop Loss Placement", "Place stop loss behind the swing high/low that caused the CHOCH."));
        items.add(new ChecklistItem(UUID.randomUUID(), "6. Target", "Aim for the next significant HTF liquidity zone. Is the Risk/Reward ratio at least 2:1?"));
        return new Checklist(UUID.randomUUID(), "Core Strategy (CHOCH + Pullback)", items);
    }

    private Checklist createBreakoutStrategyChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. Identify Key Level/Range", "Is there a clear support/resistance, trendline, or consolidation range (e.g., box, triangle)?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Buildup Confirmation", "Is price consolidating tightly near the level before the breakout? (Reduces fakeouts)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Strong Breakout Candle", "Did the candle close decisively beyond the key level with increased volume, not just a wick?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Entry Technique", "A: Enter on breakout candle close. B: Wait for a retest of the broken level before entering."));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Stop Loss Placement", "Is the stop loss placed logically on the other side of the broken structure or within the previous range?"));
        return new Checklist(UUID.randomUUID(), "Strategy: Breakout", items);
    }

    private Checklist createReversalStrategyChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. Established Trend", "Is there a clear, mature trend that appears to be losing momentum? (e.g., divergence, lower volume on new highs/lows)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Reversal Pattern", "Has a classic reversal pattern formed? (e.g., Head & Shoulders, Double/Triple Top/Bottom, Wedge)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Confirmation Signal", "Has the neckline or key support/resistance of the pattern been broken with conviction?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Entry Point", "Enter on the break or wait for a pullback to the broken neckline/level."));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Risk Management", "Is stop loss placed above the head/high of the pattern? Is the target based on the pattern's measured move?"));
        return new Checklist(UUID.randomUUID(), "Strategy: Reversal Pattern", items);
    }

    private Checklist createDoubleTopChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. Initial Up-Trend", "Was there a clear uptrend leading into the pattern?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Two Distinct Peaks", "Are the two tops roughly at the same price level, with a significant pullback in between?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Volume Divergence", "Is the volume on the second peak lower than the first peak? (Indicates weakening buying pressure)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Neckline Break", "Has price closed decisively below the support level (neckline) formed by the trough between the two peaks?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Entry & Stop", "Entry on the neckline break or retest. Stop loss placed above the highest peak of the formation."));
        return new Checklist(UUID.randomUUID(), "Strategy: Double Top / Bottom", items);
    }

    private Checklist createMacdStrategyChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. Primary Trend Filter", "Is the trade aligned with the longer-term trend? (e.g., MACD is above zero line in an uptrend)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Signal: Crossover", "Has the MACD line crossed above the signal line (for long) or below (for short)?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Signal: Divergence", "Is there a bullish (price lower low, MACD higher low) or bearish (price higher high, MACD lower high) divergence?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Confirmation", "Is the crossover/divergence supported by price action (e.g., candle patterns, break of micro-trend)?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Exit Signal", "Plan to exit on a reverse crossover, when price hits a key S/R level, or when R/R target is met."));
        return new Checklist(UUID.randomUUID(), "Strategy: S_MACD", items);
    }

    private Checklist createMaCrossoverStrategyChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. MAs Defined", "Using standard MAs? (e.g., 9/21 EMA, 50/200 SMA)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. Crossover Signal", "For long: Has the faster MA crossed decisively above the slower MA? For short: a cross below?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Price Context", "Is the crossover happening after a period of consolidation or as a pullback in an existing trend? (Avoid choppy markets)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Entry Candle", "Enter on the close of the candle where the crossover is confirmed."));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Stop Loss Placement", "Is the stop placed below the recent swing low (for longs) or above the recent swing high (for shorts)?"));
        return new Checklist(UUID.randomUUID(), "Strategy: Moving Average Crossover", items);
    }

    private Checklist createScalpingChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(new ChecklistItem(UUID.randomUUID(), "1. Low Timeframe", "Are you on a very low timeframe? (e.g., 1m, 5m)"));
        items.add(new ChecklistItem(UUID.randomUUID(), "2. High Liquidity Session", "Is this during a high-volume session (e.g., London/New York overlap) to ensure tight spreads?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "3. Clear Micro-Trend", "Is there a clear, immediate direction in the price action?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "4. Quick Entry Trigger", "Is the entry based on a precise, pre-defined signal (e.g., micro-break, order block tap)?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "5. Tight Risk Management", "Is the stop loss extremely tight and the take profit target small and achievable (e.g., 1:1 or 1.5:1 R/R)?"));
        items.add(new ChecklistItem(UUID.randomUUID(), "6. No Distractions", "Am I fully focused for rapid decision-making?"));
        return new Checklist(UUID.randomUUID(), "Strategy: Scalping", items);
    }
}