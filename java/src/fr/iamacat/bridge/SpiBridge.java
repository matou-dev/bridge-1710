package fr.iamacat.bridge;

import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * M1 walking skeleton: SPI decision in, applied log out. Pure Java, zero
 * {@code net.minecraft} (no MC jars on this gate) and zero legacy lib
 * (see gate no-legacy-matoulib in tools/check.sh).
 *
 * <p>TODO(FORGE): call {@link #tick} from the FML server tick and map
 * decisions to 1.7.10 registries. That wiring — and only that — may import
 * {@code net.minecraft} / {@code cpw.mods}.
 */
public final class SpiBridge {
    private final List<Object> applied = new ArrayList<Object>();

    /** One tick: pure decide on the SPI side, side effects owned here. */
    public <D> void tick(Snapshot snap, MatouJob<D> job) {
        if (snap == null) {
            throw new NullPointerException("E_BRIDGE_SNAPSHOT:null");
        }
        if (job == null) {
            throw new NullPointerException("E_BRIDGE_JOB:null");
        }
        D decision = job.decide(snap);
        if (decision != null) {
            applied.add(decision);
        }
    }

    public List<Object> applied() {
        return Collections.unmodifiableList(applied);
    }
}
