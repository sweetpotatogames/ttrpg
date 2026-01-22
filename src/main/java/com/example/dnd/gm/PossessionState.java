package com.example.dnd.gm;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Tracks the state of an active NPC possession by a GM.
 * Stores the GM's original position/rotation so they can be restored.
 */
public class PossessionState {
    private final UUID gmPlayerId;
    private final UUID possessedNpcId;

    // Original GM state to restore on unpossess
    private final Vector3d originalPosition;
    private final Vector3f originalRotation;

    // Timestamp for tracking possession duration
    private final long startTime;

    public PossessionState(
        @Nonnull UUID gmPlayerId,
        @Nonnull UUID possessedNpcId,
        @Nonnull Vector3d originalPosition,
        @Nullable Vector3f originalRotation
    ) {
        this.gmPlayerId = gmPlayerId;
        this.possessedNpcId = possessedNpcId;
        this.originalPosition = originalPosition;
        this.originalRotation = originalRotation != null ? originalRotation : new Vector3f(0, 0, 0);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Get how long the possession has been active in milliseconds.
     */
    public long getDurationMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Get a human-readable duration string.
     */
    public String getDurationString() {
        long seconds = getDurationMs() / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }

    @Nonnull
    public UUID getGmPlayerId() { return gmPlayerId; }

    @Nonnull
    public UUID getPossessedNpcId() { return possessedNpcId; }

    @Nonnull
    public Vector3d getOriginalPosition() { return originalPosition; }

    @Nonnull
    public Vector3f getOriginalRotation() { return originalRotation; }

    public long getStartTime() { return startTime; }
}
