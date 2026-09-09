package fr.iamacat.bridge;

import java.util.List;

/**
 * B1 pure cell helpers: parse decision cells and apply them verbatim to a
 * {@link CellSink}. Two shapes: {@code "x,z"} plane cells (wire y/block)
 * and {@code "x,y,z:ns:block"} volume cells (own y/block). Pure Java,
 * zero Minecraft: the FML side owns the live world, this side only
 * refuses loudly. Java 8, zero deps.
 */
public final class ForgeCells {
    private ForgeCells() {}

    /** One parsed volume cell: position plus the block name to place. */
    public static final class BlockCell {
        public final int x;
        public final int y;
        public final int z;
        public final String block;

        BlockCell(int x, int y, int z, String block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

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
     * @return parsed volume cell, never null.
     * @throws NullPointerException when cell is null.
     * @throws IllegalArgumentException when cell is not
     *         {@code "x,y,z:ns:block"} (ints plus a non-empty block name).
     *         The y range is owned FML-side ({@code WorldCellSink}).
     */
    public static BlockCell parseBlockCell(String cell) {
        if (cell == null) {
            throw new NullPointerException(
                    "E_BRIDGE_CELL:null (want \"x,y,z:ns:block\")");
        }
        int cut = cell.indexOf(':');
        String head = cut < 0 ? cell : cell.substring(0, cut);
        String block = cut < 0 ? "" : cell.substring(cut + 1);
        String[] parts = head.split(",", -1);
        if (parts.length != 3 || block.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell
                            + "> (want \"x,y,z:ns:block\")");
        }
        try {
            return new BlockCell(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    block);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell
                            + "> (want \"x,y,z:ns:block\")");
        }
    }

    /**
     * Applies every cell in order, no dedup, no replace: merge policy lives
     * in the jobs (Q3-Q4), the bridge only lands data. A cell holding a
     * {@code :} is a volume cell ({@link #setBlock} path), anything else a
     * plane cell ({@link #setCell} path) — both refuse loudly on bad
     * shape. Null entries refused.
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
            if (cell.indexOf(':') < 0) {
                int[] pos = parseCell(cell);
                sink.setCell(pos[0], pos[1]);
            } else {
                BlockCell vol = parseBlockCell(cell);
                sink.setBlock(vol.x, vol.y, vol.z, vol.block);
            }
        }
    }
}
