package com.example.dnd.gm.commands;

import com.example.dnd.DndPlugin;
import com.example.dnd.combat.TurnManager;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Parent command collection for all GM commands.
 * All commands are accessed via /gm <subcommand>
 */
public class GMCommands extends AbstractCommandCollection {

    public GMCommands(DndPlugin plugin, TurnManager turnManager) {
        super("gm", "Game Master commands for TTRPG");

        // Add all subcommands
        addSubCommand(new GMToggleCommand());
        addSubCommand(new GMSpawnCommand());
        addSubCommand(new GMSelectCommand());
        addSubCommand(new GMDamageCommand());
        addSubCommand(new GMHealCommand());
        addSubCommand(new GMInitiativeCommand());
        addSubCommand(new GMPossessCommand());
        addSubCommand(new GMUnpossessCommand());
        addSubCommand(new GMPanelCommand(plugin));
    }
}
