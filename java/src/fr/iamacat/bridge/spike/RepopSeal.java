package fr.iamacat.bridge.spike;

import fr.iamacat.spi.MatouId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Repop spike: per-tick seal of bridge-owned mined state into snapshot
 * states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the result with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory, SPI untouched — and
 * runs the pure {@link RepopJob} over it. The store itself is never
 * shared: only this sealed copy crosses the seam, so a later record can
 * never rewrite a decision already taken. Java 8, zero deps beyond
 * matou-spi.
 */
public final class RepopSeal {
    private RepopSeal() {}

    /**
     * Seal view for one tick: {@code spike:mined} (cell to mined tick)
     * plus {@code spike:delay}. Insertion order throughout, so live and
     * verdict replay agree. Never null.
     *
     * @throws NullPointerException when store is null.
     * @throws IllegalArgumentException when delay is negative.
     */
    public static Map<MatouId, Object> seal(MinedStore store, long delay) {
        if (store == null) {
            throw new NullPointerException("E_SPIKE_SEAL:null store");
        }
        if (delay < 0) {
            throw new IllegalArgumentException(
                    "E_SPIKE_SEAL:range <" + delay + "> (want >= 0)");
        }
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(RepopJob.MINED, store.sealed());
        states.put(RepopJob.DELAY, Long.valueOf(delay));
        return states;
    }
}
