package fr.iamacat.bridge.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot tranche: bridge-owned harvest store (plain data, zero Minecraft).
 *
 * <p>The forge side records harvest events here (ore harvests, mob kills);
 * per tick the bridge seals {@link #sealed} beside the pack states and the
 * pure {@code LootJob} decides what drops. Claimed harvests leave the
 * store — a re-harvest re-records them. Drops are immediate (no repop
 * delay): a harvest sealed at tick T is due at tick T. Insertion order
 * throughout, so live and verdict replay agree. Java 8, zero deps.
 */
public final class DropStore {
    private final LinkedHashMap<String, Long> harvested =
            new LinkedHashMap<String, Long>();

    /**
     * Record a harvest ({@code "x,y,z:kind"}) at a tick. Re-harvesting
     * overwrites the previous tick (latest harvest wins).
     */
    public void record(String harvest, long tick) {
        if (harvest == null) {
            throw new NullPointerException("E_LOOT_STORE:null harvest");
        }
        if (tick < 0) {
            throw new IllegalArgumentException(
                    "E_LOOT_STORE:range <" + tick + "> (want >= 0)");
        }
        harvested.put(harvest, tick);
    }

    /**
     * Claim harvests due at {@code nowTick}: every harvest with
     * {@code harvestTick <= nowTick}, in record order. Claimed harvests
     * leave the store. Mirrors the pure loot decision over {@link #sealed}
     * up to the table expansion — the gate holds both paths equal.
     */
    public List<String> claimDue(long nowTick) {
        if (nowTick < 0) {
            throw new IllegalArgumentException(
                    "E_LOOT_STORE:range <" + nowTick + "> (want >= 0)");
        }
        List<String> due = new ArrayList<String>();
        for (Map.Entry<String, Long> e : harvested.entrySet()) {
            if (e.getValue() <= nowTick) {
                due.add(e.getKey());
            }
        }
        for (String harvest : due) {
            harvested.remove(harvest);
        }
        return due;
    }

    /** Seal view for snapshot states (harvest to harvest tick). A copy. */
    public Map<String, Long> sealed() {
        return new LinkedHashMap<String, Long>(harvested);
    }

    public int size() {
        return harvested.size();
    }
}
