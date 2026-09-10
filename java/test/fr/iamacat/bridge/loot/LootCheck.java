package fr.iamacat.bridge.loot;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.example1.LootJob;
import fr.iamacat.example1.LootTable;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
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

    private static Snapshot seal(DropStore store, long tick) {
        Map<MatouId, Object> states =
                LootSeal.seal(store, table(), 1L);
        return ForgeSnapshot.snapshot(tick, states);
    }

    private static DropStore seeded() {
        DropStore s = new DropStore();
        s.record("8,10,8:" + LootJob.ORE, 100L);
        s.record("12,10,8:" + LootJob.BEAST, 110L);
        return s;
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

        // Table wires from the sibling content (single mob funds ore+beast).
        Map<String, String> wires = table();
        check(wires.size() == 2, "table wires 2 kinds");
        check("example1.content:my_gem".equals(wires.get(LootJob.ORE)),
                "table ore pays gem");
        check("example1.content:my_gem".equals(wires.get(LootJob.BEAST)),
                "table beast pays gem");

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
        Map<MatouId, Object> st = LootSeal.seal(seeded(), wires, 1L);
        check(st.get(LootJob.HARVESTED) instanceof Map,
                "seal carries example1.loot:harvested");
        check(wires.equals(st.get(LootJob.TABLE)),
                "seal carries example1.loot:table");
        check(Long.valueOf(1L).equals(st.get(LootJob.COUNT)),
                "seal carries example1.loot:count");
        DropStore iso = new DropStore();
        iso.record("1,2,3:" + LootJob.ORE, 5L);
        Map<MatouId, Object> snap0 = LootSeal.seal(iso, wires, 1L);
        iso.record("9,9,9:" + LootJob.BEAST, 6L);
        check(((Map<?, ?>) snap0.get(LootJob.HARVESTED)).size() == 1,
                "sealed harvests are a copy, later records never leak");
        Map<String, String> mut = new LinkedHashMap<String, String>(wires);
        Map<MatouId, Object> snapT = LootSeal.seal(seeded(), mut, 1L);
        mut.put(LootJob.ORE, "example1.content:nope");
        check(wires.equals(snapT.get(LootJob.TABLE)),
                "sealed table is a copy, later edits never leak");
        final DropStore nullStore = null;
        expectNPE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(nullStore, table(), 1L);
            }
        }, "null store");
        expectNPE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(seeded(), null, 1L);
            }
        }, "null table");
        expectIAE(new Runnable() {
            @Override public void run() {
                LootSeal.seal(seeded(), table(), 0L);
            }
        }, "zero count");

        // Comparateur: store claim expanded by the table equals the job
        // decision tick by tick over the same sealed states (same order,
        // same boundary), at count 1 and 2.
        for (long count = 1L; count <= 2L; count++) {
            for (long tick = 95L; tick <= 115L; tick += 5L) {
                List<String> viaStore = seeded().claimDue(tick);
                Map<MatouId, Object> states =
                        LootSeal.seal(seeded(), wires, count);
                List<String> viaJob = job.decide(
                        ForgeSnapshot.snapshot(tick, states));
                check(viaJob.equals(expand(viaStore, wires, count)),
                        "store claim == job decision at tick " + tick
                                + " x" + count);
            }
        }
        System.out.println("ok spike-loot : all");
    }
}
