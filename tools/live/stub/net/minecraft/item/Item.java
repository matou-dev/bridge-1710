package net.minecraft.item;

/**
 * B3 compile stub: shape-only vanilla item type used by the loot sink in
 * forge/ (MatouBridgeMod wraps the carrier, pinned to the 1.7.10 SRG by
 * tools/run-live.sh). Never runs (compile classpath only). Used as a
 * type only — no member is called, so no pin is owned here.
 */
public class Item {
    public Item setMaxStackSize(int maxStackSize) {
        return this;
    }

    public Item setUnlocalizedName(String unlocalizedName) {
        return this;
    }

    public static int getIdFromItem(Item item) {
        return 0;
    }
}
