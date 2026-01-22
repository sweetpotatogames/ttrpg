package com.example.dnd.gm.commands;

import com.example.dnd.character.DiceRoller;
import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.GMSession;
import com.example.dnd.gm.ManagedNPC;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Add or remove NPCs from initiative.
 * Usage: /gm initiative <add|remove> [target] [roll]
 */
public class GMInitiativeCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> actionArg;
    private final DefaultArg<String> targetArg;
    private final DefaultArg<Integer> rollArg;

    public GMInitiativeCommand() {
        super("initiative", "server.commands.gm.initiative.desc");

        actionArg = withRequiredArg("action", "add or remove", ArgTypes.STRING);
        targetArg = withDefaultArg("target", "NPC name or 'selected'", ArgTypes.STRING, "selected", "selected");
        rollArg = withDefaultArg("roll", "Initiative roll (auto-rolls d20 if not specified)", ArgTypes.INTEGER, -1, "auto");
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

        String action = context.get(actionArg);
        String target = context.get(targetArg);

        switch (action.toLowerCase()) {
            case "add" -> handleAdd(context, gmManager, playerRef, world, target);
            case "remove" -> handleRemove(gmManager, playerRef, world, target);
            default -> playerRef.sendMessage(Message.raw("[GM] Unknown action: " + action + ". Use: add, remove"));
        }
    }

    private void handleAdd(
        CommandContext context,
        GMManager gmManager,
        PlayerRef playerRef,
        World world,
        String target
    ) {
        ManagedNPC npc = resolveTarget(gmManager, playerRef.getUuid(), target);
        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] Target NPC not found. Specify name or select one first."));
            return;
        }

        if (npc.isInInitiative()) {
            playerRef.sendMessage(Message.raw(String.format("[GM] %s is already in initiative.", npc.getName())));
            return;
        }

        // Get or roll initiative
        int roll = context.get(rollArg);
        DiceRoller.DiceResult result = null;
        if (roll < 0) {
            result = DiceRoller.rollD20(0);
            roll = result.total();
        }

        boolean success = gmManager.addNpcToInitiative(npc.getId(), roll, world);
        if (success) {
            String message;
            if (result != null) {
                message = String.format("[GM] %s joins initiative: %s", npc.getName(), result.format());
            } else {
                message = String.format("[GM] %s joins initiative with roll %d", npc.getName(), roll);
            }
            broadcastMessage(world, message);
        } else {
            playerRef.sendMessage(Message.raw("[GM] Failed to add NPC to initiative."));
        }
    }

    private void handleRemove(
        GMManager gmManager,
        PlayerRef playerRef,
        World world,
        String target
    ) {
        ManagedNPC npc = resolveTarget(gmManager, playerRef.getUuid(), target);
        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] Target NPC not found. Specify name or select one first."));
            return;
        }

        if (!npc.isInInitiative()) {
            playerRef.sendMessage(Message.raw(String.format("[GM] %s is not in initiative.", npc.getName())));
            return;
        }

        boolean success = gmManager.removeNpcFromInitiative(npc.getId(), world);
        if (success) {
            broadcastMessage(world, String.format("[GM] %s removed from initiative.", npc.getName()));
        } else {
            playerRef.sendMessage(Message.raw("[GM] Failed to remove NPC from initiative."));
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

    @SuppressWarnings("deprecation")
    private void broadcastMessage(World world, String message) {
        for (Player player : world.getPlayers()) {
            player.getPlayerRef().sendMessage(Message.raw(message));
        }
    }
}
