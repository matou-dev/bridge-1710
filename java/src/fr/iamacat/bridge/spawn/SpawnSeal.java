package fr.iamacat.bridge.spawn;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawn tranche: per-tick seal of bridge-owned census state plus the wired
 * spawn table into snapshot states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the merge with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory — and runs the pure
 * {@code SpawnJob} over it. Neither the store nor the range is ever
 * shared: only sealed copies cross the seam, so a later record can never
 * rewrite a decision already taken. State ids resolve through the
 * pack-served {@link StateVocabulary} (T3 registry, hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): the seal shares the job's
 * ids with no bridge-to-content compile edge. Java 8, zero deps beyond
 * matou-spi.
 */
public final class SpawnSeal {
    private SpawnSeal() {}

    /**
     * Seal view for one tick: census (entity id to spawn cell) plus table
     * (content mob ref) plus cap plus budget plus y ({@code [yMin, yMax]}
     * longs) — roles resolved through {@code vocab} in seal order, so
     * live and verdict replay agree. The forge side serves the pack's
     * vocabulary at wire time (parse-once, never on the tick path).
     *
     * @throws NullPointerException when vocab, store or mob is null.
     * @throws IllegalArgumentException when mob is bare, cap or budget
     *         is not positive, the range is unordered, or the vocabulary
     *         carries no spawn roles.
     */
    public static Map<MatouId, Object> seal(StateVocabulary vocab,
            SpawnStore store, String mob,
            long cap, long budget, long yMin, long yMax) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_SPAWN_SEAL:null vocabulary");
        }
        if (store == null) {
            throw new NullPointerException("E_SPAWN_SEAL:null store");
        }
        if (mob == null) {
            throw new NullPointerException("E_SPAWN_SEAL:null mob");
        }
        if (mob.isEmpty() || mob.indexOf(':') < 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_SEAL:type <" + mob
                            + "> (want \"ns:mob\" content ref)");
        }
        if (cap <= 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_SEAL:range <" + cap + "> (want cap > 0)");
        }
        if (budget <= 0) {
            throw new IllegalArgumentException(
                    "E_SPAWN_SEAL:range <" + budget
                            + "> (want budget > 0)");
        }
        if (yMin < 0 || yMax < yMin) {
            throw new IllegalArgumentException(
                    "E_SPAWN_SEAL:range <[" + yMin + "," + yMax
                            + "]> (want 0 <= yMin <= yMax)");
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(SpawnStates.census(vocab), store.sealed());
        states.put(SpawnStates.table(vocab), mob);
        states.put(SpawnStates.cap(vocab), Long.valueOf(cap));
        states.put(SpawnStates.budget(vocab), Long.valueOf(budget));
        states.put(SpawnStates.y(vocab), Arrays.asList(
                Long.valueOf(yMin), Long.valueOf(yMax)));
        return states;
    }
}
