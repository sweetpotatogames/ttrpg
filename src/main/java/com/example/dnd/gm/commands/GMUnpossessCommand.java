package com.example.dnd.gm.commands;

import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.GMSession;
import com.example.dnd.gm.PossessionState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Stop possessing an NPC and return to original position.
 * Usage: /gm unpossess
 */
public class GMUnpossessCommand extends AbstractPlayerCommand {

    public GMUnpossessCommand() {
        super("unpossess", "server.commands.gm.unpossess.desc");
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

        // Check if possessing
        if (!gmManager.isPossessing(playerRef.getUuid())) {
            playerRef.sendMessage(Message.raw("[GM] Not currently possessing an NPC."));
            return;
        }

        // Get player entity
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("[GM] Error: Could not get player component."));
            return;
        }

        // Get possession state for duration info
        GMSession session = gmManager.getSession(playerRef.getUuid());
        PossessionState possessionState = session != null ? session.getPossessionState() : null;

        // End possession
        boolean success = gmManager.endPossession(player, store);
        if (success) {
            String duration = possessionState != null ? possessionState.getDurationString() : "unknown";
            playerRef.sendMessage(Message.raw(String.format("[GM] Possession ended. (Duration: %s)", duration)));
        } else {
            playerRef.sendMessage(Message.raw("[GM] Failed to end possession."));
        }
    }
}
