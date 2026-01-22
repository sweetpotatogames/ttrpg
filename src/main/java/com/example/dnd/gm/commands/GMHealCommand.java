package com.example.dnd.gm.commands;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Heal a managed NPC.
 * Usage: /gm heal <amount> [target]
 *
 * Target can be:
 * - NPC name (partial match)
 * - "selected" or omitted to use selected NPC
 */
public class GMHealCommand extends AbstractPlayerCommand {
    private final RequiredArg<Integer> amountArg;
    private final DefaultArg<String> targetArg;

    public GMHealCommand() {
        super("heal", "server.commands.gm.heal.desc");

        amountArg = withRequiredArg("amount", "Heal amount", ArgTypes.INTEGER);
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

        int amount = context.get(amountArg);
        String target = context.get(targetArg);

        if (amount <= 0) {
            playerRef.sendMessage(Message.raw("[GM] Heal amount must be positive."));
            return;
        }

        // Resolve target NPC
        ManagedNPC npc = resolveTarget(gmManager, playerRef.getUuid(), target);
        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] Target NPC not found. Specify name or select one first."));
            return;
        }

        if (npc.isDead()) {
            playerRef.sendMessage(Message.raw(String.format("[GM] %s is dead. Use revive to bring them back.", npc.getName())));
            return;
        }

        // Apply healing
        int actualHeal = gmManager.healNpc(npc.getId(), amount, playerRef.getUuid());

        if (actualHeal == 0) {
            playerRef.sendMessage(Message.raw(String.format("[GM] %s is already at full HP.", npc.getName())));
        } else {
            String message = String.format("[GM] %s healed for %d! (HP: %s)",
                npc.getName(), actualHeal, npc.getHpString());
            gmManager.broadcastToGMs(world, message);
        }
    }

    private ManagedNPC resolveTarget(GMManager gmManager, UUID playerId, String target) {
        // Use selected NPC
        if (target.equalsIgnoreCase("selected") || target.isEmpty()) {
            GMSession session = gmManager.getSession(playerId);
            if (session != null && session.getSelectedNpcId() != null) {
                return gmManager.getNpc(session.getSelectedNpcId());
            }
            return null;
        }

        // Try to parse as UUID
        try {
            UUID npcId = UUID.fromString(target);
            ManagedNPC npc = gmManager.getNpc(npcId);
            if (npc != null) return npc;
        } catch (IllegalArgumentException ignored) {}

        // Search by name
        return gmManager.getNpcByName(target);
    }
}
