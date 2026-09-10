package fr.iamacat.bridge.loot;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.example1.ExamplePack;
import fr.iamacat.example1.LootJob;
import fr.iamacat.example1.LootTable;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot tranche gate (no JUnit): harvest-store semantics plus pure loot
 * decisions plus the store-vs-job comparateur (both paths over the same
 * sealed states stay equal up to the table expansion). Any violation
 * prints {@code FAIL spike-loot : ...} and exits 1. Run by tools/check.sh
 * etage 1. Zero Minecraft.
 */
public final class LootCheck {
    private LootCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL spike-loot : " + what);
            System.exit(1);
        }
        System.out.println("ok spike-loot : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok spike-loot : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-loot : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok spike-loot : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-loot : accepted " + what);
        System.exit(1);
    }

    private static Map<String, String> table() {
        return LootTable.fromFile(
                "../example1/content/owned.matou").drops();
    }

    private static long tableCount() {
        return LootTable.fromFile(
                "../example1/content/owned.matou").count();
    }

    private static StateVocabulary vocab() {
        // T3 provision path: the vocabulary the forge wire serves from
        // the reflectively loaded pack — the gate drives the shipped
        // seal through it, never through job imports.
        return new ExamplePack().vocabulary(LootStates.SCOPE);
    }

    private static Snapshot seal(DropStore store, long tick) {
        Map<MatouId, Object> states =
                LootSeal.seal(vocab(), store, table(), tableCount());
        return ForgeSnapshot.snapshot(tick, states);
    }

    private static DropStore seeded() {
        DropStore s = new DropStore();
        s.record("8,10,8:" + LootJob.ORE, 100L);
        s.record("12,10,8:" + LootJob.BEAST, 110L);
        return s;
    }

    private static Packs.PackSpec spec(String block, String... kv) {
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("ownedFile", "o");
        for (int i = 0; i < kv.length; i += 2) {
            args.put(kv[i], kv[i + 1]);
        }
        return new Packs.PackSpec("fr.iamacat.example1.ExamplePack", 63,
                block, args);
    }

    private static List<Packs.PackSpec> specs(Packs.PackSpec... ss) {
        List<Packs.PackSpec> out = new ArrayList<Packs.PackSpec>();
        for (Packs.PackSpec s : ss) {
            out.add(s);
        }
        return out;
    }

    /**
     * Table expansion: what the pure decision must equal for a claimed
     * harvest list (kind to item, count copies each, order kept).
     */
    private static List<String> expand(List<String> claimed,
            Map<String, String> table, long count) {
        List<String> out = new ArrayList<String>();
        for (String harvest : claimed) {
            int cut = harvest.indexOf(':');
            String head = harvest.substring(0, cut);
            String kind = harvest.substring(cut + 1);
            String item = table.get(kind);
            for (long c = 0; c < count; c++) {
                out.add(head + ":" + item);
            }
        }
        return out;
    }

    public static void main(String[] args) {
        LootJob job = new LootJob();

        // Table wires from the sibling content (single mob funds ore+beast,
        // count included — the gate proves the content-decided number rides
        // the seal, never a bridge constant).
        Map<String, String> wires = table();
        check(wires.size() == 2, "table wires 2 kinds");
        check("example1.content:my_gem".equals(wires.get(LootJob.ORE)),
                "table ore pays gem");
        check("example1.content:my_gem".equals(wires.get(LootJob.BEAST)),
                "table beast pays gem");
        check(tableCount() == 1L, "table wires authorial count");

        // Store: record then claim on both sides of the boundary.
        DropStore store = new DropStore();
        store.record("8,10,8:" + LootJob.ORE, 100L);
        check(store.size() == 1, "recorded 1 harvest");
        check(store.claimDue(99L).isEmpty(),
                "nothing due before harvest tick");
        List<String> due = store.claimDue(100L);
        check(due.size() == 1
                && due.get(0).equals("8,10,8:" + LootJob.ORE),
                "boundary harvest due at harvestTick == now");
        check(store.size() == 0, "claimed harvests leave the store");
        store.record("8,10,8:" + LootJob.ORE, 200L);
        check(store.claimDue(300L).size() == 1,
                "re-harvest re-records with the latest tick");

        // Store refusals.
        final DropStore bad = new DropStore();
        expectNPE(new Runnable() {
            @Override public void run() { bad.record(null, 0L); }
        }, "null harvest");
        expectIAE(new Runnable() {
            @Override public void run() { bad.record("x", -1L); }
        }, "negative record tick");
        expectIAE(new Runnable() {
            @Override public void run() { bad.claimDue(-1L); }
        }, "negative claim tick");

        // Job: pure decision over sealed states, boundary included.
        DropStore js = new DropStore();
        js.record("8,10,8:" + LootJob.ORE, 100L);
        js.record("12,10,8:" + LootJob.BEAST, 110L);
        List<String> jdue = job.decide(seal(js, 110L));
        check(jdue.equals(expand(seeded().claimDue(110L), wires, 1L)),
                "job decides the table expansion");
        check(jdue.size() == 2
                && jdue.get(0).equals(
                        "8,10,8:example1.content:my_gem"),
                "job pays the gem at the harvest pos");
        check(job.decide(seal(js, 99L)).isEmpty(),
                "job emits nothing before harvest tick");

        // Job refusals: missing ids fail at the seam, never silently.
        final Snapshot bare = ForgeSnapshot.snapshot(
                0L, new HashMap<MatouId, Object>());
        expectIAE(new Runnable() {
            @Override public void run() { job.decide(bare); }
        }, "missing loot ids");

        // Seal: contents, copy isolation, refusals (the helper above
        // routes through the shipped LootSeal, so every assertion below
        // exercises the live path).
        Map<MatouId, Object> st = LootSeal.seal(vocab(), seeded(),
                wires, tableCount());
        check(st.get(LootJob.HARVESTED) instanceof Map,
                "seal carries example1.loot:harvested");
        check(wires.equals(st.get(LootJob.TABLE)),
                "seal carries example1.loot:table");
        check(Long.valueOf(tableCount()).equals(st.get(LootJob.COUNT)),
                "seal carries example1.loot:count");
        check(new ArrayList<MatouId>(st.keySet()).equals(Arrays.asList(
                LootJob.HARVESTED, LootJob.TABLE, LootJob.COUNT)),
                "seal keys follow the vocabulary order");
        DropStore iso = new DropStore();
        iso.record("1,2,3:" + LootJob.ORE, 5L);
        Map<MatouId, Object> snap0 = LootSeal.seal(vocab(), iso, wires,
                tableCount());
        iso.record("9,9,9:" + LootJob.BEAST, 6L);
        check(((Map<?, ?>) snap0.get(LootJob.HARVESTED)).size() == 1,
                "sealed harvests are a copy, later records never leak");
        Map<String, String> mut = new LinkedHashMap<String, String>(wires);
        Map<MatouId, Object> snapT = LootSeal.seal(vocab(), seeded(),
                mut, tableCount());
        mut.put(LootJob.ORE, "example1.content:nope");
        check(wires.equals(snapT.get(LootJob.TABLE)),
                "sealed table is a copy, later edits never leak");
        final DropStore nullStore = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(null, nullStore, table(), 1L);
            }
        }, "null vocabulary");
        expectNPE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(vocab(), nullStore, table(), 1L);
            }
        }, "null store");
        expectNPE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(vocab(), seeded(), null, 1L);
            }
        }, "null table");
        expectIAE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(vocab(), seeded(), table(), 0L);
            }
        }, "zero count");
        final StateVocabulary foreign =
                new ExamplePack().vocabulary(SpawnStates.SCOPE);
        expectIAE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(foreign, seeded(), table(), 1L);
            }
        }, "spawn vocabulary into loot seal");

        // Comparateur: store claim expanded by the table equals the job
        // decision tick by tick over the same sealed states (same order,
        // same boundary), at count 1 and 2.
        for (long count = 1L; count <= 2L; count++) {
            for (long tick = 95L; tick <= 115L; tick += 5L) {
                List<String> viaStore = seeded().claimDue(tick);
                Map<MatouId, Object> states =
                        LootSeal.seal(vocab(), seeded(), wires, count);
                List<String> viaJob = job.decide(
                        ForgeSnapshot.snapshot(tick, states));
                check(viaJob.equals(expand(viaStore, wires, count)),
                        "store claim == job decision at tick " + tick
                                + " x" + count);
            }
        }

        // Operator overrides (T2, hub decisions/SPAWN.md): absent means
        // content, present wins, bad refuses loudly — the same rule the
        // forge wire consumes, exercised here through the shipped
        // OperatorPolicy. The ore scope is the wire-block column, never
        // a bridge constant.
        final long contentCount = tableCount();
        check(OperatorPolicy.effectiveLootCount(contentCount,
                specs(spec("example1:my_ore"))) == 1L,
                "operator absent means content count");
        check(OperatorPolicy.effectiveLootCount(contentCount,
                specs(spec("example1:my_ore", "loot.count", "2"))) == 2L,
                "operator loot.count wins over content");
        DropStore cs = new DropStore();
        cs.record("8,10,8:" + LootJob.ORE, 100L);
        cs.record("12,10,8:" + LootJob.BEAST, 110L);
        long overCount = OperatorPolicy.effectiveLootCount(contentCount,
                specs(spec("example1:my_ore", "loot.count", "2")));
        Map<MatouId, Object> overStates =
                LootSeal.seal(vocab(), cs, wires, overCount);
        check(job.decide(ForgeSnapshot.snapshot(110L, overStates)).equals(
                expand(seeded().claimDue(110L), wires, 2L)),
                "overridden count seals and decides the x2 expansion");
        List<String> one = OperatorPolicy.wireBlocks(
                specs(spec("example1:my_ore")));
        check(one.size() == 1 && one.get(0).equals("example1:my_ore"),
                "wire block names the loot ore scope");
        List<String> ordered = OperatorPolicy.wireBlocks(specs(
                spec("example1:my_ore"), spec("minecraft:stone")));
        check(ordered.size() == 2
                && ordered.get(0).equals("example1:my_ore")
                && ordered.get(1).equals("minecraft:stone"),
                "wire blocks keep line order");
        List<String> dedup = OperatorPolicy.wireBlocks(specs(
                spec("example1:my_ore"), spec("example1:my_ore")));
        check(dedup.size() == 1
                && dedup.get(0).equals("example1:my_ore"),
                "duplicate wire blocks dedupe");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(contentCount,
                        specs(spec("example1:my_ore",
                                "loot.count", "0")));
            }
        }, "zero operator count");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(contentCount,
                        specs(spec("example1:my_ore",
                                "loot.count", "-1")));
            }
        }, "negative operator count");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(contentCount,
                        specs(spec("example1:my_ore",
                                "loot.count", "x")));
            }
        }, "non-numeric operator count");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(contentCount,
                        specs(spec("example1:my_ore", "loot.count", "1"),
                                spec("example1:my_ore",
                                        "loot.count", "2")));
            }
        }, "differing operator count");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(contentCount,
                        specs(spec("example1:my_ore",
                                "loot.cout", "2")));
            }
        }, "unknown operator key");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.wireBlocks(specs(spec("my_ore")));
            }
        }, "bare wire block");
        expectNPE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveLootCount(1L, null);
            }
        }, "null specs");
        System.out.println("ok spike-loot : all");
    }
}
