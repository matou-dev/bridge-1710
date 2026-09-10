package fr.iamacat.bridge.spawn;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.example1.SpawnJob;
import fr.iamacat.example1.SpawnTable;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.util.Arrays;
import java.util.HashMap;
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

    private static String table() {
        return SpawnTable.fromFile(
                "../example1/content/owned.matou").mob();
    }

    private static Snapshot seal(SpawnStore store, long tick) {
        Map<MatouId, Object> states =
                SpawnSeal.seal(store, table(), 4L, 1L, 66L, 68L);
        return ForgeSnapshot.snapshot(tick, states);
    }

    private static SpawnStore seeded() {
        SpawnStore s = new SpawnStore();
        s.record("11", "1,66,2:" + MOB, 100L);
        s.record("23", "3,67,4:" + MOB, 110L);
        return s;
    }

    public static void main(String[] args) {
        SpawnJob job = new SpawnJob();

        // Table wires from the sibling content (single mob funds spawn).
        check(MOB.equals(table()), "table wires beast");

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
        check(jdue.size() == js.slotsDue(4, 1),
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
        Map<MatouId, Object> st = SpawnSeal.seal(seeded(), MOB,
                4L, 1L, 66L, 68L);
        check(st.get(SpawnJob.CENSUS) instanceof Map,
                "seal carries example1.spawn:census");
        check(MOB.equals(st.get(SpawnJob.TABLE)),
                "seal carries example1.spawn:table");
        check(Long.valueOf(4L).equals(st.get(SpawnJob.CAP)),
                "seal carries example1.spawn:cap");
        check(Long.valueOf(1L).equals(st.get(SpawnJob.BUDGET)),
                "seal carries example1.spawn:budget");
        check(Arrays.asList(Long.valueOf(66L), Long.valueOf(68L)).equals(
                st.get(SpawnJob.Y)), "seal carries example1.spawn:y");
        SpawnStore iso = new SpawnStore();
        iso.record("1", "1,66,2:" + MOB, 5L);
        Map<MatouId, Object> snap0 = SpawnSeal.seal(iso, MOB,
                4L, 1L, 66L, 68L);
        iso.record("2", "9,67,9:" + MOB, 6L);
        check(((Map<?, ?>) snap0.get(SpawnJob.CENSUS)).size() == 1,
                "sealed census is a copy, later records never leak");
        final SpawnStore nullStore = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(nullStore, MOB, 4L, 1L, 66L, 68L);
            }
        }, "null store");
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(seeded(), null, 4L, 1L, 66L, 68L);
            }
        }, "null mob");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(seeded(), "my_beast", 4L, 1L, 66L, 68L);
            }
        }, "bare mob ref");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(seeded(), MOB, 0L, 1L, 66L, 68L);
            }
        }, "zero cap");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(seeded(), MOB, 4L, 0L, 66L, 68L);
            }
        }, "zero budget");
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnSeal.seal(seeded(), MOB, 4L, 1L, 68L, 66L);
            }
        }, "inverted y");

        // Comparateur: budgeted slots over the census equal the job
        // decision size tick by tick over the same sealed states (same
        // order, same cap boundary), at budgets 1..2.
        for (int budget = 1; budget <= 2; budget++) {
            for (long tick = 95L; tick <= 125L; tick += 10L) {
                int viaStore = seeded().slotsDue(4, budget);
                Map<MatouId, Object> states = SpawnSeal.seal(
                        seeded(), MOB, 4L, budget, 66L, 68L);
                List<String> viaJob = job.decide(
                        ForgeSnapshot.snapshot(tick, states));
                check(viaJob.size() == viaStore,
                        "store slots == job decision at tick " + tick
                                + " x" + budget);
            }
        }
        System.out.println("ok spike-spawn : all");
    }
}
