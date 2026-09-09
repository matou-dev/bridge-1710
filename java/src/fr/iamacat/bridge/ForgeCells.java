package fr.iamacat.bridge;

import java.util.List;

/**
 * B1 pure cell helpers: parse {@code "x,z"} decision cells and apply them
 * verbatim to a {@link CellSink}. Pure Java, zero Minecraft: the FML side
 * owns the live world, this side only refuses loudly. Java 8, zero deps.
 */
public final class ForgeCells {
    private ForgeCells() {}

    /**
     * @return int[2] {x, z}, never null.
     * @throws NullPointerException when cell is null.
     * @throws IllegalArgumentException when cell is not {@code "x,z"} ints.
     */
    public static int[] parseCell(String cell) {
        if (cell == null) {
            throw new NullPointerException("E_BRIDGE_CELL:null (want \"x,z\")");
        }
        String[] parts = cell.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell + "> (want \"x,z\")");
        }
        try {
            return new int[]{Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell + "> (want \"x,z\")");
        }
    }

    /**
     * Applies every cell in order, no dedup, no replace: merge policy lives
     * in the jobs (Q3-Q4), the bridge only lands data. Null entries refused.
     *
     * @throws NullPointerException when cells, sink, or any entry is null.
     */
    public static void applyCells(List<String> cells, CellSink sink) {
        if (cells == null) {
            throw new NullPointerException("E_BRIDGE_CELLS:null");
        }
        if (sink == null) {
            throw new NullPointerException("E_BRIDGE_SINK:null");
        }
        for (String cell : cells) {
            if (cell == null) {
                throw new NullPointerException("E_BRIDGE_CELL:null entry");
            }
            int[] pos = parseCell(cell);
            sink.setCell(pos[0], pos[1]);
        }
    }
}
