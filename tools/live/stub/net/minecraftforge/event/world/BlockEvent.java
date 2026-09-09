package net.minecraftforge.event.world;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 break-event API
 * (universal jar, never obfuscated). Field reads serve the repop spike
 * hook in forge/ (MatouBridgeMod records stone breaks, pinned by
 * tools/run-live.sh); the constructors serve the dev-only autoplay spike
 * proof (tools/autoplay/, never shipped), which posts a simulated harvest
 * (pinned by tools/autoplay/universal-pin.txt). Never runs (compile
 * classpath only) — drift fails loudly.
 *
 * <p>NEVER final on a primitive field here: a final primitive with an
 * initializer is a compile-time constant, so javac folds the stub fiction
 * (here 0) into prod bytes instead of reading the live event (measured
 * 2026-09-09: the spike hook recorded 0,0,0 for every break, caught live —
 * the {@code no-stub-const} gate in tools/check.sh refuses this shape).
 */
public class BlockEvent extends Event {
    public int x;
    public int y;
    public int z;
    public final World world = null;
    public final Block block = null;
    public int blockMetadata;

    public BlockEvent(int x, int y, int z, World world, Block block,
            int blockMetadata) {
    }

    public static class BreakEvent extends BlockEvent {
        public BreakEvent(int x, int y, int z, World world, Block block,
                int blockMetadata, EntityPlayer player) {
            super(x, y, z, world, block, blockMetadata);
        }
    }
}
