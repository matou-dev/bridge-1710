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
import java.util.Arrays;
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
 * names a loot block); each kind seals its content {@code drop_count}
 * unless the operator {@code loot.count} wins uniformly
 * (distinct-drops tranche, hub decisions/LOOT.md — the ore kind pays
 * the first sealed mob, each {@code beast.<mob>} kind pays its mob). Due
 * drops land as {@link EntityItem} carriers beside the
 * vanilla drops (vanilla behaviour untouched). The carrier is vanilla
 * diamond until item registration lands on the REGISTRATION path; every
 * dim-0 kill pays through the per-mob table (hub decisions/LOOT.md). New refusals stay loot-local ({@code E_LOOT_*},
 * never in the {@code E_FORGE_*} parity catalog), so bridge parity holds
 * with behaviour intentionally 1710-only until proven.
 *
 * <p>Spawn (event-sourced, hub decisions/SPAWN.md): the pure
 * pack-served spawn job reads the bridge-owned {@link SpawnStore} census plus
 * the wired per-mob spawn tables beside the first wire's pack states
 * (the pack-served spawn vocabulary, T3 registry — hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}) and decides budgeted spawns
 * per mob; due spawns land as the registered custom beast
 * ({@link MatouEntity}, pig shape and renderer reused — registration-path
 * tranche, hub decisions/REGISTRATION.md), every beast carrying its own
 * short mob identity (per-mob tranche, hub
 * {@code decisions/VIRTUAL_HITBOXES.md}). A live {@code slots != due}
 * divergence fails the tick loudly ({@code E_SPAWN_SEAL:diverged},
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
    /** Spawn policy, sealed per mob from the content table at wire time
     * unless the operator {@code spawn.*} wins (hub decisions/SPAWN.md
     * operator-override tranche plus the per-mob tranche in hub
     * decisions/VIRTUAL_HITBOXES.md): effective cap, per-tick budget
     * and y band by qualified mob ref, in seal order — the per-mob key
     * wins, else the global key, else content. The bridge transports
     * them into the seal, it never owns a spawn number. The companion
     * mirrors the effective cap (see its SPAWN_CAP note). */
    private final LinkedHashMap<String, Long> spawnCap =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnBudget =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnYMin =
            new LinkedHashMap<String, Long>();
    private final LinkedHashMap<String, Long> spawnYMax =
            new LinkedHashMap<String, Long>();
    /** Spec hp by qualified mob ref, in seal order (content-only, same
     * split as the weakspot multipliers — no operator key, never a quiet
     * knob). */
    private final LinkedHashMap<String, Long> spawnHp =
            new LinkedHashMap<String, Long>();
    /** Combat reach, sealed per mob from the content table at wire time
     * (hub decisions/VIRTUAL_HITBOXES.md combat-policy tranche):
     * effective eye-to-hitVec cutoff by short mob name, in seal order —
     * content reach unless the operator {@code combat.reach} wins
     * globally or {@code combat.reach.<mob>} wins for that mob
     * (per-mob tranche, same precedence as spawn). The bridge
     * transports it into the hook, it never owns a combat number. */
    private final LinkedHashMap<String, Double> combatReach =
            new LinkedHashMap<String, Double>();
    /** Loot policy, sealed per kind from the content table at wire time
     * unless the operator {@code loot.count} wins uniformly
     * (distinct-drops tranche, hub decisions/LOOT.md): effective items
     * per harvest by harvest kind (the ore kind plus one
     * {@code beast.<mob>} kind per sealed mob). Transported, never
     * owned. */
    private final LinkedHashMap<String, Long> lootCounts =
            new LinkedHashMap<String, Long>();
    /** Beast harvest kinds by short mob name, in seal order
     * (distinct-drops tranche): the kill hook records the victim's mob
     * identity through this map. */
    private final LinkedHashMap<String, String> lootBeastKinds =
            new LinkedHashMap<String, String>();
    /** Sealed loot mobs, short names in file order (the ore harvest and
     * non-beast kills pay the first — same first-mob rule as the
     * content builders). */
    private final List<String> lootMobs = new ArrayList<String>();

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
    private StateVocabulary lootVocab;
    /** First sealed qualified mob ref (null when passive — Q1
     * cohabitation): the hooks gate on {@link #spawnMobs} being empty
     * (the same invariant), this stays for cells and logs where the
     * first mob is the sensible sole view. */
    private String spawnMob;
    /** Sealed mobs, short name to qualified ref, in seal order: the
     * per-mob dispatch truth (census cells, seals and landings key off
     * it; empty means passive). */
    private final LinkedHashMap<String, String> spawnMobs =
            new LinkedHashMap<String, String>();
    private StateVocabulary spawnVocab;
    /** Owned content path (shared with the future spawn wire — same file
     * funds both tables, parsed once here). */
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
      * tick path): the ore kind pays the first sealed mob, one
      * {@code beast.<mob>} kind per sealed mob pays that mob (hub
      * decisions/LOOT.md distinct-drops tranche), each kind sealing its
      * content {@code drop_count} unless the operator {@code loot.count}
      * wins uniformly, plus the ore scope from the operator wire-block
      * column (T2 operator-override tranche — no bridge constant names a
      * loot block). Harvest kinds come from the same policy (never
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
        lootMobs.addAll(policy.lootMobs());
        lootTable = new LinkedHashMap<String, String>();
        lootTable.put(oreKind, policy.lootDrop(lootMobs.get(0)));
        Map<String, Long> contentCounts =
                new LinkedHashMap<String, Long>();
        contentCounts.put(oreKind,
                Long.valueOf(policy.lootCount(lootMobs.get(0))));
        for (String mob : lootMobs) {
            String kind = policy.lootBeastKind(mob);
            lootBeastKinds.put(mob, kind);
            lootTable.put(kind, policy.lootDrop(mob));
            contentCounts.put(kind,
                    Long.valueOf(policy.lootCount(mob)));
        }
        for (String kind : lootBeastKinds.values()) {
            if (!lootTable.containsKey(oreKind)
                    || !lootTable.containsKey(kind)) {
                throw new IllegalArgumentException("E_LOOT_TABLE:kind <"
                        + new ArrayList<String>(lootTable.keySet())
                        + "> (want <" + oreKind + "> + <" + kind + ">)");
            }
        }
        loot = policy.lootJob();
        lootCounts.putAll(OperatorPolicy.effectiveLootCounts(
                contentCounts, specs));
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
                + "> count <" + lootCounts + "> ore <" + oreNames + ">"
                + lootNote);
        for (String dropRef : lootTable.values()) {
            if (resolveItem(dropRef) == null) {
                throw new IllegalArgumentException("E_LOOT_ITEM:unknown <"
                        + dropRef + ">");
            }
        }
    }

    /**
      * Spawn wiring: the sealed mobs plus their spec hp plus the
      * effective spawn policy from the first wire's pack policy (parsed
      * once at pack wire time, like registration — never on the tick
      * path): content cap/budget/band per mob unless the operator
      * {@code spawn.*} wins (T2 operator-override tranche, per-mob
      * tranche in hub decisions/VIRTUAL_HITBOXES.md — the per-mob key
      * wins for its mob, else the global key wins uniformly, else
      * content). The hp lands on each beast's max-health attribute at
      * every landing (hp tranche, hub decisions/SPAWN.md) — a spec field
      * with no live reader would be a silent default; the same holds for
      * the effective policy. No owned file anywhere means spawn stays
      * passive (Q1 cohabitation): the hooks gate on the empty mob map.
      *
      * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
      * shorts from {@code PolicyPack.spawnMobs()} qualify into cell refs
      * through {@link #contentNamespace} (same-file namespace rule).
      */
    private void wireSpawn(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        spawnVocab = vocabulary(SpawnStates.SCOPE, "E_SPAWN_SEAL");
        PolicyPack policy = policy("E_SPAWN_POLICY");
        spawn = policy.spawnJob();
        String ns = contentNamespace();
        List<String> sealedShorts = new ArrayList<String>(
                policy.spawnMobs());
        for (String shortMob : sealedShorts) {
            long[] eff = OperatorPolicy.effectiveSpawn(
                    policy.spawnCap(shortMob),
                    policy.spawnBudget(shortMob),
                    policy.spawnYMin(shortMob),
                    policy.spawnYMax(shortMob), shortMob, sealedShorts,
                    specs);
            String qualified = ns + ":" + shortMob;
            if (spawnMob == null) {
                spawnMob = qualified;
            }
            spawnMobs.put(shortMob, qualified);
            spawnHp.put(qualified, Long.valueOf(policy.spawnHp(shortMob)));
            spawnCap.put(qualified, Long.valueOf(eff[0]));
            spawnBudget.put(qualified, Long.valueOf(eff[1]));
            spawnYMin.put(qualified, Long.valueOf(eff[2]));
            spawnYMax.put(qualified, Long.valueOf(eff[3]));
        }
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
        for (String shortMob : sealedShorts) {
            if (OperatorPolicy.present(specs,
                    OperatorPolicy.SPAWN_CAP + "." + shortMob)) {
                over.add("spawn.cap." + shortMob);
            }
            if (OperatorPolicy.present(specs,
                    OperatorPolicy.SPAWN_BUDGET + "." + shortMob)) {
                over.add("spawn.budget." + shortMob);
            }
            if (OperatorPolicy.present(specs,
                    OperatorPolicy.SPAWN_Y_MIN + "." + shortMob)) {
                over.add("spawn.y_min." + shortMob);
            }
            if (OperatorPolicy.present(specs,
                    OperatorPolicy.SPAWN_Y_MAX + "." + shortMob)) {
                over.add("spawn.y_max." + shortMob);
            }
        }
        String spawnNote = over.isEmpty() ? ""
                : " overridden <" + join(over) + ">";
        if (spawnMobs.size() == 1) {
            System.out.println("[MatouBridge] spawn wired <" + spawnMob
                    + "> hp <" + spawnHp.get(spawnMob) + "> cap <"
                    + spawnCap.get(spawnMob) + "> budget <"
                    + spawnBudget.get(spawnMob) + "> y <"
                    + spawnYMin.get(spawnMob) + ".."
                    + spawnYMax.get(spawnMob) + ">" + spawnNote);
        } else {
            System.out.println("[MatouBridge] spawn wired <"
                    + spawnShapes() + ">" + spawnNote);
        }
    }

    /**
     * Per-mob spawn wire shapes ({@code {short hp <h> cap <c> budget
     * <b> y <min..max>}}, seal order, comma-joined).
     */
    private String spawnShapes() {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, String> e : spawnMobs.entrySet()) {
            String q = e.getValue();
            rows.add("{" + e.getKey() + " hp <" + spawnHp.get(q)
                    + "> cap <" + spawnCap.get(q) + "> budget <"
                    + spawnBudget.get(q) + "> y <" + spawnYMin.get(q)
                    + ".." + spawnYMax.get(q) + ">}");
        }
        return join(rows);
    }

    /**
      * Combat wiring: the per-mob weakspot tables plus the reach
      * attributes from the first wire's pack policy (parsed once at pack
      * wire time, like loot/spawn — never on the tick path). The tables
      * seal into the bridge model holder the beast reads at hit time;
      * each mob's effective reach (content reach unless the operator
      * {@code combat.reach} wins globally or {@code combat.reach.<mob>}
      * wins for that mob — same precedence as spawn, per-mob tranche in
      * hub decisions/VIRTUAL_HITBOXES.md) lands in the hook's per-mob map
      * (reach-override tranche, hub decisions/VIRTUAL_HITBOXES.md;
      * weakspot multipliers stay content-only, same split as
      * {@code spawnHp}). No owned file anywhere means combat stays
      * passive (Q1 cohabitation): the seal stays empty and any hit-time
      * read refuses loudly instead of defaulting 1.0x.
      */
    private void wireCombat(List<Packs.PackSpec> specs) {
        if (ownedPath == null) {
            return;
        }
        PolicyPack policy = policy("E_COMBAT_POLICY");
        Map<String, Map<String, Float>> perMobWeakspots =
                new LinkedHashMap<String, Map<String, Float>>();
        Map<String, Double> perMobReach =
                new LinkedHashMap<String, Double>();
        List<String> sealedMobs = new ArrayList<String>(
                policy.combatMobs());
        for (String mob : sealedMobs) {
            perMobWeakspots.put(mob, policy.combatWeakspots(mob));
            perMobReach.put(mob, Double.valueOf(policy.combatReach(mob)));
            combatReach.put(mob, Double.valueOf(
                    OperatorPolicy.effectiveCombatReach(
                            policy.combatReach(mob), mob, sealedMobs,
                            specs)));
        }
        BeastModel.sealCombat(perMobWeakspots, perMobReach);
        List<String> over = new ArrayList<String>();
        if (OperatorPolicy.present(specs, OperatorPolicy.COMBAT_REACH)) {
            over.add("combat.reach");
        }
        for (String mob : sealedMobs) {
            if (OperatorPolicy.present(specs,
                    OperatorPolicy.COMBAT_REACH + "." + mob)) {
                over.add("combat.reach." + mob);
            }
        }
        String combatNote = over.isEmpty() ? ""
                : " overridden <" + join(over) + ">";
        if (perMobWeakspots.size() == 1) {
            System.out.println("[MatouBridge] combat wired <"
                    + policy.combatWeakspots() + "> reach <"
                    + combatReach.values().iterator().next() + ">"
                    + combatNote);
        } else {
            System.out.println("[MatouBridge] combat wired <"
                    + combatShapes(perMobWeakspots) + "> reach <"
                    + reachShapes() + ">" + combatNote);
        }
    }

    /**
     * Per-mob combat table shapes ({@code {mob={bone=mult, ...}}}, seal
     * order, comma-joined).
     */
    private static String combatShapes(
            Map<String, Map<String, Float>> perMobWeakspots) {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Float>> e
                : perMobWeakspots.entrySet()) {
            rows.add("{" + e.getKey() + "=" + e.getValue() + "}");
        }
        return join(rows);
    }

    /**
     * Per-mob effective reach shapes ({@code {mob=reach}}, seal order,
     * comma-joined).
     */
    private String reachShapes() {
        List<String> rows = new ArrayList<String>();
        for (Map.Entry<String, Double> e : combatReach.entrySet()) {
            rows.add("{" + e.getKey() + "=" + e.getValue() + "}");
        }
        return join(rows);
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
     * Loot record: a server-side dim-0 mob kill becomes a per-mob beast
     * harvest at the entity's block coords (hub decisions/LOOT.md
     * distinct-drops tranche): a registered beast pays its own mob's
     * {@code beast.<mob>} kind through its NBT identity (legacy saves
     * adopt the first sealed mob, same rule as combat), any other kill
     * pays the first sealed mob — the T1 any-kill-pays scope survives
     * per-mob, never a quiet filter here. (The census species narrowed
     * with custom-entity registration; the companion kills registered
     * beasts.)
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
        if (!spawnMobs.isEmpty() && body instanceof MatouEntity) {
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
        String victim = body instanceof MatouEntity
                ? ((MatouEntity) body).mobOrFirst() : lootMobs.get(0);
        String kind = lootBeastKinds.get(victim);
        if (kind == null) {
            throw new IllegalArgumentException("E_LOOT_TABLE:kind <"
                    + victim + "> (want one of " + lootBeastKinds.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        int x = (int) Math.floor(body.posX);
        int y = (int) Math.floor(body.posY);
        int z = (int) Math.floor(body.posZ);
        String harvest = Cell.of(x, y, z, kind).render();
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
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
      * the ray-test cutoff is the victim's per-mob effective reach from
      * the wire-time map (content reach unless the operator
      * {@code combat.reach} wins globally or
      * {@code combat.reach.<mob>} wins for that mob); the multiplier
      * dispatches per mob
     * through the victim's weakspot table. An unsealed mob refuses
     * loudly — never a defaulted reach. Passive without a wired pack
     * (same silent fallback as the unwired reach before).
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
        if (combatReach.isEmpty()) {
            return;
        }
        Entity attacker = event.source.getEntity();
        if (attacker == null) {
            return;
        }
        String shortMob = ((MatouEntity) body).mobOrFirst();
        Double at = combatReach.get(shortMob);
        if (at == null) {
            throw new IllegalStateException("E_COMBAT_WIRE:unmapped mob <"
                    + shortMob + "> (want one of " + combatReach.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        Vec3 look = attacker.getLookVec();
        fr.iamacat.spi.hit.Vec3d origin = new fr.iamacat.spi.hit.Vec3d(
                attacker.posX, attacker.posY + attacker.getEyeHeight(),
                attacker.posZ);
        fr.iamacat.spi.hit.Vec3d dir = new fr.iamacat.spi.hit.Vec3d(
                look.xCoord, look.yCoord, look.zCoord);
        RayHit hit = HitTester.test((MatouEntity) body, origin, dir,
                at.doubleValue());
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
     * the living reality: a join past its mob's cap is refused instead
     * (the budget never decided it), anything else joins the census the
     * pure budget counts. Vanilla pigs are a different species (ignored
     * here, never vetoed). Passive without a wired mob, and passive
     * unless {@code SPAWN=1} (the union and loot runs never see a beast,
     * recorded or otherwise).
     *
     * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
     * the veto counts the census entries carrying the joining entity's
     * own mob against that mob's cap (an unsealed mob refuses loudly);
     * the recorded cell carries the entity's qualified mob ref.
     */
    @SubscribeEvent
    public void onJoin(EntityJoinWorldEvent event) {
        if (!SPAWN || spawnMobs.isEmpty()) {
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
        MatouEntity beast = (MatouEntity) body;
        String qualified = qualifiedMob(beast);
        int count = 0;
        for (String cell : census.sealed().values()) {
            if (cellMob(cell).equals(qualified)) {
                count++;
            }
        }
        long cap = spawnCap.get(qualified).longValue();
        if (count >= cap) {
            event.setCanceled(true);
            System.out.println("[MatouBridge] spawn vetoed <"
                    + beast.mob() + "> at tick "
                    + tick + " (census at cap " + cap + ")");
            return;
        }
        int x = (int) Math.floor(body.posX);
        int y = (int) Math.floor(body.posY);
        int z = (int) Math.floor(body.posZ);
        String cell = Cell.of(x, y, z, qualified).render();
        census.record(Integer.toString(body.getEntityId()), cell, tick);
        System.out.println("[MatouBridge] spawn joined <" + cell
                + "> at tick " + tick);
    }

    /**
      * Spawn seal: census plus per-mob tables, caps, budgets and bands
      * beside the first wire's pack states, pure decide, land one beast
      * per due slot, record every landing. The census is reconciled first
      * (see {@link #reconcile}): the join event misses silent paths
      * (measured live: a natural grass spawn bypassed it and breached
      * the cap), so the sealed census is the polled living reality,
      * never the event trail alone. The budgeted slots the etage-1 gate
      * holds equal to the job decision size are re-checked loudly here
      * per mob, summed: a live divergence (slots != decided) fails the
      * tick instead of spawning off-budget silently. Passive without a
      * wired pack or mob, and passive unless {@code SPAWN=1}.
      *
      * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
      * due cells already carry their mob ({@code x,y,z:mob}) — each
      * landing lands its cell's mob.
      */
    private void spawnTick(World world, long now) {
        if (!SPAWN || spawnMobs.isEmpty() || wires.isEmpty()) {
            return;
        }
        reconcile(world, now);
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>(
                wires.get(0).states(now));
        List<String> table = new ArrayList<String>(spawnMobs.values());
        Map<String, List<Long>> bands =
                new LinkedHashMap<String, List<Long>>();
        for (String qualified : table) {
            bands.put(qualified, Arrays.asList(
                    spawnYMin.get(qualified), spawnYMax.get(qualified)));
        }
        states.putAll(SpawnSeal.seal(spawnVocab, census, table,
                spawnCap, spawnBudget, bands));
        Snapshot snap = ForgeSnapshot.snapshot(now, states);
        List<String> due = spawn.decide(snap);
        Map<String, String> sealed = census.sealed();
        int slots = 0;
        for (String qualified : table) {
            int count = 0;
            for (String cell : sealed.values()) {
                if (cellMob(cell).equals(qualified)) {
                    count++;
                }
            }
            slots += census.slotsDue(count,
                    spawnCap.get(qualified).intValue(),
                    spawnBudget.get(qualified).intValue());
        }
        if (slots != due.size()) {
            throw new IllegalStateException("E_SPAWN_SEAL:diverged <slots="
                    + slots + " due=" + due + "> at tick " + now);
        }
        for (String cell : due) {
            ForgeCells.BlockCell pad = ForgeCells.parseBlockCell(cell);
            landBeast(world, pad.x, pad.y, pad.z, cell, pad.block, now);
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
     * entity's own qualified mob ref at the current pos (per-mob tranche,
     * hub {@code decisions/VIRTUAL_HITBOXES.md} — never a single wired
     * mob). Tranche-1 scope: beasts outside the loaded set sweep — the
     * proof world keeps them loaded; a rejoin re-adopts next tick.
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
                    Cell.of(x, y, z,
                            qualifiedMob((MatouEntity) body)).render());
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
      * tranche, hub decisions/SPAWN.md) and the read-back is tripwired
      * per mob: a beast that does not carry its spec hp fails the tick
      * instead of roaming underpowered silently. A refused spawn fails
      * loudly — an unrecorded beast is census drift silently otherwise.
      *
      * <p>Per-mob tranche (hub {@code decisions/VIRTUAL_HITBOXES.md}):
      * the hp comes from the due cell's own mob map entry (an unsealed
      * mob refuses loudly), and the short mob identity is sealed on the
      * entity before the spawn.
      *
      * <p>Owner discipline (measured live on loot: NoSuchFieldError posX):
      * reobf only walks in-jar superclass chains, and stub supertypes
      * never ship — so inherited vanilla members go through the declaring
      * stub type ({@code Entity}, {@code EntityLivingBase},
      * {@code SharedMonsterAttributes}), never through the beast.
      */
    private void landBeast(World world, int x, int y, int z, String cell,
            String qualifiedMob, long now) {
        Long hp = spawnHp.get(qualifiedMob);
        if (hp == null) {
            throw new IllegalStateException("E_SPAWN_HP:unknown mob <"
                    + qualifiedMob + "> (want one of " + spawnHp.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        MatouEntity beast = new MatouEntity(world);
        beast.setMob(shortName(qualifiedMob));
        Entity body = beast;
        EntityLivingBase living = beast;
        living.getEntityAttribute(SharedMonsterAttributes.maxHealth)
                .setBaseValue(hp.doubleValue());
        living.setHealth(hp.floatValue());
        if (living.getMaxHealth() != hp.floatValue()) {
            throw new IllegalStateException("E_SPAWN_HP:diverged <want="
                    + hp + " got=" + living.getMaxHealth()
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
      * Qualified ref of an entity's mob (adopts with its note when unset);
      * afterwards {@code beast.mob()} is non-null. Loud on an unsealed
      * mob — a census entry nobody budgeted would be silent drift.
      */
    private String qualifiedMob(MatouEntity beast) {
        String shortMob = beast.mobOrFirst();
        String qualified = spawnMobs.get(shortMob);
        if (qualified == null) {
            throw new IllegalStateException("E_SPAWN_MOB:unknown mob <"
                    + shortMob + "> (want one of " + spawnMobs.keySet()
                    + " — sealed mobs only, never defaulted)");
        }
        return qualified;
    }

    /**
      * Short content name past the first colon (same split as
      * {@code Example1Mod.registerBeast} — one convention, not two).
      */
    private static String shortName(String qualifiedMob) {
        if (qualifiedMob == null) {
            throw new NullPointerException("E_SPAWN_MOB:null mob "
                    + "(want a \"ns:mob\" content ref)");
        }
        int colon = qualifiedMob.indexOf(':');
        if (colon < 0 || colon + 1 >= qualifiedMob.length()) {
            throw new IllegalArgumentException("E_SPAWN_MOB:type <"
                    + qualifiedMob + "> (want a \"ns:mob\" content ref)");
        }
        return qualifiedMob.substring(colon + 1);
    }

    /**
      * Content namespace for mob refs, read off the wired loot drop refs:
      * the parser enforces one {@code namespace} per owned file and the
      * spawn wire shares the loot wire's file (one table per bridge), so
      * the drop refs carry the mobs' namespace. Loud when the loot wire
      * never ran or a drop ref is bare — a guessed namespace would be a
      * silent default. (A qualified spawn-table view through
      * {@code PolicyPack} would remove this loot-to-spawn read — named
      * follow-up, zero behaviour difference.)
      */
    private String contentNamespace() {
        if (lootTable == null || lootTable.isEmpty()) {
            throw new IllegalStateException("E_SPAWN_MOB:nowire (want "
                    + "the loot table wired — one owned file funds both "
                    + "tables)");
        }
        String drop = lootTable.values().iterator().next();
        if (drop == null) {
            throw new IllegalStateException("E_SPAWN_MOB:null drop "
                    + "(want a \"ns:item\" drop ref to read the file "
                    + "namespace from)");
        }
        int colon = drop.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("E_SPAWN_MOB:type <"
                    + drop + "> (want a \"ns:item\" drop ref to read "
                    + "the file namespace from)");
        }
        return drop.substring(0, colon);
    }

    /**
      * Census-cell mob suffix (the qualified ref past the first colon —
      * same cut as the job's census rule); a colon-less cell matches no
      * sealed mob.
      */
    private static String cellMob(String cell) {
        int cut = cell.indexOf(':');
        return cut < 0 ? cell : cell.substring(cut + 1);
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
                lootCounts));
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
            String kind = harvest.substring(cut + 1);
            String item = lootTable.get(kind);
            Long count = lootCounts.get(kind);
            if (item == null || count == null) {
                throw new IllegalStateException("E_LOOT_SEAL:diverged <"
                        + harvest + "> (unpaid kind — never a silent "
                        + "no-drop)");
            }
            for (long c = 0; c < count.longValue(); c++) {
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
