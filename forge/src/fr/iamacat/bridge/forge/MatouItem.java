package fr.iamacat.bridge.forge;

import net.minecraft.item.Item;

/**
 * Lead bridge generic item (1.7.10): drives item registration from
 * content {@code ItemSpec} (short name, stack size) parsed once in
 * preInit. Pure, zero content literals here — {@link Example1Mod}
 * instances it from reflective specs.
 */
public final class MatouItem extends Item {
    public MatouItem(String shortName, int stackSize) {
        setUnlocalizedName(shortName);
        setMaxStackSize(stackSize);
    }
}
