package fr.iamacat.bridge.spike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repop spike: bridge-owned mined-cell store (plain data, zero Minecraft).
 *
 * <p>The forge side records break events here; per tick the bridge seals
 * {@link #sealed} into the snapshot states and the pure
 * {@link RepopJob} decides what is due. Claimed cells leave the store —
 * a re-mine re-records them. Insertion order throughout, so live and
 * verdict replay agree. Java 8, zero deps.
 */
public final class MinedStore {
    private final LinkedHashMap<String, Long> mined =
            new LinkedHashMap<String, Long>();

    /**
     * Record a mined volume cell ({@code "x,y,z:ns:block"}) at a tick.
     * Re-mining overwrites the previous tick (latest break wins).
     */
    public void record(String cell, long tick) {
        if (cell == null) {
            throw new NullPointerException("E_SPIKE_MINED:null cell");
        }
        if (tick < 0) {
            throw new IllegalArgumentException(
                    "E_SPIKE_MINED:range <" + tick + "> (want >= 0)");
        }
        mined.put(cell, tick);
    }

    /**
     * Claim cells due for repop at {@code nowTick}: every cell with
     * {@code minedTick + delay <= nowTick}, in record order. Claimed
     * cells leave the store. Mirrors {@link RepopJob} over
     * {@link #sealed} — the gate holds both paths equal.
     */
    public List<String> claimDue(long nowTick, long delay) {
        if (nowTick < 0) {
            throw new IllegalArgumentException(
                    "E_SPIKE_MINED:range <" + nowTick + "> (want >= 0)");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    "E_SPIKE_MINED:range <" + delay + "> (want >= 0)");
        }
        List<String> due = new ArrayList<String>();
        for (Map.Entry<String, Long> e : mined.entrySet()) {
            if (e.getValue() + delay <= nowTick) {
                due.add(e.getKey());
            }
        }
        for (String cell : due) {
            mined.remove(cell);
        }
        return due;
    }

    /** Seal view for snapshot states (cell to mined tick). A copy, never live. */
    public Map<String, Long> sealed() {
        return new LinkedHashMap<String, Long>(mined);
    }

    public int size() {
        return mined.size();
    }
}
