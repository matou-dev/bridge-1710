package fr.iamacat.bridge.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Bridge-side holder of the beast texture (hub decisions/MATOU_MODEL.md,
 * V2 tranche): loads the operator {@code my_beast.png} once, serves the
 * raw RGBA texels top-row-first with their dimensions to the thin forge
 * renderer, which uploads them through the SPI GlBackend texture surface.
 * Zero MC imports — the forge renderer stays a two-liner, the decode and
 * refusals stay testable here.
 *
 * <p>Row order is PNG-native top-row-first with NO vertical flip: GL
 * samples v = 0 at the first uploaded row and the Bedrock per-face
 * convention bakes v = 0 at the texture top, so the first decoded row
 * must stay first in the upload (flipping here would mirror every beast
 * vertically — the mirror-trap class, hub decisions/GPU_INSTANCING.md).
 */
public final class BeastTexture {
    /**
     * Operator texture path, deployed by {@code tools/run-live.sh} next to
     * the geo asset and replaceable by hand (same rule as packs.cfg: the
     * harness never clobbers a hand-tuned file it did not write — the
     * client script copies only when missing).
     */
    public static final String TEX_PATH = "config/matoubridge/my_beast.png";

    private final int width;
    private final int height;
    private final byte[] rgba;

    private BeastTexture(int width, int height, byte[] rgba) {
        this.width = width;
        this.height = height;
        this.rgba = rgba;
    }

    /**
     * Parses the texture file at {@code path} against the model grid
     * {@code gridW} x {@code gridH}: loud on any failure. A decoded size
     * off the declared grid refuses (a stretched sample would lie
     * silently — every baked uv divides by the declared grid).
     */
    public static BeastTexture load(String path, int gridW, int gridH) {
        if (path == null) {
            throw new NullPointerException("E_MODEL_TEX:null path (want a texture file)");
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            throw new IllegalStateException("E_MODEL_TEX:unreadable <"
                    + path + "> (" + e.getMessage() + ")", e);
        }
        final BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("E_MODEL_TEX:unreadable <"
                    + path + "> (" + e.getMessage() + ")", e);
        }
        if (img == null) {
            throw new IllegalStateException("E_MODEL_TEX:unreadable <"
                    + path + "> (not a decodable image)");
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            throw new IllegalStateException("E_MODEL_TEX:shape <"
                    + path + " " + w + "x" + h + "> (want positive dims)");
        }
        if (w != gridW || h != gridH) {
            throw new IllegalStateException("E_MODEL_TEX:dims <"
                    + path + " " + w + "x" + h + "> (want the model grid "
                    + gridW + "x" + gridH + " — a stretched sample lies)");
        }
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            rgba[i * 4] = (byte) ((p >> 16) & 0xFF);
            rgba[i * 4 + 1] = (byte) ((p >> 8) & 0xFF);
            rgba[i * 4 + 2] = (byte) (p & 0xFF);
            rgba[i * 4 + 3] = (byte) ((p >> 24) & 0xFF);
        }
        return new BeastTexture(w, h, rgba);
    }

    private static volatile BeastTexture cached;

    /** Process-wide beast texture, loaded once from {@link #TEX_PATH}. */
    public static BeastTexture cached() {
        BeastTexture hit = cached;
        if (hit == null) {
            synchronized (BeastTexture.class) {
                hit = cached;
                if (hit == null) {
                    BeastModel beast = BeastModel.cached();
                    hit = load(TEX_PATH, beast.model().textureWidth,
                            beast.model().textureHeight);
                    cached = hit;
                }
            }
        }
        return hit;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Raw RGBA texels, top-row-first: a copy. */
    public byte[] rgba() {
        return rgba.clone();
    }

    /** Direct upload buffer over a copy of {@link #rgba()}. */
    public ByteBuffer uploadBuffer() {
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length)
                .order(ByteOrder.nativeOrder());
        buf.put(rgba);
        buf.flip();
        return buf;
    }
}
