package fr.iamacat.bridge;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.Snapshot;
import java.util.Map;

/**
 * B1 snapshot choke point: the FML side converts the live world to plain
 * data maps, this factory seals them into an immutable {@link Snapshot}
 * with bridge-named refusals. Jobs only ever see the snapshot, never the
 * world. Pure Java, zero Minecraft. Java 8, zero deps beyond matou-spi.
 */
public final class ForgeSnapshot {
    private ForgeSnapshot() {}

    /**
     * @param tick server tick, must be {@code >= 0}.
     * @param states plain-data states keyed by SPI id, never null.
     * @throws IllegalArgumentException on negative tick.
     * @throws NullPointerException on null states (via Snapshot).
     */
    public static Snapshot snapshot(long tick, Map<MatouId, Object> states) {
        if (tick < 0) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_TICK:range <" + tick + "> (want >= 0)");
        }
        return new Snapshot(tick, states);
    }
}
