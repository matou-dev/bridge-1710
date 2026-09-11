package fr.iamacat.bridge.spawn;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.example1.ExamplePack;
import fr.iamacat.example1.SpawnJob;
import fr.iamacat.example1.SpawnTable;
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
 * Spawn tranche gate (no JUnit): census-store semantics plus pure spawn
 * decisions plus the store-vs-job comparateur (budgeted slots over the
 * sealed census stay equal to the job decision size tick by tick). Any
 * violation prints {@code FAIL spike-spawn : ...} and exits 1. Run by
 * tools/check.sh etage 1. Zero Minecraft.
 */
public final class SpawnCheck {
    private SpawnCheck() {}

    private static final String MOB = "example1.content:my_beast";
    private static final String BRUTE = "example1.content:my_brute";
    private static final String BEAST_SHORT = "my_beast";
    private static final String BRUTE_SHORT = "my_brute";

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL spike-spawn : " + what);
            System.exit(1);
        }
        System.out.println("ok spike-spawn : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok spike-spawn : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-spawn : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok spike-spawn : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-spawn : accepted " + what);
        System.exit(1);
    }

    private static SpawnTable table() {
        return SpawnTable.fromFile(
                "../example1/content/owned.matou");
    }

    private static StateVocabulary vocab() {
        // T3 provision path: the vocabulary the forge wire serves from
        // the reflectively loaded pack — the gate drives the shipped
        // seal through it, never through job imports.
        return new ExamplePack().vocabulary(SpawnStates.SCOPE);
    }

    private static Snapshot seal(SpawnStore store, long tick) {
        SpawnTable wired = table();
        List<String> table = Arrays.asList(MOB, BRUTE);
        Map<String, Long> caps = new LinkedHashMap<String, Long>();
        caps.put(MOB, Long.valueOf(wired.cap(BEAST_SHORT)));
        caps.put(BRUTE, Long.valueOf(wired.cap(BRUTE_SHORT)));
        Map<String, Long> budgets = new LinkedHashMap<String, Long>();
        budgets.put(MOB, Long.valueOf(wired.budget(BEAST_SHORT)));
        budgets.put(BRUTE, Long.valueOf(wired.budget(BRUTE_SHORT)));
        Map<String, List<Long>> bands =
                new LinkedHashMap<String, List<Long>>();
        bands.put(MOB, Arrays.asList(
                Long.valueOf(wired.yMin(BEAST_SHORT)),
                Long.valueOf(wired.yMax(BEAST_SHORT))));
        bands.put(BRUTE, Arrays.asList(
                Long.valueOf(wired.yMin(BRUTE_SHORT)),
                Long.valueOf(wired.yMax(BRUTE_SHORT))));
        Map<MatouId, Object> states =
                SpawnSeal.seal(vocab(), store, table, caps, budgets,
                        bands);
        return ForgeSnapshot.snapshot(tick, states);
    }

    private static SpawnStore seeded() {
        SpawnStore s = new SpawnStore();
        s.record("11", "1,66,2:" + MOB, 100L);
        s.record("23", "3,67,4:" + MOB, 110L);
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

    public static void main(String[] args) {
        SpawnJob job = new SpawnJob();

        // Table wires from the sibling content (both mobs fund spawn,
        // policy included — the gate proves content-decided numbers ride
        // the seal, never bridge constants).
        SpawnTable wired = table();
        check(new ArrayList<String>(wired.mobs()).equals(
                Arrays.asList(BEAST_SHORT, BRUTE_SHORT)),
                "table wires both mobs in file order");
        check(wired.hp(BEAST_SHORT) == 20L
                && wired.hp(BRUTE_SHORT) == 30L,
                "table wires per-mob hp");
        check(wired.cap(BEAST_SHORT) == 4L
                && wired.cap(BRUTE_SHORT) == 4L,
                "table wires per-mob cap");
        check(wired.budget(BEAST_SHORT) == 1L
                && wired.budget(BRUTE_SHORT) == 1L,
                "table wires per-mob budget");
        check(wired.yMin(BEAST_SHORT) == 66L
                && wired.yMax(BEAST_SHORT) == 68L
                && wired.yMin(BRUTE_SHORT) == 66L
                && wired.yMax(BRUTE_SHORT) == 68L,
                "table wires per-mob y band");

        // Store: record, release, budgeted slots on both sides of cap.
        SpawnStore store = new SpawnStore();
        store.record("11", "1,66,2:" + MOB, 100L);
        check(store.size() == 1, "recorded 1 beast");
        check(store.slotsDue(4, 1) == 1, "room 3 budgets 1");
        check(store.slotsDue(4, 2) == 2, "room 3 budgets 2");
        check(store.slotsDue(1, 2) == 0, "cap reached budgets none");
        store.record("23", "3,67,4:" + MOB, 110L);
        store.record("37", "5,68,6:" + MOB, 120L);
        store.record("41", "7,66,8:" + MOB, 130L);
        store.record("53", "9,67,0:" + MOB, 140L);
        check(store.size() == 5, "recorded 5 beasts");
        check(store.slotsDue(4, 2) == 0, "over cap never negative");
        check(store.release("23"), "release known id");
        check(store.size() == 4, "released beast leaves the census");
        check(!store.release("23"), "release unknown id is false");
        check(!store.release("999"), "release foreign id is false");
        check(store.slotsDue(4, 1) == 0, "census back at cap");
        store.record("11", "2,66,3:" + MOB, 200L);
        check(store.size() == 4, "re-record keeps the id");

        // Store refusals.
        final SpawnStore bad = new SpawnStore();
        expectNPE(new Runnable() {
            @Override public void run() { bad.record(null, "c", 0L); }
        }, "null id");
        expectNPE(new Runnable() {
            @Override public void run() { bad.record("1", null, 0L); }
        }, "null cell");
        expectNPE(new Runnable() {
            @Override public void run() { bad.release(null); }
        }, "null release id");
        expectIAE(new Runnable() {
            @Override public void run() { bad.record("x", "c", 0L); }
        }, "non-numeric id");
        expectIAE(new Runnable() {
            @Override public void run() { bad.record("-3", "c", 0L); }
        }, "negative id");
        expectIAE(new Runnable() {
            @Override public void run() { bad.record("1", "c", -1L); }
        }, "negative record tick");
        expectIAE(new Runnable() {
            @Override public void run() { bad.slotsDue(0, 1); }
        }, "zero cap");
        expectIAE(new Runnable() {
            @Override public void run() { bad.slotsDue(4, 0); }
        }, "zero budget");

        // Job: pure budgeted decision over sealed states, one pad per
        // mob in table order, caps included.
        SpawnStore js = new SpawnStore();
        js.record("11", "1,66,2:" + MOB, 100L);
        List<String> jdue = job.decide(seal(js, 110L));
        check(jdue.size() == js.slotsDue(1, (int) wired.cap(BEAST_SHORT),
                (int) wired.budget(BEAST_SHORT))
                + js.slotsDue(0, (int) wired.cap(BRUTE_SHORT),
                        (int) wired.budget(BRUTE_SHORT))
                && jdue.size() == 2
                && jdue.get(0).matches(
                        "[0-9]+,6[678],[0-9]+:example1\\.content:my_beast")
                && jdue.get(1).matches(
                        "[0-9]+,6[678],[0-9]+:example1\\.content:my_brute"),
                "job decides one pad per mob in table order");
        check(job.decide(seal(js, 110L)).equals(jdue), "job pure");
        SpawnStore capped = new SpawnStore();
        capped.record("1", "1,66,2:" + MOB, 0L);
        capped.record("2", "3,67,4:" + MOB, 0L);
        capped.record("3", "5,68,6:" + MOB, 0L);
        capped.record("4", "7,66,8:" + MOB, 0L);
        capped.record("5", "1,66,2:" + BRUTE, 0L);
        capped.record("6", "3,67,4:" + BRUTE, 0L);
        capped.record("7", "5,68,6:" + BRUTE, 0L);
        capped.record("8", "7,66,8:" + BRUTE, 0L);
        check(job.decide(seal(capped, 50L)).isEmpty(),
                "job emits nothing at cap");

        // Job refusals: missing ids fail at the seam, never silently.
        final Snapshot bare = ForgeSnapshot.snapshot(
                0L, new HashMap<MatouId, Object>());
        expectIAE(new Runnable() {
            @Override public void run() { job.decide(bare); }
        }, "missing spawn ids");

        // Seal: contents, copy isolation, refusals (the helper above
        // routes through the shipped SpawnSeal, so every assertion below
        // exercises the live path).
        Map<String, Long> cap44 = new LinkedHashMap<String, Long>();
        cap44.put(MOB, Long.valueOf(4L));
        cap44.put(BRUTE, Long.valueOf(4L));
        Map<String, Long> bud11 = new LinkedHashMap<String, Long>();
        bud11.put(MOB, Long.valueOf(1L));
        bud11.put(BRUTE, Long.valueOf(1L));
        Map<String, List<Long>> band6668 =
                new LinkedHashMap<String, List<Long>>();
        band6668.put(MOB, Arrays.asList(Long.valueOf(66L),
                Long.valueOf(68L)));
        band6668.put(BRUTE, Arrays.asList(Long.valueOf(66L),
                Long.valueOf(68L)));
        Map<MatouId, Object> st = SpawnSeal.seal(vocab(), seeded(),
                Arrays.asList(MOB, BRUTE), cap44, bud11, band6668);
        check(st.get(SpawnJob.CENSUS) instanceof Map,
                "seal carries example1.spawn:census");
        check(Arrays.asList(MOB, BRUTE).equals(st.get(SpawnJob.TABLE)),
                "seal carries example1.spawn:table");
        check(cap44.equals(st.get(SpawnJob.CAP)),
                "seal carries example1.spawn:cap");
        check(bud11.equals(st.get(SpawnJob.BUDGET)),
                "seal carries example1.spawn:budget");
        check(band6668.equals(st.get(SpawnJob.Y)),
                "seal carries example1.spawn:y");
        check(new ArrayList<MatouId>(st.keySet()).equals(Arrays.asList(
                SpawnJob.CENSUS, SpawnJob.TABLE, SpawnJob.CAP,
                SpawnJob.BUDGET, SpawnJob.Y)),
                "seal keys follow the vocabulary order");
        SpawnStore iso = new SpawnStore();
        iso.record("1", "1,66,2:" + MOB, 5L);
        Map<MatouId, Object> snap0 = SpawnSeal.seal(vocab(), iso,
                MOB, 4L, 1L, 66L, 68L);
        iso.record("2", "9,67,9:" + MOB, 6L);
        check(((Map<?, ?>) snap0.get(SpawnJob.CENSUS)).size() == 1,
                "sealed census is a copy, later records never leak");
        final SpawnStore nullStore = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(null, nullStore, MOB, 4L, 1L, 66L, 68L);
            }
        }, "null vocabulary");
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), nullStore, MOB, 4L, 1L, 66L,
                        68L);
            }
        }, "null store");
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), null, 4L, 1L, 66L,
                        68L);
            }
        }, "null mob");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), "my_beast", 4L, 1L,
                        66L, 68L);
            }
        }, "bare mob ref");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), MOB, 0L, 1L, 66L,
                        68L);
            }
        }, "zero cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), MOB, 4L, 0L, 66L,
                        68L);
            }
        }, "zero budget");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), MOB, 4L, 1L, 68L,
                        66L);
            }
        }, "inverted y");
        final StateVocabulary foreign =
                new ExamplePack().vocabulary(LootStates.SCOPE);
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(foreign, seeded(), MOB, 4L, 1L, 66L,
                        68L);
            }
        }, "loot vocabulary into spawn seal");

        // Comparateur: budgeted slots over the census equal the job
        // decision size tick by tick over the same sealed states (same
        // order, same cap boundary), at budgets 1..2 over the wired cap,
        // summed per mob like the forge tripwire.
        for (int budget = 1; budget <= 2; budget++) {
            for (long tick = 95L; tick <= 125L; tick += 10L) {
                int viaStore = 0;
                for (String mob : Arrays.asList(MOB, BRUTE)) {
                    int count = 0;
                    for (String cell
                            : seeded().sealed().values()) {
                        if (cell.endsWith(":" + mob)) {
                            count++;
                        }
                    }
                    viaStore += seeded().slotsDue(count, 4, budget);
                }
                Map<String, Long> budgets =
                        new LinkedHashMap<String, Long>();
                budgets.put(MOB, Long.valueOf((long) budget));
                budgets.put(BRUTE, Long.valueOf((long) budget));
                Map<MatouId, Object> states = SpawnSeal.seal(
                        vocab(), seeded(), Arrays.asList(MOB, BRUTE),
                        cap44, budgets, band6668);
                List<String> viaJob = job.decide(
                        ForgeSnapshot.snapshot(tick, states));
                check(viaJob.size() == viaStore,
                        "store slots == job decision at tick " + tick
                                + " x" + budget);
            }
        }

        // Operator overrides (T2, hub decisions/SPAWN.md): absent means
        // content, present wins uniformly per mob, bad refuses loudly —
        // the same rule the forge wire consumes per mob, exercised here
        // through the shipped OperatorPolicy.
        SpawnTable content = table();
        List<Packs.PackSpec> bareSpecs =
                specs(spec("example1:my_ore"));
        long[] effBeast = OperatorPolicy.effectiveSpawn(
                content.cap(BEAST_SHORT), content.budget(BEAST_SHORT),
                content.yMin(BEAST_SHORT), content.yMax(BEAST_SHORT),
                bareSpecs);
        long[] effBrute = OperatorPolicy.effectiveSpawn(
                content.cap(BRUTE_SHORT), content.budget(BRUTE_SHORT),
                content.yMin(BRUTE_SHORT), content.yMax(BRUTE_SHORT),
                bareSpecs);
        check(effBeast[0] == 4L && effBeast[1] == 1L
                && effBeast[2] == 66L && effBeast[3] == 68L
                && effBrute[0] == 4L && effBrute[1] == 1L
                && effBrute[2] == 66L && effBrute[3] == 68L,
                "operator absent means content policy");
        List<Packs.PackSpec> capSpecs = specs(spec("example1:my_ore",
                "spawn.cap", "2"));
        long[] overBeast = OperatorPolicy.effectiveSpawn(
                content.cap(BEAST_SHORT), content.budget(BEAST_SHORT),
                content.yMin(BEAST_SHORT), content.yMax(BEAST_SHORT),
                capSpecs);
        long[] overBrute = OperatorPolicy.effectiveSpawn(
                content.cap(BRUTE_SHORT), content.budget(BRUTE_SHORT),
                content.yMin(BRUTE_SHORT), content.yMax(BRUTE_SHORT),
                capSpecs);
        check(overBeast[0] == 2L && overBeast[1] == 1L
                && overBeast[2] == 66L && overBeast[3] == 68L
                && overBrute[0] == 2L && overBrute[1] == 1L
                && overBrute[2] == 66L && overBrute[3] == 68L,
                "operator spawn.cap wins uniformly per mob");
        List<Packs.PackSpec> fullSpecs = specs(spec("example1:my_ore",
                "spawn.cap", "2", "spawn.budget", "2", "spawn.y_min",
                "60", "spawn.y_max", "61"));
        long[] fullBeast = OperatorPolicy.effectiveSpawn(
                content.cap(BEAST_SHORT), content.budget(BEAST_SHORT),
                content.yMin(BEAST_SHORT), content.yMax(BEAST_SHORT),
                fullSpecs);
        long[] fullBrute = OperatorPolicy.effectiveSpawn(
                content.cap(BRUTE_SHORT), content.budget(BRUTE_SHORT),
                content.yMin(BRUTE_SHORT), content.yMax(BRUTE_SHORT),
                fullSpecs);
        check(fullBeast[0] == 2L && fullBeast[1] == 2L
                && fullBeast[2] == 60L && fullBeast[3] == 61L
                && fullBrute[0] == 2L && fullBrute[1] == 2L
                && fullBrute[2] == 60L && fullBrute[3] == 61L,
                "operator full policy wins uniformly per mob");
        check(!OperatorPolicy.present(
                specs(spec("example1:my_ore")), OperatorPolicy.SPAWN_CAP),
                "absent key is not present");
        check(OperatorPolicy.present(
                specs(spec("example1:my_ore", "spawn.cap", "2")),
                OperatorPolicy.SPAWN_CAP), "present key is present");
        SpawnStore empty = new SpawnStore();
        Map<String, Long> fullCap = new LinkedHashMap<String, Long>();
        fullCap.put(MOB, Long.valueOf(fullBeast[0]));
        fullCap.put(BRUTE, Long.valueOf(fullBrute[0]));
        Map<String, Long> fullBudget = new LinkedHashMap<String, Long>();
        fullBudget.put(MOB, Long.valueOf(fullBeast[1]));
        fullBudget.put(BRUTE, Long.valueOf(fullBrute[1]));
        Map<String, List<Long>> fullBands =
                new LinkedHashMap<String, List<Long>>();
        fullBands.put(MOB, Arrays.asList(
                Long.valueOf(fullBeast[2]), Long.valueOf(fullBeast[3])));
        fullBands.put(BRUTE, Arrays.asList(
                Long.valueOf(fullBrute[2]), Long.valueOf(fullBrute[3])));
        Map<MatouId, Object> overStates = SpawnSeal.seal(vocab(), empty,
                Arrays.asList(MOB, BRUTE), fullCap, fullBudget,
                fullBands);
        List<String> overDue = job.decide(
                ForgeSnapshot.snapshot(10L, overStates));
        check(overDue.size() == 4
                && overDue.size() == empty.slotsDue(0,
                        (int) fullBeast[0], (int) fullBeast[1])
                        + empty.slotsDue(0, (int) fullBrute[0],
                                (int) fullBrute[1])
                && overDue.get(0).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_beast")
                && overDue.get(1).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_beast")
                && overDue.get(2).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_brute")
                && overDue.get(3).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_brute"),
                "overridden policy seals and decides 2 slots per mob on its band");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.cap", "0")));
            }
        }, "zero operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.cap", "-1")));
            }
        }, "negative operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.cap", "x")));
            }
        }, "non-numeric operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L,
                        specs(spec("example1:my_ore", "spawn.cap", "2"),
                                spec("example1:my_ore",
                                        "spawn.cap", "3")));
            }
        }, "differing operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.cpa", "2")));
            }
        }, "unknown operator key");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.y_min", "70")));
            }
        }, "operator band inverting content");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, specs(spec("example1:my_ore",
                                "spawn.y_min", "68",
                                "spawn.y_max", "66")));
            }
        }, "operator inverted band");
        expectNPE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, null);
            }
        }, "null specs");

        // Per-mob seal (second-beast tranche, hub
        // decisions/VIRTUAL_HITBOXES.md): a single-mob per-mob seal emits
        // states identical to the legacy seal (same decision downstream),
        // and a two-mob seal carries the per-mob shapes (ordered mob
        // list, cap/budget maps, y map) the per-mob SpawnJob decides —
        // one pad per mob in table order.
        final Map<String, Long> soloCap =
                new LinkedHashMap<String, Long>();
        soloCap.put(MOB, Long.valueOf(4L));
        final Map<String, Long> soloBudget =
                new LinkedHashMap<String, Long>();
        soloBudget.put(MOB, Long.valueOf(1L));
        final Map<String, List<Long>> soloY =
                new LinkedHashMap<String, List<Long>>();
        soloY.put(MOB, Arrays.asList(Long.valueOf(66L),
                Long.valueOf(68L)));
        final List<String> soloTable = new ArrayList<String>();
        soloTable.add(MOB);
        final Map<MatouId, Object> soloStates = SpawnSeal.seal(
                vocab(), seeded(), soloTable, soloCap, soloBudget,
                soloY);
        Map<MatouId, Object> legacyStates = SpawnSeal.seal(vocab(),
                seeded(), MOB, 4L, 1L, 66L, 68L);
        check(soloStates.equals(legacyStates),
                "single-mob per-mob seal is identical to the legacy seal");
        List<String> soloDue = job.decide(
                ForgeSnapshot.snapshot(110L, soloStates));
        check(soloDue.size() == 1
                && soloDue.get(0).matches(
                        "[0-9]+,6[678],[0-9]+:example1\\.content:my_beast"),
                "identical states decide the table mob on the sealed band");
        final List<String> duoTable = new ArrayList<String>(
                Arrays.asList(MOB, BRUTE));
        final Map<String, Long> duoCap =
                new LinkedHashMap<String, Long>();
        duoCap.put(MOB, Long.valueOf(4L));
        duoCap.put(BRUTE, Long.valueOf(2L));
        final Map<String, Long> duoBudget =
                new LinkedHashMap<String, Long>();
        duoBudget.put(MOB, Long.valueOf(1L));
        duoBudget.put(BRUTE, Long.valueOf(1L));
        final Map<String, List<Long>> duoY =
                new LinkedHashMap<String, List<Long>>();
        duoY.put(MOB, Arrays.asList(Long.valueOf(66L),
                Long.valueOf(68L)));
        duoY.put(BRUTE, Arrays.asList(Long.valueOf(60L),
                Long.valueOf(61L)));
        final Map<MatouId, Object> duo = SpawnSeal.seal(vocab(),
                seeded(), duoTable, duoCap, duoBudget, duoY);
        check(duoTable.equals(duo.get(SpawnJob.TABLE)),
                "two-mob seal carries the ordered mob list");
        check(duoCap.equals(duo.get(SpawnJob.CAP)),
                "two-mob seal carries the per-mob cap map");
        check(duoBudget.equals(duo.get(SpawnJob.BUDGET)),
                "two-mob seal carries the per-mob budget map");
        check(duoY.equals(duo.get(SpawnJob.Y)),
                "two-mob seal carries the per-mob y map");
        check(new ArrayList<MatouId>(duo.keySet()).equals(Arrays.asList(
                SpawnJob.CENSUS, SpawnJob.TABLE, SpawnJob.CAP,
                SpawnJob.BUDGET, SpawnJob.Y)),
                "per-mob seal keys follow the vocabulary order");
        final Snapshot duoSnap = ForgeSnapshot.snapshot(10L, duo);
        List<String> duoDue = job.decide(duoSnap);
        check(duoDue.size() == 2
                && duoDue.get(0).matches(
                        "[0-9]+,6[678],[0-9]+:example1\\.content:my_beast")
                && duoDue.get(1).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_brute"),
                "two-mob seal decides one pad per mob in table order");

        // Per-mob seal refusals: every unfunded or overfunded entry
        // fails at the seam, never silently.
        final List<String> nullTable = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), nullTable, duoCap,
                        duoBudget, duoY);
            }
        }, "null per-mob table");
        final List<String> emptyTable = new ArrayList<String>();
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), emptyTable, duoCap,
                        duoBudget, duoY);
            }
        }, "empty per-mob table");
        final List<String> nullEntry = new ArrayList<String>(
                Arrays.asList(MOB, null));
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), nullEntry, duoCap,
                        duoBudget, duoY);
            }
        }, "null per-mob entry");
        final List<String> bareEntry = new ArrayList<String>();
        bareEntry.add("my_beast");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), bareEntry, soloCap,
                        soloBudget, soloY);
            }
        }, "bare per-mob entry");
        final List<String> dupeTable = new ArrayList<String>(
                Arrays.asList(MOB, MOB));
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), dupeTable, soloCap,
                        soloBudget, soloY);
            }
        }, "duplicated per-mob entry");
        final Map<String, Long> nullCap = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, nullCap,
                        soloBudget, soloY);
            }
        }, "null per-mob cap");
        final Map<String, Long> noCap = new LinkedHashMap<String, Long>();
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, noCap,
                        soloBudget, soloY);
            }
        }, "missing per-mob cap");
        final Map<String, Long> extraCap =
                new LinkedHashMap<String, Long>(duoCap);
        extraCap.put("example1.content:nope", Long.valueOf(1L));
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), duoTable, extraCap,
                        duoBudget, duoY);
            }
        }, "overfunded per-mob cap");
        final Map<String, Long> zeroCap =
                new LinkedHashMap<String, Long>();
        zeroCap.put(MOB, Long.valueOf(0L));
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, zeroCap,
                        soloBudget, soloY);
            }
        }, "zero per-mob cap");
        final Map<String, Object> strCap =
                new LinkedHashMap<String, Object>();
        strCap.put(MOB, "4");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, strCap,
                        soloBudget, soloY);
            }
        }, "non-numeric per-mob cap");
        final Map<String, List<Long>> shortY =
                new LinkedHashMap<String, List<Long>>();
        shortY.put(MOB, Arrays.asList(Long.valueOf(66L)));
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, soloCap,
                        soloBudget, shortY);
            }
        }, "short per-mob y");
        final Map<String, List<Long>> flippedY =
                new LinkedHashMap<String, List<Long>>();
        flippedY.put(MOB, Arrays.asList(Long.valueOf(68L),
                Long.valueOf(66L)));
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(vocab(), seeded(), soloTable, soloCap,
                        soloBudget, flippedY);
            }
        }, "inverted per-mob y");

        // Count-scoped budgeted slots: one room per sealed mob sums to
        // the whole-census room while every entry carries a sealed mob.
        check(seeded().slotsDue(2, 4, 1) == 1,
                "count-scoped slots match the whole census");
        check(seeded().slotsDue(4, 4, 1) == 0,
                "count-scoped slots honour the cap");
        expectIAE(new Runnable() {
            @Override public void run() { bad.slotsDue(-1, 4, 1); }
        }, "negative scoped count");
        expectIAE(new Runnable() {
            @Override public void run() { bad.slotsDue(0, 0, 1); }
        }, "zero scoped cap");

        // Per-mob operator keys stay refused (named follow-up — global
        // overrides apply uniformly per mob until that tranche lands).
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L,
                        specs(spec("example1:my_ore",
                                "spawn.cap.my_beast", "2")));
            }
        }, "per-mob operator key");
        System.out.println("ok spike-spawn : all");
    }
}
