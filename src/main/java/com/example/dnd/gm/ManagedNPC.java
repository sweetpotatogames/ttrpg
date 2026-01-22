package com.example.dnd.gm;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A GM-spawned NPC with TTRPG stats (HP, AC).
 * Tracks both the entity reference and D&D-style combat stats.
 */
public class ManagedNPC {
    private final UUID id;
    private final String name;
    private final String role;
    private final Ref<EntityStore> entityRef;
    private final int networkId;

    // TTRPG Stats
    private int maxHp;
    private int currentHp;
    private int armorClass;

    // State flags
    private boolean isDead = false;
    private boolean inInitiative = false;

    public ManagedNPC(
        @Nonnull UUID id,
        @Nonnull String name,
        @Nonnull String role,
        @Nonnull Ref<EntityStore> entityRef,
        int networkId,
        int maxHp,
        int armorClass
    ) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.entityRef = entityRef;
        this.networkId = networkId;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.armorClass = armorClass;
    }

    /**
     * Apply damage to this NPC.
     * @return The actual damage dealt (may be less if HP reaches 0)
     */
    public int takeDamage(int amount) {
        if (isDead || amount <= 0) return 0;

        int actualDamage = Math.min(amount, currentHp);
        currentHp -= actualDamage;

        if (currentHp <= 0) {
            currentHp = 0;
            isDead = true;
        }

        return actualDamage;
    }

    /**
     * Heal this NPC.
     * @return The actual HP healed (may be less if at max HP)
     */
    public int heal(int amount) {
        if (isDead || amount <= 0) return 0;

        int actualHeal = Math.min(amount, maxHp - currentHp);
        currentHp += actualHeal;

        return actualHeal;
    }

    /**
     * Revive a dead NPC with specified HP.
     */
    public void revive(int hp) {
        isDead = false;
        currentHp = Math.min(hp, maxHp);
    }

    /**
     * Check if the entity reference is still valid.
     */
    public boolean isEntityValid() {
        return entityRef != null && entityRef.isValid();
    }

    /**
     * Get HP as a formatted string (e.g., "15/20").
     */
    public String getHpString() {
        return currentHp + "/" + maxHp;
    }

    /**
     * Get HP as a percentage (0.0 to 1.0).
     */
    public float getHpPercent() {
        return maxHp > 0 ? (float) currentHp / maxHp : 0f;
    }

    // Getters and setters
    @Nonnull
    public UUID getId() { return id; }

    @Nonnull
    public String getName() { return name; }

    @Nonnull
    public String getRole() { return role; }

    @Nullable
    public Ref<EntityStore> getEntityRef() { return entityRef; }

    public int getNetworkId() { return networkId; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = Math.max(1, maxHp); }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = Math.max(0, Math.min(currentHp, maxHp)); }

    public int getArmorClass() { return armorClass; }
    public void setArmorClass(int armorClass) { this.armorClass = armorClass; }

    public boolean isDead() { return isDead; }

    public boolean isInInitiative() { return inInitiative; }
    public void setInInitiative(boolean inInitiative) { this.inInitiative = inInitiative; }

    @Override
    public String toString() {
        return String.format("%s [%s] HP: %s AC: %d%s",
            name, role, getHpString(), armorClass, isDead ? " (DEAD)" : "");
    }
}
