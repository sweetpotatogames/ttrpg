package com.example.dnd.gm.commands;

import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.GMSession;
import com.example.dnd.gm.ManagedNPC;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Spawn a managed NPC with TTRPG stats.
 * Usage: /gm spawn <role> [name] [hp] [ac]
 */
public class GMSpawnCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> roleArg;
    private final DefaultArg<String> nameArg;
    private final DefaultArg<Integer> hpArg;
    private final DefaultArg<Integer> acArg;

    public GMSpawnCommand() {
        super("spawn", "server.commands.gm.spawn.desc");

        roleArg = withRequiredArg("role", "NPC role (e.g., 'Trork Scout', 'goblin')", ArgTypes.STRING);
        nameArg = withDefaultArg("name", "Display name", ArgTypes.STRING, "", "");
        hpArg = withDefaultArg("hp", "Maximum HP", ArgTypes.INTEGER, 20, "20");
        acArg = withDefaultArg("ac", "Armor Class", ArgTypes.INTEGER, 12, "12");
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

        String role = context.get(roleArg);
        String name = context.get(nameArg);
        int hp = context.get(hpArg);
        int ac = context.get(acArg);

        // Default name to role if not specified
        if (name.isEmpty()) {
            name = role;
        }

        // Get player position for spawn location
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            playerRef.sendMessage(Message.raw("[GM] Cannot determine spawn position."));
            return;
        }

        // Spawn 2 blocks in front of player
        Vector3d playerPos = transform.getPosition();
        float yaw = transform.getRotation().getY();
        double offsetX = -Math.sin(yaw) * 2.0;
        double offsetZ = Math.cos(yaw) * 2.0;
        Vector3d spawnPos = new Vector3d(
            playerPos.getX() + offsetX,
            playerPos.getY(),
            playerPos.getZ() + offsetZ
        );

        // Spawn the NPC
        ManagedNPC npc = gmManager.spawnNpc(
            store, role, spawnPos, name, hp, ac,
            world.getWorldConfig().getUuid()
        );

        if (npc == null) {
            playerRef.sendMessage(Message.raw("[GM] Failed to spawn NPC. Check the role name."));
            playerRef.sendMessage(Message.raw("[GM] Try full paths like 'Trork/Trork Scout' or check vanilla assets."));
            return;
        }

        // Track spawn stat
        GMSession session = gmManager.getSession(playerRef.getUuid());
        if (session != null) {
            session.recordNpcSpawned();
            session.setSelectedNpcId(npc.getId()); // Auto-select the new NPC
        }

        playerRef.sendMessage(Message.raw(String.format(
            "[GM] Spawned: %s (HP: %d/%d, AC: %d)",
            npc.getName(), npc.getCurrentHp(), npc.getMaxHp(), npc.getArmorClass()
        )));
        playerRef.sendMessage(Message.raw(String.format("[GM] NPC ID: %s", npc.getId())));
    }
}
