package fr.iamacat.bridge.loot;

import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.StateVocabulary;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loot tranche: per-tick seal of bridge-owned harvest state plus the wired
 * loot table into snapshot states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the merge with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory — and runs the pure
 * {@code LootJob} over it. Neither the store nor the table is ever
 * shared: only sealed copies cross the seam, so a later record can never
 * rewrite a decision already taken. State ids resolve through the
 * pack-served {@link StateVocabulary} (T3 registry, hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): the seal shares the job's
 * ids with no bridge-to-content compile edge. Java 8, zero deps beyond
 * matou-spi.
 */
public final class LootSeal {
    private LootSeal() {}

    /**
     * Seal view for one tick: harvests (harvest to harvest tick) plus
     * table (kind to content item ref) plus count (items per harvest) —
     * roles resolved through {@code vocab} in seal order, so live and
     * verdict replay agree. The forge side serves the pack's vocabulary
     * at wire time (parse-once, never on the tick path).
     *
     * @throws NullPointerException when vocab, store or table is null.
     * @throws IllegalArgumentException when count is not positive or the
     *         vocabulary carries no loot roles.
     */
    public static Map<MatouId, Object> seal(StateVocabulary vocab,
            DropStore store, Map<String, String> table, long count) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_LOOT_SEAL:null vocabulary");
        }
        if (store == null) {
            throw new NullPointerException("E_LOOT_SEAL:null store");
        }
        if (table == null) {
            throw new NullPointerException("E_LOOT_SEAL:null table");
        }
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "E_LOOT_SEAL:range <" + count + "> (want > 0)");
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(LootStates.harvested(vocab), store.sealed());
        states.put(LootStates.table(vocab), Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(table)));
        states.put(LootStates.count(vocab), Long.valueOf(count));
        return states;
    }
}
