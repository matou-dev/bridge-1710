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
        Map<MatouId, Object> states =
                SpawnSeal.seal(vocab(), store, wired.mob(), wired.cap(),
                        wired.budget(), wired.yMin(), wired.yMax());
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

        // Table wires from the sibling content (single mob funds spawn,
        // policy included — the gate proves content-decided numbers ride
        // the seal, never bridge constants).
        SpawnTable wired = table();
        check(MOB.equals(wired.mob()), "table wires beast");
        check(wired.cap() == 4L, "table wires authorial cap");
        check(wired.budget() == 1L, "table wires authorial budget");
        check(wired.yMin() == 66L && wired.yMax() == 68L,
                "table wires authorial y band");

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

        // Job: pure budgeted decision over sealed states, cap included.
        SpawnStore js = new SpawnStore();
        js.record("11", "1,66,2:" + MOB, 100L);
        List<String> jdue = job.decide(seal(js, 110L));
        check(jdue.size() == js.slotsDue((int) wired.cap(),
                (int) wired.budget()),
                "job decides the budgeted slots");
        check(jdue.size() == 1
                && jdue.get(0).matches(
                        "[0-9]+,6[678],[0-9]+:example1\\.content:my_beast"),
                "job spawns the table mob on the sealed band");
        check(job.decide(seal(js, 110L)).equals(jdue), "job pure");
        SpawnStore capped = new SpawnStore();
        capped.record("1", "1,66,2:" + MOB, 0L);
        capped.record("2", "3,67,4:" + MOB, 0L);
        capped.record("3", "5,68,6:" + MOB, 0L);
        capped.record("4", "7,66,8:" + MOB, 0L);
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
        Map<MatouId, Object> st = SpawnSeal.seal(vocab(), seeded(),
                MOB, wired.cap(), wired.budget(), wired.yMin(),
                wired.yMax());
        check(st.get(SpawnJob.CENSUS) instanceof Map,
                "seal carries example1.spawn:census");
        check(MOB.equals(st.get(SpawnJob.TABLE)),
                "seal carries example1.spawn:table");
        check(Long.valueOf(wired.cap()).equals(st.get(SpawnJob.CAP)),
                "seal carries example1.spawn:cap");
        check(Long.valueOf(wired.budget()).equals(st.get(SpawnJob.BUDGET)),
                "seal carries example1.spawn:budget");
        check(Arrays.asList(Long.valueOf(wired.yMin()),
                Long.valueOf(wired.yMax())).equals(
                st.get(SpawnJob.Y)), "seal carries example1.spawn:y");
        check(new ArrayList<MatouId>(st.keySet()).equals(Arrays.asList(
                SpawnJob.CENSUS, SpawnJob.TABLE, SpawnJob.CAP,
                SpawnJob.BUDGET, SpawnJob.Y)),
                "seal keys follow the vocabulary order");
        SpawnStore iso = new SpawnStore();
        iso.record("1", "1,66,2:" + MOB, 5L);
        Map<MatouId, Object> snap0 = SpawnSeal.seal(vocab(), iso,
                MOB, wired.cap(), wired.budget(), wired.yMin(),
                wired.yMax());
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
        // order, same cap boundary), at budgets 1..2 over the wired cap.
        for (int budget = 1; budget <= 2; budget++) {
            for (long tick = 95L; tick <= 125L; tick += 10L) {
                int viaStore = seeded().slotsDue(
                        (int) wired.cap(), budget);
                Map<MatouId, Object> states = SpawnSeal.seal(
                        vocab(), seeded(), MOB, wired.cap(), budget,
                        wired.yMin(), wired.yMax());
                List<String> viaJob = job.decide(
                        ForgeSnapshot.snapshot(tick, states));
                check(viaJob.size() == viaStore,
                        "store slots == job decision at tick " + tick
                                + " x" + budget);
            }
        }

        // Operator overrides (T2, hub decisions/SPAWN.md): absent means
        // content, present wins, bad refuses loudly — the same rule the
        // forge wire consumes, exercised here through the shipped
        // OperatorPolicy.
        SpawnTable content = table();
        long[] eff = OperatorPolicy.effectiveSpawn(content.cap(),
                content.budget(), content.yMin(), content.yMax(),
                specs(spec("example1:my_ore")));
        check(eff[0] == 4L && eff[1] == 1L && eff[2] == 66L
                && eff[3] == 68L, "operator absent means content policy");
        long[] over = OperatorPolicy.effectiveSpawn(content.cap(),
                content.budget(), content.yMin(), content.yMax(),
                specs(spec("example1:my_ore", "spawn.cap", "2")));
        check(over[0] == 2L && over[1] == 1L && over[2] == 66L
                && over[3] == 68L,
                "operator spawn.cap wins over content");
        long[] full = OperatorPolicy.effectiveSpawn(content.cap(),
                content.budget(), content.yMin(), content.yMax(),
                specs(spec("example1:my_ore", "spawn.cap", "2",
                        "spawn.budget", "2", "spawn.y_min", "60",
                        "spawn.y_max", "61")));
        check(full[0] == 2L && full[1] == 2L && full[2] == 60L
                && full[3] == 61L, "operator full policy wins over content");
        check(!OperatorPolicy.present(
                specs(spec("example1:my_ore")), OperatorPolicy.SPAWN_CAP),
                "absent key is not present");
        check(OperatorPolicy.present(
                specs(spec("example1:my_ore", "spawn.cap", "2")),
                OperatorPolicy.SPAWN_CAP), "present key is present");
        SpawnStore empty = new SpawnStore();
        Map<MatouId, Object> overStates = SpawnSeal.seal(vocab(), empty,
                MOB, full[0], full[1], full[2], full[3]);
        List<String> overDue = job.decide(
                ForgeSnapshot.snapshot(10L, overStates));
        check(overDue.size() == 2 && overDue.size() == empty.slotsDue(
                (int) full[0], (int) full[1])
                && overDue.get(0).matches(
                        "[0-9]+,6[01],[0-9]+:example1\\.content:my_beast"),
                "overridden policy seals and decides 2 slots on its band");
        final SpawnTable contentCap = content;
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.cap", "0")));
            }
        }, "zero operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.cap", "-1")));
            }
        }, "negative operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.cap", "x")));
            }
        }, "non-numeric operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(),
                        specs(spec("example1:my_ore", "spawn.cap", "2"),
                                spec("example1:my_ore",
                                        "spawn.cap", "3")));
            }
        }, "differing operator cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.cpa", "2")));
            }
        }, "unknown operator key");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.y_min", "70")));
            }
        }, "operator band inverting content");
        expectIAE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(contentCap.cap(),
                        contentCap.budget(), contentCap.yMin(),
                        contentCap.yMax(), specs(spec("example1:my_ore",
                                "spawn.y_min", "68",
                                "spawn.y_max", "66")));
            }
        }, "operator inverted band");
        expectNPE(new Runnable() {
            @Override public void run() {
                OperatorPolicy.effectiveSpawn(4L, 1L, 66L, 68L, null);
            }
        }, "null specs");
        System.out.println("ok spike-spawn : all");
    }
}
