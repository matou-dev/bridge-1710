package fr.iamacat.bridge.spawn;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawn tranche: bridge-owned living census (plain data, zero Minecraft).
 *
 * <p>The forge side records every dim-0 host join here (entity id to
 * spawn cell) — own landings and vanilla joins alike, the join veto only
 * refusing past-cap joins — and releases it when the beast dies; per tick
 * the bridge seals {@link #sealed} beside the pack states and the pure
 * {@code SpawnJob} decides the budgeted spawns. Recording every join the
 * veto lets through is what keeps the census equal to the living reality
 * (measured live: a vanilla join below cap bypassed a landing-only census
 * and breached it). Census entries are never consumed by spawning — only
 * death releases them — so {@link #slotsDue} is a pure count over the
 * census size, and the gate holds it equal to
 * the job decision size. Insertion order throughout, so live and verdict
 * replay agree. Java 8, zero deps.
 */
public final class SpawnStore {
    private final LinkedHashMap<String, String> census =
            new LinkedHashMap<String, String>();

    /**
     * Record a living beast (entity id to spawn cell
     * {@code "x,y,z:mob"}) at a tick. Re-recording overwrites the
     * previous cell (latest landing wins).
     */
    public void record(String entityId, String cell, long tick) {
        if (entityId == null) {
            throw new NullPointerException("E_SPAWN_STORE:null id");
        }
        if (cell == null) {
            throw new NullPointerException("E_SPAWN_STORE:null cell");
        }
        try {
            if (Integer.parseInt(entityId) < 0) {
                throw new NumberFormatException("negative");
            }
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException(
                    "E_SPAWN_STORE:type <" + entityId
                            + "> (want entity-id string)");
        }
        if (tick < 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_STORE:range <" + tick + "> (want >= 0)");
        }
        census.put(entityId, cell);
    }

    /**
     * Release a dead beast. Returns true when an entry left the census.
     * Unknown ids are not ours (a vanilla beast, or the loot proof's own
     * pig, dies without ever being recorded) — false, never a refusal:
     * refusing here would fail ticks that never spawned.
     */
    public boolean release(String entityId) {
        if (entityId == null) {
            throw new NullPointerException("E_SPAWN_STORE:null id");
        }
        return census.remove(entityId) != null;
    }

    /**
     * Budgeted spawn slots for a tick: {@code min(budget, max(cap -
     * census, 0))} — a full census means no spawn, never a negative one.
     * Mirrors the pure spawn decision size over {@link #sealed} — the
     * gate holds both paths equal.
     */
    public int slotsDue(int cap, int budget) {
        return slotsDue(census.size(), cap, budget);
    }

    /**
     * Count-scoped budgeted slots (per-mob tranche, hub
     * {@code decisions/VIRTUAL_HITBOXES.md}): the same budget math over
     * an explicit living count instead of the whole census, so the forge
     * side can sum one room per sealed mob. The two-arg view counts the
     * whole census (sole-mob seals stay identical).
     */
    public int slotsDue(int count, int cap, int budget) {
        if (count < 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_STORE:range <" + count + "> (want >= 0)");
        }
        if (cap <= 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_STORE:range <" + cap + "> (want cap > 0)");
        }
        if (budget <= 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_STORE:range <" + budget
                            + "> (want budget > 0)");
        }
        return Math.min(budget, Math.max(cap - count, 0));
    }

    /** Seal view for snapshot states (entity id to spawn cell). A copy. */
    public Map<String, String> sealed() {
        return new LinkedHashMap<String, String>(census);
    }

    public int size() {
        return census.size();
    }
}
