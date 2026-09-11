package cpw.mods.fml.common.registry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

/**
 * Forge compile stub: shape-only Forge API used by {@code forge/}
 * sources. Never runs (compile classpath only). {@code registerBlock}
 * serves the registration preInit (presence pinned against the
 * provisioned 1614 universal by tools/run-live.sh) — drift fails loudly.
 */
public class GameRegistry {
    public static Block registerBlock(Block block, String name) {
        return null;
    }

    public static void registerItem(Item item, String name) {
    }

    public static Item findItem(String modId, String name) {
        return null;
    }
}
