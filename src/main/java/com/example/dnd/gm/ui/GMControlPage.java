package com.example.dnd.gm.ui;

import com.example.dnd.DndPlugin;
import com.example.dnd.combat.CombatState;
import com.example.dnd.combat.TurnManager;
import com.example.dnd.gm.GMManager;
import com.example.dnd.gm.GMSession;
import com.example.dnd.gm.ManagedNPC;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GM Control Panel UI showing managed NPCs, players, and quick actions.
 */
public class GMControlPage extends InteractiveCustomUIPage<GMControlPage.GMEventData> {
    private final DndPlugin plugin;
    private final World world;

    public GMControlPage(@Nonnull PlayerRef playerRef, @Nonnull DndPlugin plugin, @Nonnull World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, GMEventData.CODEC);
        this.plugin = plugin;
        this.world = world;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder cmd,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        cmd.append("Pages/Dnd/GMControl.ui");

        GMManager gmManager = GMManager.get();
        GMSession session = gmManager.getSession(playerRef.getUuid());
        TurnManager turnManager = plugin.getTurnManager();
        CombatState combatState = turnManager.getCombatState(world);

        // GM Status
        boolean gmActive = session != null && session.isGmModeActive();
        cmd.set("#gmStatusLabel.Text", gmActive ? "GM Mode: ACTIVE" : "GM Mode: INACTIVE");
        cmd.set("#gmStatusLabel.Style.TextColor", gmActive ? "#4caf50" : "#f44336");

        // Session stats
        if (session != null) {
            cmd.set("#statsLabel.Text", String.format(
                "NPCs: %d | Damage: %d | Healing: %d",
                session.getNpcsSpawned(), session.getDamageDealt(), session.getHealingDone()
            ));
        } else {
            cmd.set("#statsLabel.Text", "No session data");
        }

        // Possession status
        if (session != null && session.isPossessing()) {
            ManagedNPC possessed = gmManager.getNpc(session.getPossessedNpcId());
            String npcName = possessed != null ? possessed.getName() : "Unknown";
            cmd.set("#possessionLabel.Text", "Possessing: " + npcName);
            cmd.set("#possessionLabel.Style.TextColor", "#ffeb3b");
        } else {
            cmd.set("#possessionLabel.Text", "Not possessing");
            cmd.set("#possessionLabel.Style.TextColor", "#888888");
        }

        // Build NPC list
        buildNpcList(cmd, events, gmManager, session);

        // Build player list (initiative order)
        buildPlayerList(cmd, combatState);

        // Bind action buttons
        bindActionButtons(events);
    }

    private void buildNpcList(UICommandBuilder cmd, UIEventBuilder events, GMManager gmManager, GMSession session) {
        List<ManagedNPC> npcs = gmManager.getNpcsInWorld(world.getWorldConfig().getUuid());
        UUID selectedId = session != null ? session.getSelectedNpcId() : null;

        int maxSlots = 8;
        for (int i = 0; i < maxSlots; i++) {
            String slotId = "#npcSlot" + i;

            if (i < npcs.size()) {
                ManagedNPC npc = npcs.get(i);
                boolean isSelected = npc.getId().equals(selectedId);

                // NPC name and status
                String statusIcon = npc.isDead() ? " [DEAD]" : (npc.isInInitiative() ? " [INIT]" : "");
                cmd.set(slotId + "Name.Text", npc.getName() + statusIcon);
                cmd.set(slotId + "Name.Style.TextColor", isSelected ? "#4caf50" : "#cccccc");
                cmd.set(slotId + "Name.Style.RenderBold", isSelected);

                // HP bar
                cmd.set(slotId + "Hp.Text", npc.getHpString());
                float hpPercent = npc.getHpPercent();
                String hpColor = hpPercent > 0.5f ? "#4caf50" : (hpPercent > 0.25f ? "#ffeb3b" : "#f44336");
                cmd.set(slotId + "Hp.Style.TextColor", hpColor);

                // AC display
                cmd.set(slotId + "Ac.Text", "AC: " + npc.getArmorClass());

                cmd.set(slotId + ".Visible", true);

                // Bind select button for this NPC
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    slotId + "SelectBtn",
                    new EventData().append("Action", "SelectNpc").append("NpcId", npc.getId().toString()),
                    false
                );

                // Bind quick damage button
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    slotId + "DamageBtn",
                    new EventData().append("Action", "QuickDamage").append("NpcId", npc.getId().toString()),
                    false
                );

                // Bind quick heal button
                events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    slotId + "HealBtn",
                    new EventData().append("Action", "QuickHeal").append("NpcId", npc.getId().toString()),
                    false
                );
            } else {
                cmd.set(slotId + ".Visible", false);
            }
        }

        cmd.set("#noNpcsMsg.Visible", npcs.isEmpty());
    }

    private void buildPlayerList(UICommandBuilder cmd, CombatState combatState) {
        List<UUID> order = combatState.getInitiativeOrder();
        Map<UUID, String> names = combatState.getPlayerNames();
        Map<UUID, Integer> rolls = combatState.getInitiativeRolls();
        UUID currentPlayer = combatState.getCurrentPlayer();

        int maxSlots = 8;
        for (int i = 0; i < maxSlots; i++) {
            String slotId = "#initSlot" + i;

            if (i < order.size()) {
                UUID id = order.get(i);
                String name = names.getOrDefault(id, "Unknown");
                int roll = rolls.getOrDefault(id, 0);

                boolean isCurrent = combatState.isCombatActive() && id.equals(currentPlayer);

                cmd.set(slotId + ".Text", String.format("%d. %s (%d)", i + 1, name, roll));
                cmd.set(slotId + ".Style.TextColor", isCurrent ? "#4caf50" : "#cccccc");
                cmd.set(slotId + ".Style.RenderBold", isCurrent);
                cmd.set(slotId + ".Visible", true);
            } else {
                cmd.set(slotId + ".Visible", false);
            }
        }

        cmd.set("#noInitMsg.Visible", order.isEmpty());
    }

    private void bindActionButtons(UIEventBuilder events) {
        // Refresh button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#refreshBtn",
            new EventData().append("Action", "Refresh"),
            false
        );

        // Toggle GM mode
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#toggleGmBtn",
            new EventData().append("Action", "ToggleGm"),
            false
        );

        // Unpossess button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#unpossessBtn",
            new EventData().append("Action", "Unpossess"),
            false
        );

        // Possess selected button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#possessSelectedBtn",
            new EventData().append("Action", "PossessSelected"),
            false
        );

        // Add to initiative button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#addInitBtn",
            new EventData().append("Action", "AddToInit"),
            false
        );

        // Remove from initiative button
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#removeInitBtn",
            new EventData().append("Action", "RemoveFromInit"),
            false
        );
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull GMEventData data
    ) {
        GMManager gmManager = GMManager.get();
        GMSession session = gmManager.getSession(playerRef.getUuid());
        UICommandBuilder cmd = new UICommandBuilder();

        switch (data.action) {
            case "Refresh" -> {
                // Just refresh the display
            }
            case "ToggleGm" -> {
                gmManager.toggleGmMode(playerRef.getUuid(), playerRef.getUsername());
            }
            case "SelectNpc" -> handleSelectNpc(gmManager, data.npcId);
            case "QuickDamage" -> handleQuickDamage(gmManager, data.npcId);
            case "QuickHeal" -> handleQuickHeal(gmManager, data.npcId);
            case "Unpossess" -> handleUnpossess(gmManager, store, ref);
            case "PossessSelected" -> handlePossessSelected(gmManager, session, store, ref);
            case "AddToInit" -> handleAddToInit(gmManager, session);
            case "RemoveFromInit" -> handleRemoveFromInit(gmManager, session);
        }

        // Refresh the UI
        refreshDisplay(cmd, gmManager, session);
        sendUpdate(cmd, null, false);
    }

    private void handleSelectNpc(GMManager gmManager, String npcIdStr) {
        if (npcIdStr == null || npcIdStr.isEmpty()) return;

        try {
            UUID npcId = UUID.fromString(npcIdStr);
            GMSession session = gmManager.getOrCreateSession(playerRef.getUuid(), playerRef.getUsername());
            session.setSelectedNpcId(npcId);

            ManagedNPC npc = gmManager.getNpc(npcId);
            if (npc != null) {
                playerRef.sendMessage(Message.raw(String.format("[GM] Selected: %s", npc.getName())));
            }
        } catch (IllegalArgumentException e) {
            playerRef.sendMessage(Message.raw("[GM] Invalid NPC ID"));
        }
    }

    private void handleQuickDamage(GMManager gmManager, String npcIdStr) {
        if (npcIdStr == null || npcIdStr.isEmpty()) return;

        try {
            UUID npcId = UUID.fromString(npcIdStr);
            int damage = gmManager.damageNpc(npcId, 1, playerRef.getUuid());

            ManagedNPC npc = gmManager.getNpc(npcId);
            if (npc != null) {
                gmManager.broadcastToGMs(world, String.format("[GM] %s takes %d damage! (HP: %s)",
                    npc.getName(), damage, npc.getHpString()));
            }
        } catch (IllegalArgumentException e) {
            playerRef.sendMessage(Message.raw("[GM] Invalid NPC ID"));
        }
    }

    private void handleQuickHeal(GMManager gmManager, String npcIdStr) {
        if (npcIdStr == null || npcIdStr.isEmpty()) return;

        try {
            UUID npcId = UUID.fromString(npcIdStr);
            int healed = gmManager.healNpc(npcId, 1, playerRef.getUuid());

            ManagedNPC npc = gmManager.getNpc(npcId);
            if (npc != null && healed > 0) {
                gmManager.broadcastToGMs(world, String.format("[GM] %s healed for %d! (HP: %s)",
                    npc.getName(), healed, npc.getHpString()));
            }
        } catch (IllegalArgumentException e) {
            playerRef.sendMessage(Message.raw("[GM] Invalid NPC ID"));
        }
    }

    private void handleUnpossess(GMManager gmManager, Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            gmManager.endPossession(player, store);
        }
    }

    private void handlePossessSelected(GMManager gmManager, GMSession session, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (session == null || session.getSelectedNpcId() == null) {
            playerRef.sendMessage(Message.raw("[GM] No NPC selected."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            gmManager.startPossession(player, store, session.getSelectedNpcId());
        }
    }

    private void handleAddToInit(GMManager gmManager, GMSession session) {
        if (session == null || session.getSelectedNpcId() == null) {
            playerRef.sendMessage(Message.raw("[GM] No NPC selected."));
            return;
        }

        ManagedNPC npc = gmManager.getNpc(session.getSelectedNpcId());
        if (npc != null && !npc.isInInitiative()) {
            // Roll d20 for initiative
            int roll = (int)(Math.random() * 20) + 1;
            gmManager.addNpcToInitiative(npc.getId(), roll, world);
            gmManager.broadcastToGMs(world, String.format("[GM] %s joins initiative with roll %d", npc.getName(), roll));
        }
    }

    private void handleRemoveFromInit(GMManager gmManager, GMSession session) {
        if (session == null || session.getSelectedNpcId() == null) {
            playerRef.sendMessage(Message.raw("[GM] No NPC selected."));
            return;
        }

        ManagedNPC npc = gmManager.getNpc(session.getSelectedNpcId());
        if (npc != null && npc.isInInitiative()) {
            gmManager.removeNpcFromInitiative(npc.getId(), world);
            gmManager.broadcastToGMs(world, String.format("[GM] %s removed from initiative.", npc.getName()));
        }
    }

    private void refreshDisplay(UICommandBuilder cmd, GMManager gmManager, GMSession session) {
        TurnManager turnManager = plugin.getTurnManager();
        CombatState combatState = turnManager.getCombatState(world);

        // GM Status
        boolean gmActive = session != null && session.isGmModeActive();
        cmd.set("#gmStatusLabel.Text", gmActive ? "GM Mode: ACTIVE" : "GM Mode: INACTIVE");
        cmd.set("#gmStatusLabel.Style.TextColor", gmActive ? "#4caf50" : "#f44336");

        // Session stats
        if (session != null) {
            cmd.set("#statsLabel.Text", String.format(
                "NPCs: %d | Damage: %d | Healing: %d",
                session.getNpcsSpawned(), session.getDamageDealt(), session.getHealingDone()
            ));
        }

        // Possession status
        if (session != null && session.isPossessing()) {
            ManagedNPC possessed = gmManager.getNpc(session.getPossessedNpcId());
            String npcName = possessed != null ? possessed.getName() : "Unknown";
            cmd.set("#possessionLabel.Text", "Possessing: " + npcName);
            cmd.set("#possessionLabel.Style.TextColor", "#ffeb3b");
        } else {
            cmd.set("#possessionLabel.Text", "Not possessing");
            cmd.set("#possessionLabel.Style.TextColor", "#888888");
        }

        // Refresh NPC list
        List<ManagedNPC> npcs = gmManager.getNpcsInWorld(world.getWorldConfig().getUuid());
        UUID selectedId = session != null ? session.getSelectedNpcId() : null;

        int maxSlots = 8;
        for (int i = 0; i < maxSlots; i++) {
            String slotId = "#npcSlot" + i;

            if (i < npcs.size()) {
                ManagedNPC npc = npcs.get(i);
                boolean isSelected = npc.getId().equals(selectedId);

                String statusIcon = npc.isDead() ? " [DEAD]" : (npc.isInInitiative() ? " [INIT]" : "");
                cmd.set(slotId + "Name.Text", npc.getName() + statusIcon);
                cmd.set(slotId + "Name.Style.TextColor", isSelected ? "#4caf50" : "#cccccc");
                cmd.set(slotId + "Name.Style.RenderBold", isSelected);

                cmd.set(slotId + "Hp.Text", npc.getHpString());
                float hpPercent = npc.getHpPercent();
                String hpColor = hpPercent > 0.5f ? "#4caf50" : (hpPercent > 0.25f ? "#ffeb3b" : "#f44336");
                cmd.set(slotId + "Hp.Style.TextColor", hpColor);

                cmd.set(slotId + "Ac.Text", "AC: " + npc.getArmorClass());
                cmd.set(slotId + ".Visible", true);
            } else {
                cmd.set(slotId + ".Visible", false);
            }
        }

        cmd.set("#noNpcsMsg.Visible", npcs.isEmpty());

        // Refresh initiative list
        List<UUID> order = combatState.getInitiativeOrder();
        Map<UUID, String> names = combatState.getPlayerNames();
        Map<UUID, Integer> rolls = combatState.getInitiativeRolls();
        UUID currentPlayer = combatState.getCurrentPlayer();

        for (int i = 0; i < maxSlots; i++) {
            String slotId = "#initSlot" + i;

            if (i < order.size()) {
                UUID id = order.get(i);
                String name = names.getOrDefault(id, "Unknown");
                int roll = rolls.getOrDefault(id, 0);

                boolean isCurrent = combatState.isCombatActive() && id.equals(currentPlayer);

                cmd.set(slotId + ".Text", String.format("%d. %s (%d)", i + 1, name, roll));
                cmd.set(slotId + ".Style.TextColor", isCurrent ? "#4caf50" : "#cccccc");
                cmd.set(slotId + ".Style.RenderBold", isCurrent);
                cmd.set(slotId + ".Visible", true);
            } else {
                cmd.set(slotId + ".Visible", false);
            }
        }

        cmd.set("#noInitMsg.Visible", order.isEmpty());
    }

    /**
     * Event data for GM control actions.
     */
    public static class GMEventData {
        public static final BuilderCodec<GMEventData> CODEC = BuilderCodec.builder(
                GMEventData.class, GMEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (e, s) -> e.action = s, e -> e.action)
            .add()
            .append(new KeyedCodec<>("NpcId", Codec.STRING, true), (e, s) -> e.npcId = s != null ? s : "", e -> e.npcId)
            .add()
            .build();

        private String action;
        private String npcId = "";

        public GMEventData() {}
    }
}
