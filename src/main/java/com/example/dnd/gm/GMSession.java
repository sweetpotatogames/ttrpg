package com.example.dnd.gm;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Per-GM session data tracking GM mode state and active possession.
 */
public class GMSession {
    private final UUID playerId;
    private final String playerName;

    // GM mode state
    private boolean gmModeActive = false;

    // Active possession (null if not possessing)
    private PossessionState possessionState = null;

    // Currently selected NPC for quick actions
    private UUID selectedNpcId = null;

    // Session stats
    private int npcsSpawned = 0;
    private int damageDealt = 0;
    private int healingDone = 0;

    public GMSession(@Nonnull UUID playerId, @Nonnull String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    /**
     * Toggle GM mode on/off.
     * @return The new state (true = on, false = off)
     */
    public boolean toggleGmMode() {
        gmModeActive = !gmModeActive;
        return gmModeActive;
    }

    /**
     * Start possessing an NPC.
     */
    public void startPossession(@Nonnull PossessionState state) {
        this.possessionState = state;
    }

    /**
     * End the current possession.
     * @return The previous possession state (for cleanup), or null if not possessing
     */
    @Nullable
    public PossessionState endPossession() {
        PossessionState previous = this.possessionState;
        this.possessionState = null;
        return previous;
    }

    /**
     * Check if currently possessing an NPC.
     */
    public boolean isPossessing() {
        return possessionState != null;
    }

    /**
     * Get the ID of the currently possessed NPC.
     */
    @Nullable
    public UUID getPossessedNpcId() {
        return possessionState != null ? possessionState.getPossessedNpcId() : null;
    }

    // Stats tracking
    public void recordNpcSpawned() { npcsSpawned++; }
    public void recordDamage(int amount) { damageDealt += amount; }
    public void recordHealing(int amount) { healingDone += amount; }

    // Getters and setters
    @Nonnull
    public UUID getPlayerId() { return playerId; }

    @Nonnull
    public String getPlayerName() { return playerName; }

    public boolean isGmModeActive() { return gmModeActive; }
    public void setGmModeActive(boolean active) { this.gmModeActive = active; }

    @Nullable
    public PossessionState getPossessionState() { return possessionState; }

    @Nullable
    public UUID getSelectedNpcId() { return selectedNpcId; }
    public void setSelectedNpcId(@Nullable UUID npcId) { this.selectedNpcId = npcId; }

    public int getNpcsSpawned() { return npcsSpawned; }
    public int getDamageDealt() { return damageDealt; }
    public int getHealingDone() { return healingDone; }

    /**
     * Get a summary of the GM session.
     */
    public String getSessionSummary() {
        return String.format("GM Session: %s\n" +
            "Mode: %s | Possessing: %s\n" +
            "NPCs spawned: %d | Damage dealt: %d | Healing done: %d",
            playerName,
            gmModeActive ? "ACTIVE" : "inactive",
            isPossessing() ? "yes" : "no",
            npcsSpawned, damageDealt, healingDone
        );
    }
}
