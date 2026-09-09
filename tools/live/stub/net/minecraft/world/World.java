package net.minecraft.world;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;

/**
 * B3 compile stub: shape-only vanilla API used by {@code forge/} sources.
 * Never runs (compile classpath only). {@code provider}, {@code isRemote}
 * and {@code setBlock} serve forge/ (pinned to the 1.7.10 SRG by
 * tools/run-live.sh); {@code provider} serves forge/ plus the spike dim-0
 * filter; {@code playerEntities} serves the spike harvest author;
 * {@code setBlockToAir} and {@code isAirBlock} serve
 * the dev-only autoplay spike proof (pinned by tools/autoplay/want.txt) —
 * drift fails loudly on the owning side.
 */
public class World {
    public WorldProvider provider;
    public boolean isRemote;
    public java.util.List<EntityPlayer> playerEntities;

    public boolean setBlock(int x, int y, int z, Block block) {
        return false;
    }

    public boolean setBlockToAir(int x, int y, int z) {
        return false;
    }

    public boolean isAirBlock(int x, int y, int z) {
        return false;
    }
}
