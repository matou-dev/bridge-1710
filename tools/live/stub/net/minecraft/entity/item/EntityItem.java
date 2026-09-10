package net.minecraft.entity.item;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only vanilla drop entity used by the loot sink
 * in forge/ (MatouBridgeMod spawns one per due drop, pinned to the
 * 1.7.10 SRG by tools/run-live.sh). Never runs (compile classpath only).
 */
public class EntityItem extends Entity {
    public EntityItem(World world, double x, double y, double z,
            ItemStack stack) {
    }

    public ItemStack getEntityItem() {
        return null;
    }
}
