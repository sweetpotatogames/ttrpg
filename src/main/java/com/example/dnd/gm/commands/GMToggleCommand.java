package com.example.dnd.gm.commands;

import com.example.dnd.gm.GMManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Toggle GM mode on/off.
 * Usage: /gm
 */
public class GMToggleCommand extends AbstractPlayerCommand {

    public GMToggleCommand() {
        super("toggle", "server.commands.gm.toggle.desc");
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
        boolean newState = gmManager.toggleGmMode(playerRef.getUuid(), playerRef.getUsername());

        if (newState) {
            playerRef.sendMessage(Message.raw("[GM] GM Mode ENABLED"));
            playerRef.sendMessage(Message.raw("[GM] Commands: spawn, damage, heal, initiative, possess, unpossess, panel"));
        } else {
            // End possession if active when disabling GM mode
            if (gmManager.isPossessing(playerRef.getUuid())) {
                playerRef.sendMessage(Message.raw("[GM] Ending possession due to GM mode disable."));
                // Note: Full unpossess requires the Player object, handled elsewhere
            }
            playerRef.sendMessage(Message.raw("[GM] GM Mode DISABLED"));
        }
    }
}
