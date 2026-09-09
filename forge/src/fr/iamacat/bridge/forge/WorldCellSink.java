package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.CellSink;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;

/**
 * B1 live sink: pure decision cells to 1.7.10 world edits. Plane
 * {@code "x,z"} cells land at the wire y/block, volume
 * {@code "x,y,z:ns:block"} cells (V3 structures) at their own y with a
 * block resolved by name (cached, refused loudly when unknown).
 * Server side only; the caller owns threading (FML server tick).
 * Only this package may import {@code net.minecraft} / {@code cpw.mods}.
 */
public final class WorldCellSink implements CellSink {
    /** 1.7.10 overworld height: y lives in 0..255. */
    static final int MAX_Y = 255;

    private final World world;
    private final int y;
    private final Block block;
    private final Map<String, Block> resolved =
            new HashMap<String, Block>();

    public WorldCellSink(World world, int y, Block block) {
        if (world == null) {
            throw new NullPointerException("E_FORGE_WORLD:null");
        }
        if (block == null) {
            throw new NullPointerException("E_FORGE_BLOCK:null");
        }
        checkY(y);
        this.world = world;
        this.y = y;
        this.block = block;
    }

    /** Single owner of the 1.7.10 height rule (also used at wire bind). */
    static void checkY(int y) {
        if (y < 0 || y > MAX_Y) {
            throw new IllegalArgumentException(
                    "E_FORGE_Y:range <" + y + "> (want 0.." + MAX_Y + ")");
        }
    }

    public void setCell(int x, int z) {
        world.setBlock(x, y, z, block);
    }

    @Override
    public void setBlock(int x, int y, int z, String blockName) {
        if (blockName == null) {
            throw new NullPointerException("E_FORGE_BLOCK:null");
        }
        checkY(y);
        Block at = resolved.get(blockName);
        if (at == null) {
            at = Block.getBlockFromName(blockName);
            if (at == null) {
                throw new IllegalArgumentException(
                        "E_FORGE_BLOCK:unknown <" + blockName + ">");
            }
            resolved.put(blockName, at);
        }
        world.setBlock(x, y, z, at);
    }
}
