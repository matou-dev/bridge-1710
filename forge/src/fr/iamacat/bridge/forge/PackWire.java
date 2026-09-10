package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.ForgeContent;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.Packs.PackSpec;
import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.MatouId;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * B2 live binding: one configured pack plus where its cells land. Bound
 * once at init (fail fast), applied per world tick. Only this package may
 * import {@code net.minecraft} / {@code cpw.mods}.
 */
public final class PackWire {
    private final ContentPack pack;
    private final int y;
    private final Block block;

    PackWire(ContentPack pack, int y, Block block) {
        this.pack = pack;
        this.y = y;
        this.block = block;
    }

    /**
     * Binds a parsed spec: reflective load, operator configure, block
     * resolve, y range check (via {@link WorldCellSink}). Every failure is
     * loud — a half-bound wire never ticks.
     */
    public static PackWire bind(PackSpec spec) {
        if (spec == null) {
            throw new NullPointerException("E_FORGE_WIRE:null spec");
        }
        ContentPack pack = Packs.load(spec.className);
        if (pack instanceof ConfigurablePack) {
            ((ConfigurablePack) pack).configure(spec.args);
        } else if (!spec.args.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_FORGE_PACKS:args rejected <" + spec.className
                            + "> (pack takes no args)");
        }
        Block block = Block.getBlockFromName(spec.blockName);
        if (block == null) {
            throw new IllegalArgumentException("E_FORGE_BLOCK:unknown <"
                    + spec.blockName + ">");
        }
        // Y range refused here, before the first tick.
        WorldCellSink.checkY(spec.y);
        return new PackWire(pack, spec.y, block);
    }

    /** One tick on one world: decide pure, land cells. */
    public void applyTo(World world, long tick) {
        if (world == null) {
            throw new NullPointerException("E_FORGE_WORLD:null");
        }
        ForgeContent.applyAll(pack, tick,
                new WorldCellSink(world, y, block));
    }

    /**
     * Plain-data states for a tick (the loot seal merges beside them —
     * hub decisions/LOOT.md). A read-through, never a copy owner: the
     * pack seals, the caller merges.
     */
    public Map<MatouId, Object> states(long tick) {
        return pack.states(tick);
    }

    /**
     * The reflectively loaded pack (the T3 vocabulary provision reads the
     * seal vocabularies from it — hub
     * decisions/SPI_STATE_VOCABULARY.md). A read-through, never a copy
     * owner.
     */
    public ContentPack pack() {
        return pack;
    }
}
