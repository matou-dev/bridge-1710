package fr.iamacat.bridge;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.Collections;

/**
 * M1 bridge self-test (no JUnit on this gate): any violation prints
 * {@code FAIL bridge-skeleton : ...} and exits 1. Run by tools/check.sh
 * against the {@code ../spi} sibling checkout.
 */
public final class BridgeCheck {
    private BridgeCheck() {}

    public static void main(String[] args) {
        SpiBridge bridge = new SpiBridge();
        MatouJob<String> yes = new MatouJob<String>() {
            public String decide(Snapshot snap) {
                return "tick-" + snap.tick();
            }
        };
        MatouJob<String> none = new MatouJob<String>() {
            public String decide(Snapshot snap) {
                return null;
            }
        };
        Snapshot s1 = new Snapshot(1L,
                Collections.<MatouId, Object>emptyMap());
        Snapshot s2 = new Snapshot(2L,
                Collections.<MatouId, Object>emptyMap());

        bridge.tick(s1, yes);
        bridge.tick(s2, none); // null decision applies nothing
        bridge.tick(s2, yes);
        if (!bridge.applied().toString().equals("[tick-1, tick-2]")) {
            System.out.println("FAIL bridge-skeleton : applied="
                    + bridge.applied());
            System.exit(1);
        }
        System.out.println("ok bridge-skeleton : decide-apply walk");

        try {
            bridge.applied().add("x");
            System.out.println("FAIL bridge-skeleton : applied mutable");
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("ok bridge-skeleton : applied immutable");
        }

        try {
            bridge.tick(null, yes);
            System.out.println("FAIL bridge-skeleton : null snapshot");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : null refused (" + e.getMessage() + ")");
        }
        System.out.println("ok bridge-skeleton : all");
    }
}
