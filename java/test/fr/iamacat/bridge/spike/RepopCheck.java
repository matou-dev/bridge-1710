package fr.iamacat.bridge.spike;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repop spike gate (no JUnit): mined-store semantics plus pure repop
 * decisions plus the store-vs-job comparateur (both paths over the same
 * sealed states stay equal). Any violation prints
 * {@code FAIL spike-repop : ...} and exits 1. Run by tools/check.sh
 * etage 1. Zero Minecraft.
 */
public final class RepopCheck {
    private RepopCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL spike-repop : " + what);
            System.exit(1);
        }
        System.out.println("ok spike-repop : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok spike-repop : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-repop : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok spike-repop : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spike-repop : accepted " + what);
        System.exit(1);
    }

    private static Snapshot seal(MinedStore store, long tick, long delay) {
        return ForgeSnapshot.snapshot(tick, RepopSeal.seal(store, delay));
    }

    private static MinedStore seeded() {
        MinedStore s = new MinedStore();
        s.record("10,64,5:minecraft:stone", 100L);
        s.record("11,64,5:minecraft:stone", 110L);
        s.record("12,64,5:minecraft:stone", 130L);
        return s;
    }

    public static void main(String[] args) {
        RepopJob job = new RepopJob();

        // Store: record then claim on both sides of the boundary.
        MinedStore store = new MinedStore();
        store.record("10,64,5:minecraft:stone", 100L);
        store.record("11,64,5:minecraft:stone", 110L);
        check(store.size() == 2, "recorded 2 mined cells");
        check(store.claimDue(109L, 10L).isEmpty(), "nothing due before delay");
        List<String> due = store.claimDue(110L, 10L);
        check(due.size() == 1
                && due.get(0).equals("10,64,5:minecraft:stone"),
                "boundary cell due at minedTick + delay == now");
        check(store.size() == 1, "claimed cells leave the store");
        store.record("11,64,5:minecraft:stone", 200L);
        check(store.claimDue(300L, 10L).size() == 1,
                "re-mine re-records with the latest tick");

        // Store refusals.
        final MinedStore bad = new MinedStore();
        expectNPE(new Runnable() {
            @Override public void run() { bad.record(null, 0L); }
        }, "null cell");
        expectIAE(new Runnable() {
            @Override public void run() { bad.record("x", -1L); }
        }, "negative record tick");
        expectIAE(new Runnable() {
            @Override public void run() { bad.claimDue(-1L, 0L); }
        }, "negative claim tick");
        expectIAE(new Runnable() {
            @Override public void run() { bad.claimDue(0L, -1L); }
        }, "negative delay");

        // Job: pure decision over sealed states, boundary included.
        MinedStore js = new MinedStore();
        js.record("10,64,5:minecraft:stone", 100L);
        js.record("11,64,5:minecraft:stone", 110L);
        List<String> jdue = job.decide(seal(js, 110L, 10L));
        check(jdue.size() == 1
                && jdue.get(0).equals("10,64,5:minecraft:stone"),
                "job emits the boundary cell only");
        check(job.decide(seal(js, 109L, 10L)).isEmpty(),
                "job emits nothing before delay");
        check(job.decide(seal(js, 500L, 10L)).size() == 2,
                "job emits every due cell late");

        // Job refusals: missing ids, wrong types, negative delay.
        final Snapshot bare = ForgeSnapshot.snapshot(
                0L, new HashMap<MatouId, Object>());
        expectIAE(new Runnable() {
            @Override public void run() { job.decide(bare); }
        }, "missing spike ids");
        Map<MatouId, Object> mistyped = new HashMap<MatouId, Object>();
        mistyped.put(RepopJob.MINED, "not-a-map");
        mistyped.put(RepopJob.DELAY, 10L);
        final Snapshot badMap = ForgeSnapshot.snapshot(0L, mistyped);
        expectIAE(new Runnable() {
            @Override public void run() { job.decide(badMap); }
        }, "non-map mined");
        Map<MatouId, Object> negdelay = new HashMap<MatouId, Object>();
        negdelay.put(RepopJob.MINED, new HashMap<String, Long>());
        negdelay.put(RepopJob.DELAY, -5L);
        final Snapshot badDelay = ForgeSnapshot.snapshot(0L, negdelay);
        expectIAE(new Runnable() {
            @Override public void run() { job.decide(badDelay); }
        }, "negative delay");
        expectNPE(new Runnable() {
            @Override public void run() { job.decide(null); }
        }, "null snapshot");

        // Seal: contents, copy isolation, refusals (the helper above
        // routes through the shipped RepopSeal, so every assertion below
        // exercises the live path).
        Map<MatouId, Object> st = RepopSeal.seal(seeded(), 10L);
        check(st.get(RepopJob.MINED) instanceof Map,
                "seal carries spike:mined");
        check(Long.valueOf(10L).equals(st.get(RepopJob.DELAY)),
                "seal carries spike:delay");
        check(((Map<?, ?>) st.get(RepopJob.MINED)).size() == 3,
                "seal carries every mined cell");
        MinedStore iso = new MinedStore();
        iso.record("1,2,3:minecraft:stone", 5L);
        Map<MatouId, Object> snap0 = RepopSeal.seal(iso, 7L);
        iso.record("9,9,9:minecraft:stone", 6L);
        check(((Map<?, ?>) snap0.get(RepopJob.MINED)).size() == 1,
                "sealed mined is a copy, later records never leak");
        final MinedStore nullStore = null;
        expectNPE(new Runnable() {
            @Override public void run() { RepopSeal.seal(nullStore, 0L); }
        }, "null store");
        expectIAE(new Runnable() {
            @Override public void run() { RepopSeal.seal(seeded(), -1L); }
        }, "negative seal delay");

        // Comparateur: store claim and job decision are equal tick by
        // tick over the same sealed states (same order, same boundary).
        for (long tick = 95L; tick <= 145L; tick += 5L) {
            List<String> viaStore = seeded().claimDue(tick, 10L);
            List<String> viaJob = job.decide(seal(seeded(), tick, 10L));
            check(viaStore.equals(viaJob),
                    "store claim == job decision at tick " + tick);
        }
        System.out.println("ok spike-repop : all");
    }
}
