package com.example.dnd.gm.commands;

import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.GMSession;
import com.example.dnd.gm.ManagedNPC;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Possess a managed NPC to control its movement.
 * Usage: /gm possess [target]
 */
public class GMPossessCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public GMPossessCommand() {
        super("possess", "server.commands.gm.possess.desc");

        targetArg = withDefaultArg("target", "NPC name or 'selected'", ArgTypes.STRING, "selected", "selected");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        GMManager gmManager = GMManager.get();

        // Check GM mode
        if (!gmManager.isGmMode(playerRef.getUuid())) {
            playerRef.sendMessage(Message.raw("[GM] Must be in GM mode. Use /gm toggle first."));
            return;
        }

        // Check if already possessing
        if (gmManager.isPossessing(playerRef.getUuid())) {
            playerRef.sendMessage(Message.raw("[GM] Already possessing an NPC. Use /gm unpossess first."));
            return;
        }

        String target = context.get(targetArg);

        // Resolve target NPC
        ManagedNPC npc = resolveTarget(gmManager, playerRef.getUuid(), target);
        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] Target NPC not found. Specify name or select one first."));
            return;
        }

        if (!npc.isEntityValid()) {
            playerRef.sendMessage(Message.raw("[GM] NPC entity is no longer valid."));
            return;
        }

        if (npc.isDead()) {
            playerRef.sendMessage(Message.raw(String.format("[GM] Cannot possess %s - NPC is dead.", npc.getName())));
            return;
        }

        // Get player entity
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("[GM] Error: Could not get player component."));
            return;
        }

        // Start possession
        boolean success = gmManager.startPossession(player, store, npc.getId());
        if (success) {
            playerRef.sendMessage(Message.raw(String.format("[GM] Now possessing %s", npc.getName())));
            playerRef.sendMessage(Message.raw("[GM] Use WASD to move. /gm unpossess to release."));
        }
    }

    private ManagedNPC resolveTarget(GMManager gmManager, UUID playerId, String target) {
        if (target.equalsIgnoreCase("selected") || target.isEmpty()) {
            GMSession session = gmManager.getSession(playerId);
            if (session != null && session.getSelectedNpcId() != null) {
                return gmManager.getNpc(session.getSelectedNpcId());
            }
            return null;
        }

        try {
            UUID npcId = UUID.fromString(target);
            ManagedNPC npc = gmManager.getNpc(npcId);
            if (npc != null) return npc;
        } catch (IllegalArgumentException ignored) {}

        return gmManager.getNpcByName(target);
    }
}
