package fr.iamacat.bridge.forge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import fr.iamacat.bridge.ForgeCells;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.spike.MinedStore;
import fr.iamacat.bridge.spike.RepopJob;
import fr.iamacat.bridge.spike.RepopSeal;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * B2 Forge wiring (10.13.4.1614): FML world tick in, pure SPI decide,
 * bridge-owned apply. Packs come from {@code config/matoubridge/packs.cfg}
 * (one {@code <class> <y> <block> [k=v ...]} per line); a missing file
 * means no packs, staying passive (Q1 cohabitation). Malformed config or
 * unloadable pack fails fast at init — a half-wired bridge never ticks.
 *
 * <p>Repop spike (event-sourced, vanilla stone, zero registration): stone
 * breaks arrive on {@link #onBreak} (Forge break events, server side,
 * dim 0 only) into the bridge-owned {@link MinedStore}; every server
 * tick {@link #repopTick} seals the store beside {@code pack.states}
 * ({@link RepopSeal}, SPI untouched) and the pure {@link RepopJob}
 * decides what is due back. New refusals stay spike-local
 * ({@code E_SPIKE_*}, never in the {@code E_FORGE_*} parity catalog),
 * so bridge parity holds with behaviour intentionally 1710-only until
 * the spike is proven.
 *
 * <p>Registration (hub decisions/REGISTRATION.md) lives next door in
 * {@link Example1Mod}: a second mod under example1's own modid, whose
 * preInit registers custom wire blocks before these init-time binds
 * resolve them (1.7.10 FML prefixes registry names with the active
 * container, so the owning prefix must register).
 *
 * <p>Only this package may import {@code net.minecraft} / {@code cpw.mods};
 * the pure gate ({@code tools/check.sh} etage 1) fails otherwise.
 */
@Mod(modid = MatouBridgeMod.MODID, name = "MatouBridge", version = "1.2.0",
        acceptableRemoteVersions = "*")
public final class MatouBridgeMod {
    public static final String MODID = "matoubridge";
    static final String PACKS_PATH = "config/matoubridge/packs.cfg";
    /** Spike-tuned repop delay: 200 ticks (10s at 20tps — human-visible
     * in a live run, far below proof windows). A constant, never a
     * default: the proof mines, waits, and watches this exact horizon. */
    static final long REPOP_DELAY = 200L;
    /** Spike scope: vanilla stone only. Other breaks are not the spike's
     * business (metadata/T.E. restore is an explicit non-goal). */
    static final String REPOP_BLOCK = "minecraft:stone";

    private final List<PackWire> wires = new ArrayList<PackWire>();
    private final MinedStore mined = new MinedStore();
    private final RepopJob repop = new RepopJob();
    private Block stone;
    private long tick;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        stone = Block.getBlockFromName(REPOP_BLOCK);
        if (stone == null) {
            throw new IllegalArgumentException("E_SPIKE_STONE:unknown <"
                    + REPOP_BLOCK + ">");
        }
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        File cfg = new File(PACKS_PATH);
        if (!cfg.isFile()) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("E_FORGE_PACKS:unreadable <"
                    + PACKS_PATH + "> (" + e.getMessage() + ")", e);
        }
        for (Packs.PackSpec spec : Packs.parseLines(lines)) {
            wires.add(PackWire.bind(spec));
        }
    }

    /**
     * Spike record: a server-side dim-0 stone break becomes a mined cell
     * at the last server tick (same clock the per-tick seal reads — both
     * run on the server thread, no cross-thread counter). Client-side
     * echoes (isRemote) are ignored: the server fires its own event for
     * the same break.
     */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider.dimensionId != 0) {
            return;
        }
        if (event.block != stone) {
            return;
        }
        String cell = Cell.of(event.x, event.y, event.z, REPOP_BLOCK)
                .render();
        mined.record(cell, tick);
        System.out.println("[MatouBridge] spike recorded <" + cell
                + "> at tick " + tick);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != Side.SERVER
                || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.world.provider.dimensionId != 0) {
            return;
        }
        for (PackWire wire : wires) {
            wire.applyTo(event.world, tick);
        }
        repopTick(event.world, tick);
        tick++;
    }

    /**
     * Spike seal: store beside pack states, pure decide, land due cells,
     * evict claimed. The store-vs-job equality the etage-1 gate holds is
     * re-checked loudly here: a live divergence (claimed != due) fails
     * the tick instead of leaking mined cells silently.
     */
    private void repopTick(World world, long now) {
        Map<MatouId, Object> states = RepopSeal.seal(mined, REPOP_DELAY);
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = repop.decide(snap);
        if (!due.isEmpty()) {
            ForgeCells.applyCells(due,
                    new WorldCellSink(world, 0, stone));
            System.out.println("[MatouBridge] spike repopped "
                    + due.size() + " cell(s) at tick " + now);
        }
        List<String> claimed = mined.claimDue(now, REPOP_DELAY);
        if (!claimed.equals(due)) {
            throw new IllegalStateException("E_SPIKE_SEAL:diverged <due="
                    + due + " claimed=" + claimed + "> at tick " + now);
        }
    }
}
