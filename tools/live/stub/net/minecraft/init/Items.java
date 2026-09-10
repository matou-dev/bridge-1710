package net.minecraft.init;

import net.minecraft.item.Item;

/**
 * B3 compile stub: shape-only vanilla item registry used by the loot sink
 * in forge/ (MatouBridgeMod spawns the diamond carrier, pinned to the
 * 1.7.10 SRG by tools/run-live.sh). Never runs (compile classpath only).
 *
 * <p>NEVER final here: a final field with an initializer is a
 * compile-time constant, so javac would fold the stub fiction into prod
 * bytes instead of reading the live registry (same class as the spike
 * {@code no-stub-const} finding — the gate refuses the shape).
 */
public class Items {
    public static Item diamond;
}
