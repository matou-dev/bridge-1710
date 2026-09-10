package fr.iamacat.bridge.loot;

import fr.iamacat.example1.LootJob;
import fr.iamacat.spi.MatouId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loot tranche: per-tick seal of bridge-owned harvest state plus the wired
 * loot table into snapshot states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the merge with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory, SPI untouched — and runs
 * the pure {@code LootJob} over it. Neither the store nor the table is
 * ever shared: only sealed copies cross the seam, so a later record can
 * never rewrite a decision already taken. Java 8, zero deps beyond
 * matou-spi plus example1 (vocabulary ids only, Q2 holds).
 */
public final class LootSeal {
    private LootSeal() {}

    /**
     * Seal view for one tick: {@code example1.loot:harvested} (harvest to
     * harvest tick) plus {@code example1.loot:table} (kind to content item
     * ref) plus {@code example1.loot:count} (items per harvest).
     * Insertion order throughout, so live and verdict replay agree.
     *
     * @throws NullPointerException when store or table is null.
     * @throws IllegalArgumentException when count is not positive.
     */
    public static Map<MatouId, Object> seal(DropStore store,
            Map<String, String> table, long count) {
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
        states.put(LootJob.HARVESTED, store.sealed());
        states.put(LootJob.TABLE, Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(table)));
        states.put(LootJob.COUNT, Long.valueOf(count));
        return states;
    }
}
