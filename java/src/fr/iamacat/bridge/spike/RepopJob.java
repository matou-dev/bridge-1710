package fr.iamacat.bridge.spike;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Repop spike: pure repop decision over bridge-sealed mined states.
 *
 * <p>Reads {@code spike:mined} (map of volume cell to mined tick,
 * numbers) plus {@code spike:delay} (ticks before a mined cell is due).
 * Emits every cell with {@code minedTick + delay <= snapshot tick}.
 * Pure like every job ({@code MatouJob} contract): the mined store
 * itself stays bridge-owned ({@link MinedStore}), this job only reads
 * the sealed snapshot. Java 8, zero deps beyond matou-spi.
 */
public final class RepopJob implements MatouJob<List<String>> {
    public static final MatouId MINED = MatouId.of("spike", "mined");
    public static final MatouId DELAY = MatouId.of("spike", "delay");

    @Override
    public List<String> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_SPIKE_REPOP:null snapshot");
        }
        Map<?, ?> mined = snap.mapOf(MINED);
        Object delayRaw = snap.require(DELAY);
        if (!(delayRaw instanceof Number)) {
            throw new IllegalArgumentException("E_SPIKE_REPOP:type <"
                    + delayRaw + "> (want number delay)");
        }
        long delay = ((Number) delayRaw).longValue();
        if (delay < 0) {
            throw new IllegalArgumentException(
                    "E_SPIKE_REPOP:range <" + delay + "> (want >= 0)");
        }
        List<String> due = new ArrayList<String>();
        for (Map.Entry<?, ?> e : mined.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                throw new IllegalArgumentException("E_SPIKE_REPOP:type <"
                        + e.getKey() + "> (want String cell)");
            }
            if (!(e.getValue() instanceof Number)) {
                throw new IllegalArgumentException("E_SPIKE_REPOP:type <"
                        + e.getValue() + "> (want number tick)");
            }
            long minedTick = ((Number) e.getValue()).longValue();
            if (minedTick < 0) {
                throw new IllegalArgumentException(
                        "E_SPIKE_REPOP:range <" + minedTick + "> (want >= 0)");
            }
            if (minedTick + delay <= snap.tick()) {
                due.add((String) e.getKey());
            }
        }
        return Collections.unmodifiableList(due);
    }
}
