package fr.iamacat.bridge;

/**
 * B1 apply seam: a pure {@code "x,z"} cell lands here. The FML side
 * implements this with {@code World.setBlock}; the pure gate tests it with
 * a recording fake. Zero Minecraft on this interface.
 */
public interface CellSink {
    /**
     * @param x cell abscissa (block X on the FML side)
     * @param z cell ordinate (block Z on the FML side)
     */
    void setCell(int x, int z);
}
