package com.example.dnd.gm.commands;

import com.example.dnd.DndPlugin;
import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.ui.GMControlPage;
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
 * Open the GM Control Panel UI.
 * Usage: /gm panel
 */
public class GMPanelCommand extends AbstractPlayerCommand {
    private final DndPlugin plugin;

    public GMPanelCommand(DndPlugin plugin) {
        super("panel", "server.commands.gm.panel.desc");
        this.plugin = plugin;
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

        // Get player entity
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            playerRef.sendMessage(Message.raw("[GM] Error: Could not get player component."));
            return;
        }

        // Open the GM control page
        GMControlPage controlPage = new GMControlPage(playerRef, plugin, world);
        player.getPageManager().openCustomPage(ref, store, controlPage);
    }
}
