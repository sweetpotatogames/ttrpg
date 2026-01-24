# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A D&D 5th Edition-inspired tabletop RPG plugin for Hytale featuring turn-based combat, character sheets, dice rolling, grid-based movement, and GM tools. Built as a Java plugin using the Hytale Server API.

## Build and Development Commands

```bash
# Build the plugin JAR
./gradlew build

# Build and run local Hytale server with plugin (debug enabled on port 5005)
./gradlew runServer

# Run server without debug mode
./gradlew runServerNoDebug

# Deploy plugin to Hytale Mods folder
./gradlew deployPlugin

# Run tests
./gradlew test

# Generate VSCode launch configuration
./gradlew generateVSCodeLaunch
```

## Project Structure

```
src/main/java/com/example/dnd/
├── DndPlugin.java           # Plugin entry point, registers commands and events
├── camera/                  # CRPG-style camera system (topdown, isometric)
├── character/               # CharacterSheet, Ability scores, DiceRoller
├── combat/                  # TurnManager, CombatState, turn phases
├── commands/                # /dnd subcommands (camera, roll, sheet, turn, etc.)
├── gm/                      # Game Master system
│   ├── GMManager.java       # GM session management
│   ├── ManagedNPC.java      # NPC tracking with HP/AC
│   ├── PossessionState.java # NPC possession tracking
│   ├── commands/            # /gm subcommands (spawn, possess, damage, etc.)
│   └── ui/                  # GM Control Panel
├── movement/                # Grid-based movement with A* pathfinding
├── targeting/               # NPC targeting system
└── ui/                      # UI page controllers (CharacterSheetPage, CombatHud, etc.)

src/main/resources/
├── manifest.json            # Plugin metadata (Main class, version)
├── Common/UI/Custom/        # Hytale UI markup files (.ui)
│   ├── Dnd/                # Shared theme components (Dnd.ui)
│   ├── Hud/Dnd/            # HUD overlays (CombatHud.ui)
│   └── Pages/Dnd/          # Modal pages (CharacterSheet.ui, CombatControl.ui, GMControl.ui)
└── Server/                  # Server-only assets
```

## Architecture

### Plugin Lifecycle
`DndPlugin` extends `JavaPlugin` and implements `setup()`, `start()`, and `shutdown()`. It:
- Initializes singleton managers (`TurnManager.get()`, `GMManager.get()`)
- Registers commands via `getCommandRegistry().registerCommand()`
- Registers event listeners via `getEventRegistry().register()`

### Manager Pattern
Core systems use singleton managers accessed via `.get()`:
- `TurnManager` - Combat turn order, phases, initiative tracking
- `GMManager` - GM mode, NPC management, possession
- `CameraInputHandler` - Mouse drag handling for camera pan/rotate

### Command Structure
Commands are hierarchical:
- `/dnd` root command with subcommands (camera, roll, sheet, turn, combat, move, target, initiative)
- `/gm` root command with subcommands (toggle, spawn, select, damage, heal, possess, unpossess, panel, initiative)

Each subcommand extends `AbstractPlayerCommand` which provides `Store<EntityStore>` and `Ref<EntityStore>` for ECS access.

### UI System
UI files use Hytale's custom `.ui` markup format:
- Define layouts with `Group`, `Label`, `Button` elements
- Reference shared styles from `Common.ui` using `$C = "../../Common.ui"`
- Use `#id` syntax for elements referenced by Java code
- Java controllers (e.g., `CombatControlPage`) bind to UI elements and handle events

## Known Limitations (ECS API)

See `TODO.md` for detailed tracking. Key limitations:
- Cannot access `ComponentAccessor<EntityStore>` from `World` - only available in ECS system iteration
- Cannot call `player.getComponent()` or `player.getEntityRef()` directly
- `player.getPlayerRef()` and `world.getPlayers()` are deprecated but functional

### Working Patterns
```java
// From AbstractPlayerCommand context:
Player player = store.getComponent(ref, Player.getComponentType());

// From Entity:
Ref<EntityStore> ref = entity.getReference();

// Block access:
int blockId = world.getBlock(x, y, z);  // Returns block ID, 0 = air
```

## UI Markup Syntax

Hytale `.ui` files use a custom markup language. Reference: `Common.ui` in game assets.

### Basic Structure
```
// Variable references to shared styles
$C = "../../Common.ui";

// Element with ID and properties
Group #elementId {
    Anchor: (Width: 200, Height: 50, Top: 10);
    LayoutMode: Top;
    Background: #1a1a2e(0.9);
    Padding: (Full: 10);
}

// Reusable component with parameters
$C.@PrimaryTextButton #buttonId {
    @Text = "Button Label";
}
```

### Background Property
```
// Solid color (full opacity)
Background: #1a1a2e;

// Color with opacity (0.0-1.0)
Background: #1a1a2e(0.9);

// Texture with 9-patch border (Border = slice size in pixels)
Background: (TexturePath: "Common/ContainerPatch.png", Border: 23);

// Texture with separate horizontal/vertical borders
Background: PatchStyle(TexturePath: "Common/Buttons/Primary.png", VerticalBorder: 12, HorizontalBorder: 80);
```

**Note:** There is no CSS-style `BorderColor` or `BorderSize` property. Visual borders come from the textures themselves (9-patch images). Use `Common.ui` components like `@Container` for bordered panels.

### Label Style Property
```
Style: (FontSize: 12, TextColor: #cccccc, RenderBold: true);

// Text alignment (NOT TextAlign!)
Style: (HorizontalAlignment: Center, VerticalAlignment: Center);
```

**Note:** Use `HorizontalAlignment` and `VerticalAlignment`, not `TextAlign`. Valid alignments: `Center`, `Start`, `End`.

### Button Components
TextButton components require an `@Text` parameter:
```
$C.@SecondaryTextButton #myButton {
    @Text = "Click Me";
    Anchor: (Width: 100, Height: 35);
}
```

Available button types from Common.ui:
- `@TextButton` - Main/primary action button
- `@SecondaryTextButton` - Secondary action
- `@SmallSecondaryTextButton` - Compact secondary button
- `@CancelTextButton` - Destructive/cancel action

### Event Bindings (Clickable Elements)
**IMPORTANT:** Only `Button` elements support the `Activating` event for click handling. `Group` elements do NOT support events, even if styled to look like buttons.

```java
// In Java, binding a click handler:
view.bind("#myButton", Activating.class, event -> {
    // This works only if #myButton is a Button element
});
```

**WRONG - This will cause runtime error:**
```
Group #myButton {
    Background: #3a3a5e;
    Label { Text: "Click Me"; }
}
```
Error: "Target element in CustomUI event binding has no compatible Activating event"

**CORRECT - Use Common.ui button components:**
```
$C.@SecondaryTextButton #myButton {
    @Text = "Click Me";
    Anchor: (Width: 100, Height: 35);
}
```

For small buttons, use `@SmallSecondaryTextButton` which works at compact sizes.

### LayoutMode Property
Valid values:
- `Top`, `Bottom`, `Middle`, `Left`, `Right`, `Center`
- `TopScrolling`, `BottomScrolling`
- `MiddleCenter`, `CenterMiddle`
- `LeftCenterWrap` - wraps children to next row
- `Full`, `Inherit`

### Key Syntax Rules
- Properties use `PropertyName: value;` format
- Compound values use `(Key: value, Key: value)` format
- Color values use hex format `#rrggbb` or `#rrggbb(opacity)`
- Element IDs use `#id` prefix
- Comments use `//`
- Variables defined with `@Name = value;`
- Spread operator `...@Style` to extend styles

## TTRPG Shared UI Components (Dnd.ui)

The plugin uses a shared component library at `Common/UI/Custom/Dnd/Dnd.ui` for consistent theming across all panels and HUDs.

### Importing Dnd.ui
```
// From Pages/Dnd/*.ui:
$D = "../../Dnd/Dnd.ui";

// From Hud/Dnd/*.ui:
$D = "../../Dnd/Dnd.ui";
```

### Color Palette (Dark Fantasy Theme)
| Color | Hex | Usage |
|-------|-----|-------|
| Primary Background | `#1a1a2e` | Dark panels |
| Secondary Background | `#2a2a4e` | Content areas |
| Section Headers | `#ccb588` | Gold/tan headings |
| Primary Text | `#cccccc` | Light gray body |
| Secondary Text | `#888888` | Medium gray muted |
| Help Text | `#666666` | Dark gray hints |
| Ability Labels | `#96a9be` | Blue-gray for STR/DEX/etc |
| Success | `#4caf50` | Green (HP, modifiers) |
| Error | `#f44336` | Red (damage, inactive) |
| Warning | `#ffeb3b` | Yellow |

### Typography Components
```
$D.@PageTitle { @Text = "Title"; }        // 18px bold gold
$D.@SectionHeader { @Text = "SECTION"; }  // 12px bold gold, bottom margin
$D.@SectionHeaderCompact { @Text = "X"; } // 12px bold gold, compact spacing
$D.@BodyText { @Text = "text"; }          // 11px light gray
$D.@SecondaryText { @Text = "text"; }     // 10px medium gray
$D.@HelpText { @Text = "- hint"; }        // 9px dark gray (14px height)
$D.@HelpTextSmall { @Text = "hint"; }     // 8px dark gray (12px height)
$D.@TipsHeader { }                        // "Tips:" label for help sections
```

### Button Components
```
$D.@DndButton #btn { @Text = "Action"; }           // Primary (220x40 default)
$D.@DndSecondaryButton #btn { @Text = "Action"; }  // Secondary (150x35 default)
$D.@DndSmallButton #btn { @Text = "Roll"; }        // Compact (45px wide)
$D.@DiceButton #btn { @Die = "D20"; }              // Dice roller buttons
```

### Stat Display Components
```
$D.@AbilityLabel { @Text = "STR"; }    // Blue-gray ability abbreviation
$D.@HpDisplay { }                       // Current/Max HP with #CurrentHp, #MaxHp labels
$D.@HpControls { }                      // -5/-1/+1/+5 button row with IDs
$D.@TurnIndicator { }                   // Current turn panel with #currentTurnLabel, #turnPromptLabel
$D.@HpBar { }                           // HP bar with #hpBarBg, #hpBarFill, #hpText
```

### Panel Components
```
$D.@DndPanel { @Opacity = 0.85; }       // Dark panel (#1a1a2e)
$D.@DndContentPanel { @Opacity = 0.8; } // Content panel (#2a2a4e)
$D.@EmptyStateMessage { @Text = "No items yet."; }  // Centered muted message
$D.@CombatStatus { @Text = "No Combat"; }           // Status label
```

### Example Usage
```
$C = "../../Common.ui";
$D = "../../Dnd/Dnd.ui";

$C.@PageOverlay {
  $C.@Container {
    $D.@SectionHeader { @Text = "ABILITIES"; }
    $D.@HpDisplay #HpDisplay { }
    $D.@HpControls #HpControls { }
    $D.@DndSecondaryButton #myButton {
      @Text = "Roll Initiative";
      @Width = 150;
    }
  }
}
```

## Testing

Connect Hytale client to `127.0.0.1` (Local Server) after starting the server. Use `/test` to verify plugin is loaded.

Key in-game commands:
- `/dnd camera topdown` - Enable tactical camera
- `/dnd sheet` - Open character sheet
- `/dnd combat` - Open combat control panel
- `/gm toggle` - Enable GM mode
- `/gm panel` - Open GM control panel
