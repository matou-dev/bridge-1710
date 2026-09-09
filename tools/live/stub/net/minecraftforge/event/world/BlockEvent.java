package net.minecraftforge.event.world;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 break-event API
 * (universal jar, never obfuscated). Serves the repop spike hook in
 * forge/ (MatouBridgeMod records stone breaks). Never runs (compile
 * classpath only). Every member below is pinned to the 1614 universal
 * by tools/run-live.sh (pin_uni presence) — drift fails loudly.
 */
public class BlockEvent {
    public final int x = 0;
    public final int y = 0;
    public final int z = 0;
    public final World world = null;
    public final Block block = null;
    public final int blockMetadata = 0;

    public static class BreakEvent extends BlockEvent {
    }
}
