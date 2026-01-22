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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Select a managed NPC for quick actions.
 * Usage: /gm select [target]
 *        /gm select list  - List all managed NPCs
 */
public class GMSelectCommand extends AbstractPlayerCommand {
    private final DefaultArg<String> targetArg;

    public GMSelectCommand() {
        super("select", "server.commands.gm.select.desc");

        targetArg = withDefaultArg("target", "NPC name, 'list', or 'clear'", ArgTypes.STRING, "list", "list");
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

        String target = context.get(targetArg);

        switch (target.toLowerCase()) {
            case "list" -> handleList(gmManager, playerRef, world);
            case "clear" -> handleClear(gmManager, playerRef);
            default -> handleSelect(gmManager, playerRef, world, target);
        }
    }

    private void handleList(GMManager gmManager, PlayerRef playerRef, World world) {
        List<ManagedNPC> npcs = gmManager.getNpcsInWorld(world.getWorldConfig().getUuid());

        if (npcs.isEmpty()) {
            playerRef.sendMessage(Message.raw("[GM] No managed NPCs in this world."));
            return;
        }

        playerRef.sendMessage(Message.raw("[GM] Managed NPCs:"));
        GMSession session = gmManager.getSession(playerRef.getUuid());
        UUID selectedId = session != null ? session.getSelectedNpcId() : null;

        for (ManagedNPC npc : npcs) {
            String marker = npc.getId().equals(selectedId) ? " <-- SELECTED" : "";
            String initMarker = npc.isInInitiative() ? " [INIT]" : "";
            playerRef.sendMessage(Message.raw(String.format("  %s - HP: %s, AC: %d%s%s",
                npc.getName(), npc.getHpString(), npc.getArmorClass(), initMarker, marker)));
        }
    }

    private void handleClear(GMManager gmManager, PlayerRef playerRef) {
        GMSession session = gmManager.getOrCreateSession(playerRef.getUuid(), playerRef.getUsername());
        session.setSelectedNpcId(null);
        playerRef.sendMessage(Message.raw("[GM] Selection cleared."));
    }

    private void handleSelect(GMManager gmManager, PlayerRef playerRef, World world, String target) {
        // Try UUID first
        ManagedNPC npc = null;
        try {
            UUID npcId = UUID.fromString(target);
            npc = gmManager.getNpc(npcId);
        } catch (IllegalArgumentException ignored) {}

        // Fall back to name search
        if (npc == null) {
            npc = gmManager.getNpcByName(target);
        }

        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] NPC not found: " + target));
            playerRef.sendMessage(Message.raw("[GM] Use '/gm select list' to see available NPCs."));
            return;
        }

        // Set selection
        GMSession session = gmManager.getOrCreateSession(playerRef.getUuid(), playerRef.getUsername());
        session.setSelectedNpcId(npc.getId());

        playerRef.sendMessage(Message.raw(String.format("[GM] Selected: %s (HP: %s, AC: %d)",
            npc.getName(), npc.getHpString(), npc.getArmorClass())));
    }
}
