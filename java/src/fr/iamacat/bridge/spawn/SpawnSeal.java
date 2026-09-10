package fr.iamacat.bridge.spawn;

import fr.iamacat.example1.SpawnJob;
import fr.iamacat.spi.MatouId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spawn tranche: per-tick seal of bridge-owned census state plus the wired
 * spawn table into snapshot states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the merge with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory, SPI untouched — and runs
 * the pure {@code SpawnJob} over it. Neither the store nor the range is
 * ever shared: only sealed copies cross the seam, so a later record can
 * never rewrite a decision already taken. Java 8, zero deps beyond
 * matou-spi plus example1 (vocabulary ids only, Q2 holds).
 */
public final class SpawnSeal {
    private SpawnSeal() {}

    /**
     * Seal view for one tick: {@code example1.spawn:census} (entity id to
     * spawn cell) plus {@code example1.spawn:table} (content mob ref) plus
     * {@code example1.spawn:cap} plus {@code example1.spawn:budget} plus
     * {@code example1.spawn:y} ({@code [yMin, yMax]} longs). Insertion
     * order throughout, so live and verdict replay agree.
     *
     * @throws NullPointerException when store or mob is null.
     * @throws IllegalArgumentException when mob is bare, cap or budget
     *         is not positive, or the range is unordered.
     */
    public static Map<MatouId, Object> seal(SpawnStore store, String mob,
            long cap, long budget, long yMin, long yMax) {
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
        states.put(SpawnJob.CENSUS, store.sealed());
        states.put(SpawnJob.TABLE, mob);
        states.put(SpawnJob.CAP, Long.valueOf(cap));
        states.put(SpawnJob.BUDGET, Long.valueOf(budget));
        states.put(SpawnJob.Y, Arrays.asList(Long.valueOf(yMin),
                Long.valueOf(yMax)));
        return states;
    }
}
