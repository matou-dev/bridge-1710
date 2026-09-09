package fr.iamacat.bridge;

/**
 * B1 apply seam: pure decision cells land here. The FML side implements
 * this with {@code World.setBlock}; the pure gate tests it with a
 * recording fake. Zero Minecraft on this interface.
 *
 * <p>Two shapes: {@code "x,z"} plane cells land at the wire y/block via
 * {@link #setCell}, {@code "x,y,z:ns:block"} volume cells (V3 structures,
 * carrying their own y and block) via {@link #setBlock}. A sink that only
 * serves one shape keeps the other default — which refuses loudly, never
 * silently drops.
 */
public interface CellSink {
    /**
     * @param x cell abscissa (block X on the FML side)
     * @param z cell ordinate (block Z on the FML side)
     */
    void setCell(int x, int z);

    /**
     * Lands one volume cell. The default refuses loudly so a 2D-only sink
     * never swallows structure cells in silence; sinks serving volumes
     * override it (see {@code WorldCellSink}).
     *
     * @param x block X
     * @param y block Y (the FML side owns the range rule)
     * @param z block Z
     * @param blockName block to place ({@code ns:name}, resolved FML-side)
     */
    default void setBlock(int x, int y, int z, String blockName) {
        throw new UnsupportedOperationException("E_BRIDGE_SINK:3d <" + x
                + "," + y + "," + z + ":" + blockName + "> (2d-only sink)");
    }
}
