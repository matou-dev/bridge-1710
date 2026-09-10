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
import fr.iamacat.bridge.loot.DropStore;
import fr.iamacat.bridge.loot.LootSeal;
import fr.iamacat.example1.LootJob;
import fr.iamacat.example1.LootTable;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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
 * <p>Loot (event-sourced, hub decisions/LOOT.md): ore harvests arrive on
 * {@link #onHarvest} (Forge harvest-drops events for the registered ore,
 * server side, dim 0 only) and mob kills on {@link #onKill} (Forge
 * living-drops events, same scope) into the bridge-owned
 * {@link DropStore}; every server tick {@link #lootTick} seals the store
 * plus the wired {@link LootTable} beside the first wire's pack states
 * ({@link LootSeal}, SPI untouched) and the pure {@link LootJob} decides
 * what drops. Due drops land as {@link EntityItem} carriers beside the
 * vanilla drops (vanilla behaviour untouched). The carrier is vanilla
 * diamond until item registration lands on the REGISTRATION path; every
 * dim-0 kill pays the single table entry until the custom entity lands
 * on the SPAWN path. New refusals stay loot-local ({@code E_LOOT_*},
 * never in the {@code E_FORGE_*} parity catalog), so bridge parity holds
 * with behaviour intentionally 1710-only until proven.
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
    /** Loot scope: the registered ore only (registry name, operator
     * domain — content names stay in example1). Other harvests are not
     * the loot's business (fortune/silk modifiers are an explicit
     * non-goal). */
    static final String LOOT_ORE = "example1:my_ore";
    /** Loot policy: one carrier per due harvest (no fortune — the pure
     * job reads it as a snapshot state, never a default). A constant,
     * never a default: the proof counts carriers per harvest. */
    static final long LOOT_COUNT = 1L;

    private final List<PackWire> wires = new ArrayList<PackWire>();
    private final MinedStore mined = new MinedStore();
    private final RepopJob repop = new RepopJob();
    private final DropStore drops = new DropStore();
    private final LootJob loot = new LootJob();
    private Block stone;
    private Block ore;
    private Map<String, String> lootTable;
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
        wireLoot(Packs.parseLines(lines));
    }

    /**
     * Loot wiring: one table per bridge from the packs' owned content
     * (parsed once, like registration — never on the tick path). No
     * owned file anywhere means loot stays passive (Q1 cohabitation):
     * the hooks gate on the null table. Several distinct owned files
     * refuse loudly — silent table picks are defaults.
     */
    private void wireLoot(List<Packs.PackSpec> specs) {
        Set<String> owned = new HashSet<String>();
        for (Packs.PackSpec spec : specs) {
            String path = spec.args.get("ownedFile");
            if (path != null) {
                owned.add(path);
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        if (owned.size() > 1) {
            throw new IllegalArgumentException("E_LOOT_TABLE:multi <"
                    + owned + "> (one table per bridge)");
        }
        lootTable = LootTable.fromFile(
                owned.iterator().next()).drops();
        ore = Block.getBlockFromName(LOOT_ORE);
        if (ore == null) {
            throw new IllegalArgumentException("E_LOOT_ORE:unknown <"
                    + LOOT_ORE + ">");
        }
        if (Items.diamond == null) {
            throw new IllegalArgumentException(
                    "E_LOOT_GEM:unknown <minecraft:diamond>");
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
        lootTick(event.world, tick);
        tick++;
    }

    /**
     * Loot record: a server-side dim-0 harvest of the registered ore
     * becomes an ore harvest at the last server tick (same clock the
     * per-tick seal reads — both run on the server thread). Client-side
     * echoes (isRemote) are ignored: the server fires its own event for
     * the same harvest. Fortune, silk touch and the vanilla drop list
     * are untouched (explicit non-goals): the seam only records.
     */
    @SubscribeEvent
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        if (lootTable == null) {
            return;
        }
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider.dimensionId != 0) {
            return;
        }
        if (event.block != ore) {
            return;
        }
        String harvest = Cell.of(event.x, event.y, event.z,
                LootJob.ORE).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot record: a server-side dim-0 mob kill becomes a beast harvest
     * at the entity's block coords. Single-table scope (hub
     * decisions/LOOT.md): every kill pays the one entry until the custom
     * entity lands — per-mob filtering is a re-opener, never a quiet
     * filter here.
     */
    @SubscribeEvent
    public void onKill(LivingDropsEvent event) {
        if (lootTable == null) {
            return;
        }
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }
        if (event.entityLiving.worldObj.provider.dimensionId != 0) {
            return;
        }
        int x = (int) Math.floor(event.entityLiving.posX);
        int y = (int) Math.floor(event.entityLiving.posY);
        int z = (int) Math.floor(event.entityLiving.posZ);
        String harvest = Cell.of(x, y, z, LootJob.BEAST).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot seal: table plus store beside the first wire's pack states,
     * pure decide, land one carrier per due drop, evict claimed. The
     * store-vs-job equality the etage-1 gate holds (up to the table
     * expansion) is re-checked loudly here: a live divergence (decided
     * != expanded claim) fails the tick instead of losing drops
     * silently. Passive without a wired pack (no table, no wires).
     */
    private void lootTick(World world, long now) {
        if (lootTable == null || wires.isEmpty()) {
            return;
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>(
                wires.get(0).states(now));
        states.putAll(LootSeal.seal(drops, lootTable, LOOT_COUNT));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = loot.decide(snap);
        for (String cell : due) {
            ForgeCells.BlockCell vol = ForgeCells.parseBlockCell(cell);
            dropCarrier(world, vol.x, vol.y, vol.z);
        }
        if (!due.isEmpty()) {
            System.out.println("[MatouBridge] loot dropped "
                    + due.size() + " carrier(s) at tick " + now);
        }
        List<String> claimed = drops.claimDue(now);
        if (!due.equals(expandClaim(claimed))) {
            throw new IllegalStateException("E_LOOT_SEAL:diverged <due="
                    + due + " claimed=" + claimed + "> at tick " + now);
        }
    }

    /**
     * Live table expansion: what the pure decision must equal for a
     * claimed harvest list (same shape as the etage-1 comparateur — the
     * gate and the tick share the rule, never a copy each).
     */
    private List<String> expandClaim(List<String> claimed) {
        List<String> out = new ArrayList<String>();
        for (String harvest : claimed) {
            int cut = harvest.indexOf(':');
            String head = harvest.substring(0, cut);
            String item = lootTable.get(harvest.substring(cut + 1));
            for (long c = 0; c < LOOT_COUNT; c++) {
                out.add(head + ":" + item);
            }
        }
        return out;
    }

    /**
     * Loot landing: one vanilla-diamond carrier per due drop, beside the
     * vanilla drops (never replacing them). A refused spawn fails loudly
     * — a lost carrier is loot lost silently otherwise.
     */
    private void dropCarrier(World world, int x, int y, int z) {
        EntityItem carrier = new EntityItem(world, x + 0.5, y + 0.5,
                z + 0.5, new ItemStack(Items.diamond, 1));
        if (!world.spawnEntityInWorld(carrier)) {
            throw new IllegalStateException("E_LOOT_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
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
