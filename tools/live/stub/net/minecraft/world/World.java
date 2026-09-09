package net.minecraft.world;

import net.minecraft.block.Block;

/**
 * B3 compile stub: shape-only vanilla API used by {@code forge/} sources.
 * Never runs (compile classpath only). Every member is pinned to the
 * 1.7.10 SRG by tools/run-live.sh before compiling — drift fails loudly.
 */
public class World {
    public WorldProvider provider;
    public boolean isRemote;

    public boolean setBlock(int x, int y, int z, Block block) {
        return false;
    }
}
