package fr.iamacat.bridge.forge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import fr.iamacat.bridge.ForgeCells;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.bridge.spike.MinedStore;
import fr.iamacat.bridge.spike.RepopJob;
import fr.iamacat.bridge.spike.RepopSeal;
import fr.iamacat.bridge.loot.DropStore;
import fr.iamacat.bridge.loot.LootSeal;
import fr.iamacat.bridge.spawn.SpawnStore;
import fr.iamacat.bridge.spawn.SpawnSeal;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.PolicyPack;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.VocabularyPack;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * <p>Loot (event-sourced, hub decisions/LOOT.md): harvests of the operator
 * wire blocks arrive on {@link #onHarvest} (Forge harvest-drops events,
 * server side, dim 0 only) and mob kills on {@link #onKill} (Forge
 * living-drops events, same scope) into the bridge-owned
 * {@link DropStore}; every server tick {@link #lootTick} seals the store
 * plus the wired loot table beside the first wire's pack states
 * (the pack-served loot vocabulary, T3 registry — hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}) and the pure pack-served
 * loot job decides what drops. The ore scope is the packs.cfg wire-block column (T2
 * operator-override tranche, hub decisions/SPAWN.md — no bridge constant
 * names a loot block); the per-harvest count is the content
 * {@code drop_count} unless the operator {@code loot.count} wins. Due
 * drops land as {@link EntityItem} carriers beside the
 * vanilla drops (vanilla behaviour untouched). The carrier is vanilla
 * diamond until item registration lands on the REGISTRATION path; every
 * dim-0 kill pays the single table entry (per-mob filtering is a
 * re-opener, never a quiet filter — hub decisions/LOOT.md). New refusals stay loot-local ({@code E_LOOT_*},
 * never in the {@code E_FORGE_*} parity catalog), so bridge parity holds
 * with behaviour intentionally 1710-only until proven.
 *
 * <p>Spawn (event-sourced, hub decisions/SPAWN.md): the pure
 * pack-served spawn job reads the bridge-owned {@link SpawnStore} census plus
 * the wired spawn table beside the first wire's pack states
 * (the pack-served spawn vocabulary, T3 registry — hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}) and decides budgeted spawns; due
 * spawns land as the registered custom beast ({@link MatouEntity}, pig
 * shape and renderer reused — registration-path tranche, hub
 * decisions/REGISTRATION.md). A live {@code slots != due} divergence
 * fails the tick loudly ({@code E_SPAWN_SEAL:diverged},
 * spike-tripwire shape). Census releases ride the kill hook below; an
 * {@code EntityJoinWorldEvent} veto holds the cap against beast joins
 * the budget never decided (vanilla pigs are a different species now:
 * ignored, never vetoed). Landing plus veto stay passive unless
 * {@code SPAWN=1} (same opt-in as the spike/loot companion proofs):
 * always-on landing would veto the loot proof's own beast once the
 * census fills, so the union and loot runs stay byte-for-byte
 * spawn-free. The spawn numbers are the content policy unless the
 * operator {@code spawn.*} wins (T2 operator-override tranche, hub
 * decisions/SPAWN.md — the bridge transports the effective policy, it
 * never owns a spawn number). New refusals stay spawn-local ({@code E_SPAWN_*}, never
 * in the {@code E_FORGE_*} parity catalog), so bridge parity holds with
 * behaviour intentionally 1710-only until proven.
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
    /** Loot scope: the operator wire blocks, resolved at wire time (T2
     * operator-override tranche, hub decisions/SPAWN.md — the packs.cfg
     * wire-block column names the ore, no bridge constant does; other
     * harvests are not the loot's business, fortune/silk modifiers stay
     * explicit non-goals). Wiring the dev stone default therefore pays
     * stone harvests; every live proof wires the registered ore.
     */
    private final List<String> oreNames = new ArrayList<String>();
    private final List<Block> ores = new ArrayList<Block>();
    /** Spawn switch (DEV proof opt-in): landing plus veto stay passive
     * unless {@code SPAWN=1}, so union and loot runs never see a beast. */
    static final boolean SPAWN = "1".equals(System.getenv("SPAWN"));
    /** Spawn policy, sealed from the content table at wire time unless
     * the operator {@code spawn.*} wins (hub decisions/SPAWN.md
     * operator-override tranche): effective cap, per-tick budget and y
     * band. The bridge transports them into the seal, it never owns a
     * spawn number. The companion mirrors the effective cap (see its
     * SPAWN_CAP note). */
    private long spawnCap;
    private long spawnBudget;
    private long spawnYMin;
    private long spawnYMax;
    /** Combat reach, sealed from the content table at wire time (hub
     * decisions/VIRTUAL_HITBOXES.md combat-policy tranche): effective
     * eye-to-hitVec cutoff for the bone ray-test. The bridge transports
     * it into the seal, it never owns a combat number. */
    private double combatReach;
    /** Loot policy, sealed from the content table at wire time unless
     * the operator {@code loot.count} wins (operator-override tranche):
     * effective items per harvest. Transported, never owned. */
    private long lootCount;

    private final List<PackWire> wires = new ArrayList<PackWire>();
    private final MinedStore mined = new MinedStore();
    private final RepopJob repop = new RepopJob();
    private final DropStore drops = new DropStore();
    private MatouJob<List<String>> loot;
    private final SpawnStore census = new SpawnStore();
    private MatouJob<List<String>> spawn;
    private Block stone;
    private Map<String, String> lootTable;
    private String oreKind;
    private String beastKind;
    private StateVocabulary lootVocab;
    private String spawnMob;
    private StateVocabulary spawnVocab;
    private long spawnHp;
    private String ownedPath;
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
        List<Packs.PackSpec> specs = Packs.parseLines(lines);
        for (Packs.PackSpec spec : specs) {
            wires.add(PackWire.bind(spec));
        }
        wireLoot(specs);
        wireSpawn(specs);
        wireCombat(specs);
    }

    /**
     * T3 vocabulary provision (hub
     * {@code decisions/SPI_STATE_VOCABULARY.md}): the seal vocabularies
     * come from the first wire's reflectively loaded pack at wire time
     * (parse-once, never on the tick path — the seals merge beside that
     * same wire's states), so seals share the job's ids with no new
     * bridge-to-content compile edge. A pack serving no vocabulary
     * refuses loudly — sealing under a guessed id would be a silent
     * default; the pack's own unknown-scope refusal propagates untouched.
     */
    private StateVocabulary vocabulary(String scope, String code) {
        if (wires.isEmpty()) {
            throw new IllegalStateException(code + ":nowire (want a "
                    + "wired pack to serve the " + scope + " vocabulary)");
        }
        ContentPack pack = wires.get(0).pack();
        if (!(pack instanceof VocabularyPack)) {
            throw new IllegalArgumentException(code + ":novocab <"
                    + pack.getClass().getName() + "> (pack serves no "
                    + scope + " vocabulary)");
        }
        return ((VocabularyPack) pack).vocabulary(scope);
    }

    /**
      * T4 pack-driven policy (hub
      * {@code decisions/SPI_STATE_VOCABULARY.md}): tables, jobs and harvest
      * kinds come from the first wire's reflectively loaded pack at wire
      * time (parse-once, never on the tick path — the pack sealed them
      * beside its states), so the forge wire carries no content import.
      * A pack serving no policy refuses loudly — wiring numbers the pack
      * never sealed would be a silent default.
      */
    private PolicyPack policy(String code) {
        if (wires.isEmpty()) {
            throw new IllegalStateException(code + ":nowire (want a "
                    + "wired pack to serve the policy)");
        }
        ContentPack pack = wires.get(0).pack();
        if (!(pack instanceof PolicyPack)) {
            throw new IllegalArgumentException(code + ":nopolicy <"
                    + pack.getClass().getName() + "> (pack serves no "
                    + "loot/spawn/combat policy)");
        }
        return (PolicyPack) pack;
    }

    /**
      * Loot wiring: one table per bridge from the first wire's pack policy
      * (parsed once at pack wire time, like registration — never on the
      * tick path), the content {@code drop_count} unless the operator
      * {@code loot.count} wins, and the ore scope from the operator
      * wire-block column (T2 operator-override tranche — no bridge constant
      * names a loot block). Harvest kinds come from the same policy (never
      * content literals here): a table missing a served kind refuses
      * loudly — an unpaid kind would be a silent no-drop. No owned file
      * anywhere means loot stays passive (Q1 cohabitation): the hooks gate
      * on the null table. Several distinct owned files refuse loudly —
      * silent table picks are defaults.
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
        ownedPath = owned.iterator().next();
        lootVocab = vocabulary(LootStates.SCOPE, "E_LOOT_SEAL");
        PolicyPack policy = policy("E_LOOT_POLICY");
        oreKind = policy.lootOreKind();
        beastKind = policy.lootBeastKind();
        lootTable = policy.lootDrops();
        if (!lootTable.containsKey(oreKind)
                || !lootTable.containsKey(beastKind)) {
            throw new IllegalArgumentException("E_LOOT_TABLE:kind <"
                    + new ArrayList<String>(lootTable.keySet())
                    + "> (want <" + oreKind + "> + <" + beastKind + ">)");
        }
        loot = policy.lootJob();
        lootCount = OperatorPolicy.effectiveLootCount(policy.lootCount(),
                specs);
        for (String name : OperatorPolicy.wireBlocks(specs)) {
            Block block = Block.getBlockFromName(name);
            if (block == null) {
                throw new IllegalArgumentException("E_LOOT_ORE:unknown <"
                        + name + ">");
            }
            oreNames.add(name);
            ores.add(block);
        }
        String lootNote = OperatorPolicy.present(specs,
                OperatorPolicy.LOOT_COUNT) ? " overridden <loot.count>"
                : "";
        System.out.println("[MatouBridge] loot wired <" + lootTable
                + "> count <" + lootCount + "> ore <" + oreNames + ">"
                + lootNote);
        for (String dropRef : lootTable.values()) {
            if (resolveItem(dropRef) == null) {
                throw new IllegalArgumentException("E_LOOT_ITEM:unknown <"
                        + dropRef + ">");
            }
        }
    }

    /**
      * Spawn wiring: the single mob ref plus its spec hp plus the
      * effective spawn policy from the first wire's pack policy (parsed
      * once at pack wire time, like registration — never on the tick
      * path): content cap/budget/band unless the operator
      * {@code spawn.*} wins (T2 operator-override tranche). The hp lands
     * on the beast's max-health attribute at every landing (hp tranche,
     * hub decisions/SPAWN.md) — a spec field with no live reader would
     * be a silent default; the same holds for the effective policy. No
     * owned file anywhere means spawn stays passive (Q1 cohabitation):
     * the hooks gate on the null mob.
     */
    private void wireSpawn(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        spawnVocab = vocabulary(SpawnStates.SCOPE, "E_SPAWN_SEAL");
        PolicyPack policy = policy("E_SPAWN_POLICY");
        spawnMob = policy.spawnMob();
        spawnHp = policy.spawnHp();
        spawn = policy.spawnJob();
        long[] eff = OperatorPolicy.effectiveSpawn(policy.spawnCap(),
                policy.spawnBudget(), policy.spawnYMin(),
                policy.spawnYMax(), specs);
        spawnCap = eff[0];
        spawnBudget = eff[1];
        spawnYMin = eff[2];
        spawnYMax = eff[3];
        List<String> over = new ArrayList<String>();
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_CAP)) {
            over.add("cap");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_BUDGET)) {
            over.add("budget");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_Y_MIN)) {
            over.add("y_min");
        }
        if (OperatorPolicy.present(specs, OperatorPolicy.SPAWN_Y_MAX)) {
            over.add("y_max");
        }
        String spawnNote = over.isEmpty() ? ""
                : " overridden <" + join(over) + ">";
        System.out.println("[MatouBridge] spawn wired <" + spawnMob
                + "> hp <" + spawnHp + "> cap <" + spawnCap
                + "> budget <" + spawnBudget + "> y <" + spawnYMin
                + ".." + spawnYMax + ">" + spawnNote);
    }

    /**
     * Combat wiring: the weakspot table plus the reach attribute from
     * the first wire's pack policy (parsed once at pack wire time, like
     * loot/spawn — never on the tick path). The table seals into the
     * bridge model holder the beast reads at hit time; the reach lands
     * on the hook's ray-test cutoff. No owned file anywhere means
     * combat stays passive (Q1 cohabitation): the seal stays empty and
     * any hit-time read refuses loudly instead of defaulting 1.0x.
     */
    private void wireCombat(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        PolicyPack policy = policy("E_COMBAT_POLICY");
        BeastModel.sealWeakspots(policy.combatWeakspots());
        combatReach = policy.combatReach();
        System.out.println("[MatouBridge] combat wired <"
                + policy.combatWeakspots() + "> reach <" + combatReach
                + ">");
    }

    /** Comma join for the override log suffix (Java 8, no extra dep). */    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(p);
        }
        return out.toString();
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
        spawnTick(event.world, tick);
        tick++;
    }

    /**
     * Loot record: a server-side dim-0 harvest of an operator wire block
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
        if (!ores.contains(event.block)) {
            return;
        }
        String harvest = Cell.of(event.x, event.y, event.z,
                oreKind).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Loot record: a server-side dim-0 mob kill becomes a beast harvest
     * at the entity's block coords. Single-table scope (hub
     * decisions/LOOT.md): every kill pays the one entry — per-mob
     * filtering is a re-opener, never a quiet filter here.
     *
     * <p>Owner discipline (measured live: NoSuchFieldError worldObj):
     * reobf only walks in-jar superclass chains, and stub supertypes
     * never ship — so inherited vanilla members are read through the
     * declaring stub type ({@code Entity}), never through the event's
     * {@code EntityLivingBase}.
     */
    @SubscribeEvent
    public void onKill(LivingDropsEvent event) {
        Entity body = event.entityLiving;
        if (spawnMob != null && body instanceof MatouEntity) {
            // Owner discipline (measured live on loot: reobf only walks
            // in-jar superclass chains, stub supertypes never ship) —
            // the id is read through the declaring stub type (Entity).
            // Unknown ids are not ours (a vanilla beast dies without ever
            // being recorded): false, never a refusal.
            census.release(Integer.toString(body.getEntityId()));
        }
        if (lootTable == null) {
            return;
        }
        if (body.worldObj.isRemote) {
            return;
        }
        if (body.worldObj.provider.dimensionId != 0) {
            return;
        }
        int x = (int) Math.floor(body.posX);
        int y = (int) Math.floor(body.posY);
        int z = (int) Math.floor(body.posZ);
        String harvest = Cell.of(x, y, z, beastKind).render();
        drops.record(harvest, tick);
        System.out.println("[MatouBridge] loot recorded <" + harvest
                + "> at tick " + tick);
    }

    /**
     * Combat hook (hub decisions/VIRTUAL_HITBOXES.md, server weakspot
     * hook — ported from the 1122 lead): a server-side dim-0 hurt on the
     * registered beast resolves the struck bone through the pure SPI
     * ray-test and scales the vanilla amount by the bone weakspot
     * multiplier (head 2x). Same fallback discipline (environmental /
     * glancing / non-beast keeps vanilla silently, corrupt attacker
     * state refuses loudly out of the SPI constructors
     * ({@code E_HIT_VEC:nan} / {@code E_HIT_DIR:zero})) and same
     * non-goal (the vanilla BUG-042 pre-rejection stays vanilla).
     *
     * <p>1614-native spelling (measured via javap against the 1614
     * universal + srg-mcp.srg, never ported blind from 1122): the hurt
     * entity rides the public {@code LivingEvent.entityLiving} field,
     * the source rides the public {@code LivingHurtEvent.source} field,
     * the true attacker rides {@code DamageSource.getEntity} (the 1122
     * {@code getTrueSource} does not exist here — same searge
     * {@code func_76346_g}), the amount rides the public
     * {@code LivingHurtEvent.ammount} field (Forge typo, mirrored
     * verbatim), eye/look ride the declaring {@code Entity} type
     * ({@code getLookVec}/{@code getEyeHeight}), and the look
     * components ride {@code Vec3.xCoord/yCoord/zCoord} (no
     * {@code Vec3d} here). The SPI {@code Vec3d} is fully qualified
     * (the MC look type owns the short name on 1122; here there is no
     * clash, kept qualified for port symmetry).
     */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof MatouEntity)) {
            return;
        }
        Entity body = event.entityLiving;
        if (body.worldObj.isRemote) {
            return;
        }
        if (body.worldObj.provider.dimensionId != 0) {
            return;
        }
        if (event.source == null) {
            return;
        }
        Entity attacker = event.source.getEntity();
        if (attacker == null) {
            return;
        }
        Vec3 look = attacker.getLookVec();
        fr.iamacat.spi.hit.Vec3d origin = new fr.iamacat.spi.hit.Vec3d(
                attacker.posX, attacker.posY + attacker.getEyeHeight(),
                attacker.posZ);
        fr.iamacat.spi.hit.Vec3d dir = new fr.iamacat.spi.hit.Vec3d(
                look.xCoord, look.yCoord, look.zCoord);
        RayHit hit = HitTester.test((MatouEntity) body, origin, dir,
                combatReach);
        if (hit == null) {
            return;
        }
        float before = event.ammount;
        float mult = ((MatouEntity) body).weakspotMultiplier(hit.boneName);
        event.ammount = before * mult;
        System.out.println("[MatouBridge] combat resolved <bone="
                + hit.boneName + " mult=" + mult + " dmg=" + before + "->"
                + event.ammount + ">");
    }

    /**
     * Spawn census: every server-side dim-0 beast join is recorded under
     * its entity id — own landings (which also fire this event, recorded
     * again here idempotently) and foreign beast joins alike. Recording
     * every join the veto lets through is what keeps the census equal to
     * the living reality: a join past the cap is refused instead (the
     * budget never decided it), anything else joins the census the pure
     * budget counts. Vanilla pigs are a different species (ignored here,
     * never vetoed). Passive without a wired mob, and passive unless
     * {@code SPAWN=1} (the union and loot runs never see a beast,
     * recorded or otherwise).
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (!SPAWN || spawnMob == null) {
            return;
        }
        if (event.world.isRemote) {
            return;
        }
        if (event.world.provider.dimensionId != 0) {
            return;
        }
        if (!(event.entity instanceof MatouEntity)) {
            return;
        }
        // Owner discipline (measured live on loot: reobf only walks
        // in-jar superclass chains, stub supertypes never ship) — the id
        // goes through the declaring stub type (Entity), and the joined
        // entity resolves through its declaring base (EntityEvent), never
        // through the beast or the join subclass.
        Entity body = event.entity;
        if (census.size() >= spawnCap) {
            event.setCanceled(true);
            System.out.println("[MatouBridge] spawn vetoed <beast> at tick "
                    + tick + " (census at cap " + spawnCap + ")");
            return;
        }
        int x = (int) Math.floor(body.posX);
        int y = (int) Math.floor(body.posY);
        int z = (int) Math.floor(body.posZ);
        String cell = Cell.of(x, y, z, spawnMob).render();
        census.record(Integer.toString(body.getEntityId()), cell, tick);
        System.out.println("[MatouBridge] spawn joined <" + cell
                + "> at tick " + tick);
    }

    /**
     * Spawn seal: census plus table, cap, budget and band beside the
     * first wire's pack states, pure decide, land one beast per due slot,
     * record every landing. The census is reconciled first (see
     * {@link #reconcile}): the join event misses silent paths (measured
     * live: a natural grass spawn bypassed it and breached the cap), so
     * the sealed census is the polled living reality, never the event
     * trail alone. The budgeted slots the etage-1 gate holds equal to
     * the job decision size are re-checked loudly here: a live divergence
     * (slots != decided) fails the tick instead of spawning off-budget
     * silently. Passive without a wired pack or mob, and passive unless
     * {@code SPAWN=1}.
     */
    private void spawnTick(World world, long now) {
        if (!SPAWN || spawnMob == null || wires.isEmpty()) {
            return;
        }
        reconcile(world, now);
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>(
                wires.get(0).states(now));
        states.putAll(SpawnSeal.seal(spawnVocab, census, spawnMob,
                spawnCap, spawnBudget, spawnYMin, spawnYMax));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = spawn.decide(snap);
        int slots = census.slotsDue((int) spawnCap, (int) spawnBudget);
        if (slots != due.size()) {
            throw new IllegalStateException("E_SPAWN_SEAL:diverged <slots="
                    + slots + " due=" + due + "> at tick " + now);
        }
        for (String cell : due) {
            ForgeCells.BlockCell pad = ForgeCells.parseBlockCell(cell);
            landBeast(world, pad.x, pad.y, pad.z, cell, now);
        }
        if (!due.isEmpty()) {
            System.out.println("[MatouBridge] spawn landed "
                    + due.size() + " beast(s) at tick " + now);
        }
    }

    /**
     * Spawn reconcile: adopt every living dim-0 beast the census does
     * not know, sweep every census id no longer living. The join event
     * stays (prompt record plus the past-cap veto), but it misses silent
     * paths — measured live on Forge 1614: a natural grass spawn never
     * fired it, a landing-only census undercounted reality and the fifth
     * living beast breached the cap loudly in the proof. The poll is the
     * census of record; events are the fast path. Adopted cells carry the
     * spawn mob ref at the current pos (tranche 1: every dim-0 beast
     * carries our loot through the single-table kill hook, so the pure
     * foreign rule holds). Tranche-1 scope: beasts outside the loaded set
     * sweep — the proof world keeps them loaded; a rejoin re-adopts next
     * tick.
     *
     * <p>Owner discipline (measured live on loot): inherited vanilla
     * members go through the declaring stub type ({@code Entity}), never
     * through the beast.
     */
    private void reconcile(World world, long now) {
        Map<String, String> living = new LinkedHashMap<String, String>();
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof MatouEntity)) {
                continue;
            }
            Entity body = (Entity) o;
            if (body.isDead) {
                continue;
            }
            int x = (int) Math.floor(body.posX);
            int y = (int) Math.floor(body.posY);
            int z = (int) Math.floor(body.posZ);
            living.put(Integer.toString(body.getEntityId()),
                    Cell.of(x, y, z, spawnMob).render());
        }
        for (Map.Entry<String, String> e : living.entrySet()) {
            if (!census.sealed().containsKey(e.getKey())) {
                census.record(e.getKey(), e.getValue(), now);
                System.out.println("[MatouBridge] spawn adopted <"
                        + e.getValue() + "> at tick " + now);
            }
        }
        for (String id : census.sealed().keySet()) {
            if (!living.containsKey(id)) {
                census.release(id);
                System.out.println("[MatouBridge] spawn swept <" + id
                        + "> at tick " + now);
            }
        }
    }

    /**
     * Spawn landing: one registered beast per due slot at the decided
     * pad, recorded into the census under its entity id. The content hp
     * lands on the beast's max-health attribute before the spawn (hp
     * tranche, hub decisions/SPAWN.md) and the read-back is tripwired:
     * a beast that does not carry the spec hp fails the tick instead of
     * roaming underpowered silently. A refused spawn fails loudly — an
     * unrecorded beast is census drift silently otherwise.
     *
     * <p>Owner discipline (measured live on loot: NoSuchFieldError posX):
     * reobf only walks in-jar superclass chains, and stub supertypes
     * never ship — so inherited vanilla members go through the declaring
     * stub type ({@code Entity}, {@code EntityLivingBase},
     * {@code SharedMonsterAttributes}), never through the beast.
     */
    private void landBeast(World world, int x, int y, int z, String cell,
            long now) {
        MatouEntity beast = new MatouEntity(world);
        Entity body = beast;
        EntityLivingBase living = beast;
        living.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue((double) spawnHp);
        living.setHealth((float) spawnHp);
        if (living.getMaxHealth() != (float) spawnHp) {
            throw new IllegalStateException("E_SPAWN_HP:diverged <want="
                    + spawnHp + " got=" + living.getMaxHealth()
                    + "> at tick " + now);
        }
        body.setPositionAndRotation(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
        if (!world.spawnEntityInWorld(beast)) {
            throw new IllegalStateException("E_SPAWN_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
        census.record(Integer.toString(body.getEntityId()), cell, now);
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
        states.putAll(LootSeal.seal(lootVocab, drops, lootTable,
                lootCount));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = loot.decide(snap);
        for (String cell : due) {
            ForgeCells.BlockCell vol = ForgeCells.parseBlockCell(cell);
            dropCarrier(world, vol.x, vol.y, vol.z, vol.block);
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
            for (long c = 0; c < lootCount; c++) {
                out.add(head + ":" + item);
            }
        }
        return out;
    }

    /**
     * Loot landing: one registered-item carrier per due drop, beside the
     * vanilla drops (never replacing them). A refused spawn fails loudly
     * — a lost carrier is loot lost silently otherwise.
     */
    private void dropCarrier(World world, int x, int y, int z, String itemRef) {
        Item item = resolveItem(itemRef);
        if (item == null) {
            throw new IllegalStateException("E_LOOT_ITEM:unknown <" + itemRef + ">");
        }
        EntityItem carrier = new EntityItem(world, x + 0.5, y + 0.5,
                z + 0.5, new ItemStack(item, 1));
        if (!world.spawnEntityInWorld(carrier)) {
            throw new IllegalStateException("E_LOOT_SPAWN:refused <" + x
                    + "," + y + "," + z + ">");
        }
    }

    static Item resolveItem(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        int colon = ref.indexOf(':');
        if (colon < 0) {
            return GameRegistry.findItem("example1", ref);
        }
        String prefix = ref.substring(0, colon);
        String name = ref.substring(colon + 1);
        Item item = GameRegistry.findItem(prefix, name);
        if (item != null) {
            return item;
        }
        int dot = prefix.indexOf('.');
        if (dot > 0) {
            String modId = prefix.substring(0, dot);
            item = GameRegistry.findItem(modId, name);
            if (item != null) {
                return item;
            }
        }
        return null;
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
