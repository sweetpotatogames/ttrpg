package com.example.dnd.gm;

import com.example.dnd.combat.CombatState;
import com.example.dnd.combat.TurnManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for GM features.
 * Handles GM sessions, managed NPCs, and possession state.
 */
public class GMManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static GMManager instance;

    // GM sessions by player UUID
    private final Map<UUID, GMSession> gmSessions = new ConcurrentHashMap<>();

    // Managed NPCs by their generated UUID
    private final Map<UUID, ManagedNPC> managedNpcs = new ConcurrentHashMap<>();

    // World-specific NPC tracking (world UUID -> list of NPC UUIDs)
    private final Map<UUID, Set<UUID>> worldNpcs = new ConcurrentHashMap<>();

    // Reference to TurnManager for initiative integration
    private TurnManager turnManager;

    private GMManager() {}

    public static GMManager get() {
        if (instance == null) {
            instance = new GMManager();
        }
        return instance;
    }

    /**
     * Initialize with the TurnManager for combat integration.
     */
    public void initialize(@Nonnull TurnManager turnManager) {
        this.turnManager = turnManager;
        LOGGER.atInfo().log("[GM] GMManager initialized");
    }

    // ==================== GM Session Management ====================

    /**
     * Get or create a GM session for a player.
     */
    @Nonnull
    public GMSession getOrCreateSession(@Nonnull UUID playerId, @Nonnull String playerName) {
        return gmSessions.computeIfAbsent(playerId, k -> new GMSession(playerId, playerName));
    }

    /**
     * Get a GM session (may be null if player hasn't used GM features).
     */
    @Nullable
    public GMSession getSession(@Nonnull UUID playerId) {
        return gmSessions.get(playerId);
    }

    /**
     * Check if a player is in GM mode.
     */
    public boolean isGmMode(@Nonnull UUID playerId) {
        GMSession session = gmSessions.get(playerId);
        return session != null && session.isGmModeActive();
    }

    /**
     * Toggle GM mode for a player.
     * @return The new state (true = on, false = off)
     */
    public boolean toggleGmMode(@Nonnull UUID playerId, @Nonnull String playerName) {
        GMSession session = getOrCreateSession(playerId, playerName);
        boolean newState = session.toggleGmMode();
        LOGGER.atInfo().log("[GM] %s toggled GM mode: %s", playerName, newState ? "ON" : "OFF");
        return newState;
    }

    // ==================== NPC Management ====================

    /**
     * Spawn a managed NPC with TTRPG stats.
     *
     * @param store The entity store
     * @param roleKey The NPC role key (e.g., "Trork Scout")
     * @param position Spawn position
     * @param name Display name for the NPC
     * @param maxHp Maximum HP
     * @param armorClass Armor class
     * @param worldId World UUID for tracking
     * @return The created ManagedNPC, or null if spawn failed
     */
    @Nullable
    public ManagedNPC spawnNpc(
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleKey,
        @Nonnull Vector3d position,
        @Nonnull String name,
        int maxHp,
        int armorClass,
        @Nonnull UUID worldId
    ) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        int roleIndex = npcPlugin.getIndex(roleKey);

        if (roleIndex < 0) {
            LOGGER.atWarning().log("[GM] Invalid NPC role: %s", roleKey);
            return null;
        }

        // Spawn the NPC entity
        Pair<Ref<EntityStore>, NPCEntity> result = npcPlugin.spawnEntity(
            store, roleIndex, position, null, null, null
        );

        if (result == null) {
            LOGGER.atWarning().log("[GM] Failed to spawn NPC with role: %s", roleKey);
            return null;
        }

        Ref<EntityStore> entityRef = result.first();
        NPCEntity npcEntity = result.second();

        // Get network ID for possession
        NetworkId networkIdComp = store.getComponent(entityRef, NetworkId.getComponentType());
        int networkId = networkIdComp != null ? networkIdComp.getId() : -1;

        // Create managed NPC wrapper
        UUID npcId = UUID.randomUUID();
        ManagedNPC managedNpc = new ManagedNPC(
            npcId, name, roleKey, entityRef, networkId, maxHp, armorClass
        );

        // Register the NPC
        managedNpcs.put(npcId, managedNpc);
        worldNpcs.computeIfAbsent(worldId, k -> ConcurrentHashMap.newKeySet()).add(npcId);

        LOGGER.atInfo().log("[GM] Spawned managed NPC: %s (ID: %s)", name, npcId);
        return managedNpc;
    }

    /**
     * Get a managed NPC by UUID.
     */
    @Nullable
    public ManagedNPC getNpc(@Nonnull UUID npcId) {
        return managedNpcs.get(npcId);
    }

    /**
     * Get a managed NPC by name (case-insensitive, partial match).
     */
    @Nullable
    public ManagedNPC getNpcByName(@Nonnull String name) {
        String lowerName = name.toLowerCase();
        for (ManagedNPC npc : managedNpcs.values()) {
            if (npc.getName().toLowerCase().contains(lowerName)) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Get all managed NPCs in a world.
     */
    @Nonnull
    public List<ManagedNPC> getNpcsInWorld(@Nonnull UUID worldId) {
        Set<UUID> npcIds = worldNpcs.get(worldId);
        if (npcIds == null || npcIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ManagedNPC> result = new ArrayList<>();
        for (UUID id : npcIds) {
            ManagedNPC npc = managedNpcs.get(id);
            if (npc != null) {
                result.add(npc);
            }
        }
        return result;
    }

    /**
     * Remove a managed NPC (doesn't despawn the entity).
     */
    public void removeNpc(@Nonnull UUID npcId) {
        ManagedNPC npc = managedNpcs.remove(npcId);
        if (npc != null) {
            // Remove from world tracking
            for (Set<UUID> worldSet : worldNpcs.values()) {
                worldSet.remove(npcId);
            }
            LOGGER.atInfo().log("[GM] Removed managed NPC: %s", npc.getName());
        }
    }

    // ==================== Damage/Heal ====================

    /**
     * Apply damage to a managed NPC.
     * @return The actual damage dealt
     */
    public int damageNpc(@Nonnull UUID npcId, int amount, @Nullable UUID gmId) {
        ManagedNPC npc = managedNpcs.get(npcId);
        if (npc == null) return 0;

        int damage = npc.takeDamage(amount);

        // Track GM stats
        if (gmId != null) {
            GMSession session = gmSessions.get(gmId);
            if (session != null) {
                session.recordDamage(damage);
            }
        }

        LOGGER.atFine().log("[GM] Damaged NPC %s for %d (now %s)", npc.getName(), damage, npc.getHpString());
        return damage;
    }

    /**
     * Heal a managed NPC.
     * @return The actual HP healed
     */
    public int healNpc(@Nonnull UUID npcId, int amount, @Nullable UUID gmId) {
        ManagedNPC npc = managedNpcs.get(npcId);
        if (npc == null) return 0;

        int healed = npc.heal(amount);

        // Track GM stats
        if (gmId != null) {
            GMSession session = gmSessions.get(gmId);
            if (session != null) {
                session.recordHealing(healed);
            }
        }

        LOGGER.atFine().log("[GM] Healed NPC %s for %d (now %s)", npc.getName(), healed, npc.getHpString());
        return healed;
    }

    // ==================== Initiative Integration ====================

    /**
     * Add an NPC to the initiative order.
     */
    public boolean addNpcToInitiative(@Nonnull UUID npcId, int initiativeRoll, @Nonnull World world) {
        ManagedNPC npc = managedNpcs.get(npcId);
        if (npc == null || turnManager == null) return false;

        CombatState combatState = turnManager.getCombatState(world);
        combatState.addToInitiative(npcId, npc.getName(), initiativeRoll);
        npc.setInInitiative(true);

        LOGGER.atInfo().log("[GM] Added NPC %s to initiative with roll %d", npc.getName(), initiativeRoll);
        return true;
    }

    /**
     * Remove an NPC from the initiative order.
     */
    public boolean removeNpcFromInitiative(@Nonnull UUID npcId, @Nonnull World world) {
        ManagedNPC npc = managedNpcs.get(npcId);
        if (npc == null || turnManager == null) return false;

        CombatState combatState = turnManager.getCombatState(world);
        combatState.removeFromInitiative(npcId);
        npc.setInInitiative(false);

        LOGGER.atInfo().log("[GM] Removed NPC %s from initiative", npc.getName());
        return true;
    }

    // ==================== Possession ====================

    /**
     * Start possessing an NPC.
     * @return true if possession started successfully
     */
    public boolean startPossession(
        @Nonnull Player player,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID npcId
    ) {
        PlayerRef playerRef = player.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        GMSession session = gmSessions.get(playerId);
        if (session == null || !session.isGmModeActive()) {
            playerRef.sendMessage(Message.raw("[GM] Must be in GM mode to possess NPCs."));
            return false;
        }

        if (session.isPossessing()) {
            playerRef.sendMessage(Message.raw("[GM] Already possessing an NPC. Use /gm unpossess first."));
            return false;
        }

        ManagedNPC npc = managedNpcs.get(npcId);
        if (npc == null || !npc.isEntityValid()) {
            playerRef.sendMessage(Message.raw("[GM] Invalid NPC."));
            return false;
        }

        // Get player's current position to restore later
        Ref<EntityStore> playerRef2 = player.getReference();
        TransformComponent playerTransform = store.getComponent(playerRef2, TransformComponent.getComponentType());
        if (playerTransform == null) {
            playerRef.sendMessage(Message.raw("[GM] Cannot determine player position."));
            return false;
        }

        Vector3d originalPos = playerTransform.getPosition();
        Vector3f originalRot = playerTransform.getRotation();

        // Create possession state
        PossessionState possessionState = new PossessionState(playerId, npcId, originalPos, originalRot);
        session.startPossession(possessionState);

        // Send mount packet to player
        MountNPC mountPacket = new MountNPC(0, 0, 0, npc.getNetworkId());
        playerRef.getPacketHandler().write(mountPacket);

        LOGGER.atInfo().log("[GM] %s started possessing %s", playerRef.getUsername(), npc.getName());
        return true;
    }

    /**
     * Stop possessing an NPC.
     * @return true if possession ended successfully
     */
    public boolean endPossession(
        @Nonnull Player player,
        @Nonnull Store<EntityStore> store
    ) {
        PlayerRef playerRef = player.getPlayerRef();
        UUID playerId = playerRef.getUuid();

        GMSession session = gmSessions.get(playerId);
        if (session == null || !session.isPossessing()) {
            playerRef.sendMessage(Message.raw("[GM] Not currently possessing an NPC."));
            return false;
        }

        PossessionState possessionState = session.endPossession();
        if (possessionState == null) {
            return false;
        }

        // Dismount by sending mount packet with entity ID 0
        MountNPC dismountPacket = new MountNPC(0, 0, 0, 0);
        playerRef.getPacketHandler().write(dismountPacket);

        // Teleport player back to original position
        Ref<EntityStore> playerEntityRef = player.getReference();
        TransformComponent transform = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transform != null) {
            transform.teleportPosition(possessionState.getOriginalPosition());
        }

        LOGGER.atInfo().log("[GM] %s stopped possessing (duration: %s)",
            playerRef.getUsername(), possessionState.getDurationString());
        return true;
    }

    /**
     * Check if a player is currently possessing an NPC.
     */
    public boolean isPossessing(@Nonnull UUID playerId) {
        GMSession session = gmSessions.get(playerId);
        return session != null && session.isPossessing();
    }

    // ==================== Utilities ====================

    /**
     * Broadcast a message to all players in GM mode in a world.
     */
    @SuppressWarnings("deprecation")
    public void broadcastToGMs(@Nonnull World world, @Nonnull String message) {
        for (Player player : world.getPlayers()) {
            UUID playerId = player.getPlayerRef().getUuid();
            if (isGmMode(playerId)) {
                player.getPlayerRef().sendMessage(Message.raw(message));
            }
        }
    }

    /**
     * Clean up invalid NPCs (entities that no longer exist).
     */
    public void cleanupInvalidNpcs() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, ManagedNPC> entry : managedNpcs.entrySet()) {
            if (!entry.getValue().isEntityValid()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID id : toRemove) {
            removeNpc(id);
        }
        if (!toRemove.isEmpty()) {
            LOGGER.atInfo().log("[GM] Cleaned up %d invalid NPCs", toRemove.size());
        }
    }

    /**
     * Get the TurnManager reference.
     */
    @Nullable
    public TurnManager getTurnManager() {
        return turnManager;
    }
}
