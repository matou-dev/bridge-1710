package fr.iamacat.bridge;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        int[] pos = ForgeCells.parseCell("3,7");
        if (pos[0] != 3 || pos[1] != 7) {
            System.out.println("FAIL bridge-forge : parseCell=3,7");
            System.exit(1);
        }
        System.out.println("ok bridge-forge : parseCell");
        for (String bad : new String[]{"3", "a,b", "1,2,3", ""}) {
            try {
                ForgeCells.parseCell(bad);
                System.out.println("FAIL bridge-forge : parseCell <" + bad + ">");
                System.exit(1);
            } catch (IllegalArgumentException e) {
                System.out.println("ok bridge-forge : refused <" + bad + "> (" + e.getMessage() + ")");
            }
        }
        try {
            ForgeCells.parseCell(null);
            System.out.println("FAIL bridge-forge : parseCell null");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-forge : null refused (" + e.getMessage() + ")");
        }

        final List<String> landed = new ArrayList<String>();
        CellSink rec = new CellSink() {
            public void setCell(int x, int z) {
                landed.add(x + "," + z);
            }
        };
        List<String> cells = new ArrayList<String>();
        cells.add("1,2");
        cells.add("3,4");
        ForgeCells.applyCells(cells, rec);
        if (!landed.toString().equals("[1,2, 3,4]")) {
            System.out.println("FAIL bridge-forge : landed=" + landed);
            System.exit(1);
        }
        System.out.println("ok bridge-forge : apply verbatim");
        try {
            List<String> withNull = new ArrayList<String>();
            withNull.add(null);
            ForgeCells.applyCells(withNull, rec);
            System.out.println("FAIL bridge-forge : null entry");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-forge : null entry refused (" + e.getMessage() + ")");
        }

        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(MatouId.parse("matou:tick"), Long.valueOf(9L));
        Snapshot sealed = ForgeSnapshot.snapshot(9L, states);
        if (sealed.tick() != 9L) {
            System.out.println("FAIL bridge-forge : snapshot tick");
            System.exit(1);
        }
        System.out.println("ok bridge-forge : snapshot sealed");
        try {
            ForgeSnapshot.snapshot(-1L, states);
            System.out.println("FAIL bridge-forge : negative tick");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-forge : tick refused (" + e.getMessage() + ")");
        }
        System.out.println("ok bridge-skeleton : all");
    }
}
