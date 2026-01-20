# Common API Pitfalls and Corrections

This document covers common mistakes when using the Hytale Server API, based on real issues encountered during plugin development. The decompiled API signatures don't always match what you might expect from similar game modding APIs.

> **Note:** This document was created during Early Access (January 2026). The API may change as Hytale development continues.

## Table of Contents

1. [Chunk Lookup Methods](#chunk-lookup-methods)
2. [Command System](#command-system)
3. [BlockType Methods](#blocktype-methods)
4. [ItemStack Methods](#itemstack-methods)
5. [Block Visual State Updates](#block-visual-state-updates)
6. [BlockState Deprecation](#blockstate-deprecation)

---

## Chunk Lookup Methods

### Problem

`World.getChunkIfLoaded()` does **not** accept `(int chunkX, int chunkZ)` parameters.

```java
// WRONG - This will not compile
WorldChunk chunk = world.getChunkIfLoaded(chunkX, chunkZ);
```

### Solution

Use `ChunkUtil.indexChunkFromBlock()` to convert block coordinates to a chunk index:

```java
import com.hypixel.hytale.math.util.ChunkUtil;

// CORRECT - Use ChunkUtil to get chunk index from block coordinates
long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
```

If you have chunk coordinates (not block coordinates), use `ChunkUtil.indexChunk()`:

```java
// From chunk coordinates
long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
```

### Full Example

```java
private ConduitBlockState getConduitState(World world, Vector3i pos) {
    long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
    WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
    if (chunk == null) {
        return null;
    }

    // Use bitwise AND to get local chunk coordinates (0-31)
    BlockState state = chunk.getState(pos.x & 31, pos.y, pos.z & 31);
    if (state instanceof ConduitBlockState) {
        return (ConduitBlockState) state;
    }
    return null;
}
```

---

## Command System

### Problem 1: No `ctx.getArguments()` method

`CommandContext` does not have a `getArguments()` method that returns a String array.

```java
// WRONG - This method does not exist
String[] args = ctx.getArguments();
```

### Problem 2: No `ctx.getPlayer()` method

`CommandContext` does not have a `getPlayer()` method.

```java
// WRONG - This method does not exist
Player player = ctx.getPlayer();
```

### Problem 3: No `addArgument()` / `ctx.getArgument()` pattern

The argument system does not work with `addArgument()` and `ctx.getArgument()`.

```java
// WRONG - This pattern does not exist
addArgument("x", ArgumentType.DOUBLE);
double x = ctx.getArgument("x", Double.class);
```

### Solution: Use Subcommands and Typed Arguments

The correct pattern uses `withRequiredArg()` / `withOptionalArg()` to define arguments, and `ctx.get()` to retrieve values:

```java
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

public class MyCommand extends CommandBase {
    private final RequiredArg<Integer> xArg;
    private final RequiredArg<Integer> yArg;
    private final RequiredArg<Integer> zArg;

    public MyCommand() {
        super("mycommand", "Description here");
        setPermissionGroup(GameMode.Creative);

        // Define typed arguments
        xArg = withRequiredArg("x", "X coordinate", ArgTypes.INTEGER);
        yArg = withRequiredArg("y", "Y coordinate", ArgTypes.INTEGER);
        zArg = withRequiredArg("z", "Z coordinate", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Get argument values with ctx.get()
        int x = ctx.get(xArg);
        int y = ctx.get(yArg);
        int z = ctx.get(zArg);

        ctx.sendMessage(Message.raw("Coordinates: " + x + ", " + y + ", " + z));
    }
}
```

### Getting the Player

Use `ctx.isPlayer()` and `ctx.senderAs()`:

```java
@Override
protected void executeSync(@Nonnull CommandContext ctx) {
    // Check if command was run by a player
    if (!ctx.isPlayer()) {
        ctx.sendMessage(Message.raw("This command must be run by a player"));
        return;
    }

    // Get the player
    Player player = ctx.senderAs(Player.class);
    World world = player.getWorld();

    // ... rest of command logic
}
```

### Using Subcommands

For commands with multiple operations, use the subcommand pattern:

```java
public class MyPluginCommand extends CommandBase {

    public MyPluginCommand() {
        super("myplugin", "My plugin commands");
        setPermissionGroup(GameMode.Adventure);

        // Add subcommands
        addSubCommand(new StatusSubCommand());
        addSubCommand(new SetSubCommand());
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Show help when no subcommand provided
        ctx.sendMessage(Message.raw("Usage: /myplugin <status|set>"));
    }

    private class StatusSubCommand extends CommandBase {
        public StatusSubCommand() {
            super("status", "Show status");
            setPermissionGroup(GameMode.Adventure);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("Status: OK"));
        }
    }

    private class SetSubCommand extends CommandBase {
        private final RequiredArg<String> valueArg;

        public SetSubCommand() {
            super("set", "Set a value");
            setPermissionGroup(GameMode.Creative);
            valueArg = withRequiredArg("value", "Value to set", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            String value = ctx.get(valueArg);
            ctx.sendMessage(Message.raw("Set value to: " + value));
        }
    }
}
```

### Available Argument Types

Common `ArgTypes` values:
- `ArgTypes.STRING` - Text strings
- `ArgTypes.INTEGER` - Integer numbers
- `ArgTypes.DOUBLE` - Floating-point numbers
- `ArgTypes.BOOLEAN` - true/false values
- `ArgTypes.PLAYER` - Player references

---

## BlockType Methods

### Problem

`BlockType.getName()` does **not** exist.

```java
// WRONG - This method does not exist
String name = blockType.getName();
```

### Solution

Use `BlockType.getId()` instead:

```java
// CORRECT
String id = blockType.getId();

// Example usage
if (blockType != null && blockType.getId().startsWith("myplugin:")) {
    // Handle custom block
}
```

---

## ItemStack Methods

### Problem 1: Wrong Package Path

`ItemStack` is **not** at `com.hypixel.hytale.server.core.inventory.item.ItemStack`.

### Solution

The correct import path:

```java
// CORRECT
import com.hypixel.hytale.server.core.inventory.ItemStack;
```

### Problem 2: No `getItemType()` method

`ItemStack.getItemType()` does **not** exist.

```java
// WRONG
String key = itemStack.getItemType().getKey();
```

### Solution

Use `getItem().getId()`:

```java
// CORRECT
import com.hypixel.hytale.server.core.inventory.ItemStack;

private boolean isMyPluginItem(ItemStack itemStack) {
    if (itemStack == null || itemStack.getItem() == null) {
        return false;
    }
    String itemId = itemStack.getItem().getId();
    return itemId != null && itemId.startsWith("myplugin:");
}
```

---

## Block Visual State Updates

### Problem

`World.getBlockAccessor()` does **not** exist. You cannot directly call `setBlockInteractionState()`.

```java
// WRONG - getBlockAccessor() does not exist
getChunk().getWorld().getBlockAccessor().setBlockInteractionState(
    getBlockX(), getBlockY(), getBlockZ(),
    getBlockType(),
    "powered",
    false
);
```

### Solution

Visual state updates should be handled through:

1. **Block State Data** - Store the state in your `BlockState` subclass and call `markNeedsSave()`
2. **Block Definition JSON** - Define visual variants in the block's JSON file that respond to state data

```java
public class MyBlockState extends BlockState {
    private int powerLevel = 0;

    public void setPowerLevel(int level) {
        if (level != this.powerLevel) {
            this.powerLevel = level;
            markNeedsSave();  // This triggers state sync to clients
        }
    }
}
```

The block definition JSON then specifies visual variants:

```json
{
    "Type": "Block",
    "Id": "myplugin:MyBlock",
    "State": {
        "Type": "MyBlockState",
        "Data": {
            "MaxPower": 15
        }
    },
    "InteractionStates": {
        "default": { "Model": "Models/MyBlock.json" },
        "powered_low": { "Model": "Models/MyBlock_Powered_Low.json" },
        "powered_high": { "Model": "Models/MyBlock_Powered_High.json" }
    }
}
```

---

## BlockState Deprecation

### Warning

`BlockState` in `com.hypixel.hytale.server.core.universe.world.meta` is marked **deprecated and for removal**.

```
warning: [removal] BlockState has been deprecated and marked for removal
```

### Current Status

As of January 2026 Early Access, `BlockState` is still functional despite the deprecation warning. Use `@SuppressWarnings("deprecation")` to suppress the warning:

```java
@SuppressWarnings("deprecation")  // BlockState is deprecated but still functional
public class MyBlockState extends BlockState {
    // ...
}
```

### Future Consideration

Monitor Hytale updates for a replacement API. The deprecation suggests Hypixel plans to replace this system, possibly with a more ECS-aligned approach.

---

## Quick Reference Table

| What You Might Try | What Actually Works |
|-------------------|---------------------|
| `world.getChunkIfLoaded(chunkX, chunkZ)` | `world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(blockX, blockZ))` |
| `ctx.getArguments()` | Use `withRequiredArg()` + `ctx.get(arg)` pattern |
| `ctx.getPlayer()` | `ctx.isPlayer()` + `ctx.senderAs(Player.class)` |
| `ctx.getArgument("name", Type.class)` | Define arg with `withRequiredArg()`, get with `ctx.get(arg)` |
| `blockType.getName()` | `blockType.getId()` |
| `itemStack.getItemType().getKey()` | `itemStack.getItem().getId()` |
| `world.getBlockAccessor().setBlockInteractionState(...)` | Use `markNeedsSave()` + JSON visual variants |

---

## See Also

- [First Plugin Guide](../guides/first-plugin.md) - Updated with correct patterns
- [API Overview](./README.md) - Package structure and key classes
- [Decompiled Source](../../tools/decompiled-server/) - Authoritative API reference
