package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.CellSink;
import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * B1 live sink: pure {@code "x,z"} cells to 1.7.10 world edits.
 * Server side only; the caller owns threading (FML server tick).
 * Only this package may import {@code net.minecraft} / {@code cpw.mods}.
 */
public final class WorldCellSink implements CellSink {
    /** 1.7.10 overworld height: y lives in 0..255. */
    static final int MAX_Y = 255;

    private final World world;
    private final int y;
    private final Block block;

    public WorldCellSink(World world, int y, Block block) {
        if (world == null) {
            throw new NullPointerException("E_FORGE_WORLD:null");
        }
        if (block == null) {
            throw new NullPointerException("E_FORGE_BLOCK:null");
        }
        if (y < 0 || y > MAX_Y) {
            throw new IllegalArgumentException(
                    "E_FORGE_Y:range <" + y + "> (want 0.." + MAX_Y + ")");
        }
        this.world = world;
        this.y = y;
        this.block = block;
    }

    public void setCell(int x, int z) {
        world.setBlock(x, y, z, block);
    }
}
