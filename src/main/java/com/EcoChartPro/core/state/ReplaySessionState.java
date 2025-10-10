package com.EcoChartPro.core.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * [REFACTORED] Represents the complete, serializable state of a multi-symbol replay session.
 * This object can be persisted to a file (e.g., JSON) and loaded
 * to resume a session later. It now acts as a container for the states of
 * individual symbols and the global account balance.
 */
public record ReplaySessionState(
    /**
     * The single, shared account balance for the entire session.
     */
    BigDecimal accountBalance,
    
    /**
     * The symbol that was active when the session was last saved.
     * This allows the UI to restore the correct view on load.
     */
    String lastActiveSymbol,
    
    /**
     * A map containing the individual state for each symbol interacted with during the session.
     * The key is the symbol identifier string (e.g., "btcusdt").
     */
    Map<String, SymbolSessionState> symbolStates

) implements Serializable {

    // Jackson constructor for robust deserialization
    @JsonCreator
    public ReplaySessionState(
        @JsonProperty("accountBalance") BigDecimal accountBalance,
        @JsonProperty("lastActiveSymbol") String lastActiveSymbol,
        @JsonProperty("symbolStates") Map<String, SymbolSessionState> symbolStates
    ) {
        this.accountBalance = accountBalance;
        this.lastActiveSymbol = lastActiveSymbol;
        this.symbolStates = symbolStates;
    }
}