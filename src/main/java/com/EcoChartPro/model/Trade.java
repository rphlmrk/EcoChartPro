package com.EcoChartPro.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Now includes Jackson annotations for correct serialization/deserialization.
 */
public final class Trade {
    private final UUID id;
    private final Symbol symbol;
    private final TradeDirection direction;
    private final Instant entryTime;
    private final BigDecimal entryPrice;
    private final Instant exitTime;
    private final BigDecimal exitPrice;
    private final BigDecimal quantity;
    private final BigDecimal profitAndLoss;
    private final boolean planFollowed;

    // Detailed Journaling Fields ---
    private String notes;
    private List<String> tags;
    private PlanAdherence planAdherence;
    private EmotionalState emotionalState;
    private List<String> identifiedMistakes;
    private String lessonsLearned;
    private UUID checklistId;
    private SetupQuality setupQuality;

    @JsonCreator
    public Trade(
            @JsonProperty("id") UUID id,
            @JsonProperty("symbol") Symbol symbol,
            @JsonProperty("direction") TradeDirection direction,
            @JsonProperty("entryTime") Instant entryTime,
            @JsonProperty("entryPrice") BigDecimal entryPrice,
            @JsonProperty("exitTime") Instant exitTime,
            @JsonProperty("exitPrice") BigDecimal exitPrice,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("profitAndLoss") BigDecimal profitAndLoss,
            @JsonProperty("planFollowed") boolean planFollowed,
            @JsonProperty("notes") String notes,
            @JsonProperty("tags") List<String> tags,
            @JsonProperty("planAdherence") PlanAdherence planAdherence,
            @JsonProperty("emotionalState") EmotionalState emotionalState,
            @JsonProperty("identifiedMistakes") List<String> identifiedMistakes,
            @JsonProperty("lessonsLearned") String lessonsLearned,
            @JsonProperty("checklistId") UUID checklistId,
            @JsonProperty("setupQuality") SetupQuality setupQuality
    ) {
        this.id = id;
        this.symbol = symbol;
        this.direction = direction;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.exitTime = exitTime;
        this.quantity = quantity;
        this.profitAndLoss = profitAndLoss;
        this.planFollowed = planFollowed;
        this.notes = notes;
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        this.planAdherence = (planAdherence != null) ? planAdherence : PlanAdherence.NOT_RATED;
        this.emotionalState = (emotionalState != null) ? emotionalState : EmotionalState.NEUTRAL;
        this.identifiedMistakes = (identifiedMistakes != null) ? new ArrayList<>(identifiedMistakes) : new ArrayList<>();
        this.lessonsLearned = lessonsLearned;
        this.checklistId = checklistId;
        this.setupQuality = (setupQuality != null) ? setupQuality : SetupQuality.NOT_RATED;
    }

    // --- Overloaded constructor for creating new trades before journaling ---
    public Trade(UUID id, Symbol symbol, TradeDirection direction, Instant entryTime, BigDecimal entryPrice,
                 Instant exitTime, BigDecimal exitPrice, BigDecimal quantity, BigDecimal profitAndLoss,
                 boolean planFollowed, String notes, List<String> tags, UUID checklistId) {
        this(id, symbol, direction, entryTime, entryPrice, exitTime, exitPrice, quantity, profitAndLoss,
             planFollowed, notes, tags, PlanAdherence.NOT_RATED, EmotionalState.NEUTRAL, new ArrayList<>(), "", checklistId, SetupQuality.NOT_RATED);
    }

    // --- Overloaded constructor for trade creation from older session files without new fields ---
    public Trade(UUID id, Symbol symbol, TradeDirection direction, Instant entryTime, BigDecimal entryPrice,
                 Instant exitTime, BigDecimal exitPrice, BigDecimal quantity, BigDecimal profitAndLoss,
                 boolean planFollowed) {
        this(id, symbol, direction, entryTime, entryPrice, exitTime, exitPrice, quantity, profitAndLoss,
             planFollowed, "", new ArrayList<>(), PlanAdherence.NOT_RATED, EmotionalState.NEUTRAL, new ArrayList<>(), "", null, SetupQuality.NOT_RATED);
    }


    @JsonProperty("id")
    public UUID id() { return id; }

    @JsonProperty("symbol")
    public Symbol symbol() { return symbol; }

    @JsonProperty("direction")
    public TradeDirection direction() { return direction; }

    @JsonProperty("entryTime")
    public Instant entryTime() { return entryTime; }

    @JsonProperty("entryPrice")
    public BigDecimal entryPrice() { return entryPrice; }

    @JsonProperty("exitTime")
    public Instant exitTime() { return exitTime; }

    @JsonProperty("exitPrice")
    public BigDecimal exitPrice() { return exitPrice; }

    @JsonProperty("quantity")
    public BigDecimal quantity() { return quantity; }

    @JsonProperty("profitAndLoss")
    public BigDecimal profitAndLoss() { return profitAndLoss; }

    @JsonProperty("planFollowed")
    public boolean planFollowed() { return planFollowed; }

    @JsonProperty("notes")
    public String notes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @JsonProperty("tags")
    public List<String> tags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    @JsonProperty("planAdherence")
    public PlanAdherence planAdherence() { return planAdherence; }
    public void setPlanAdherence(PlanAdherence planAdherence) { this.planAdherence = planAdherence; }

    @JsonProperty("emotionalState")
    public EmotionalState emotionalState() { return emotionalState; }
    public void setEmotionalState(EmotionalState emotionalState) { this.emotionalState = emotionalState; }

    @JsonProperty("identifiedMistakes")
    public List<String> identifiedMistakes() { return identifiedMistakes; }
    public void setIdentifiedMistakes(List<String> identifiedMistakes) { this.identifiedMistakes = identifiedMistakes; }

    @JsonProperty("lessonsLearned")
    public String lessonsLearned() { return lessonsLearned; }
    public void setLessonsLearned(String lessonsLearned) { this.lessonsLearned = lessonsLearned; }

    @JsonProperty("checklistId")
    public UUID checklistId() { return checklistId; }
    public void setChecklistId(UUID checklistId) { this.checklistId = checklistId; }

    @JsonProperty("setupQuality")
    public SetupQuality setupQuality() { return setupQuality; }
    public void setSetupQuality(SetupQuality setupQuality) { this.setupQuality = setupQuality; }
}