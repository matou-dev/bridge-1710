package fr.iamacat.bridge.render;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.RenderStates;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render seal: per-frame seal of bridge-owned frame state into snapshot
 * states (hub {@code decisions/GPU_INSTANCING.md}, adapter tranche).
 * Pure Java, zero Minecraft.
 *
 * <p>The forge renderer calls {@link #seal} beside no pack states —
 * the plan inputs are runtime reality, not content — wraps the result
 * with {@code ForgeSnapshot.snapshot(tick, ...)} (the snapshot choke
 * point stays the single factory, SPI untouched) and runs the pure
 * {@link RenderJob} over it, one instanced draw per decided bucket.
 * The store itself is never shared: only this sealed copy crosses the
 * seam, so a later frame can never rewrite a decision already taken.
 * Insertion order follows the vocabulary seal order, so live and
 * verdict replay agree. Java 8, zero deps beyond matou-spi.
 */
public final class RenderSeal {
    private RenderSeal() {}

    /**
     * Seal view for one frame: eye plus row-major view-projection plus
     * instance records, under the ids the vocabulary resolves. The
     * arrays and the record list are defensively copied — the caller
     * keeps mutating its frame buffers, the sealed snapshot never
     * moves. Never null.
     *
     * @throws NullPointerException when any argument is null.
     * @throws IllegalArgumentException when the vocabulary carries no
     *         render roles (a foreign vocabulary fails here, never seals
     *         under a null id).
     */
    public static Map<MatouId, Object> seal(StateVocabulary vocab,
            double[] eye, float[] viewProjection, List<Rec> recs) {
        if (vocab == null) {
            throw new NullPointerException("E_RENDER_SEAL:null vocabulary");
        }
        if (eye == null) {
            throw new NullPointerException("E_RENDER_SEAL:null eye");
        }
        if (viewProjection == null) {
            throw new NullPointerException("E_RENDER_SEAL:null matrix");
        }
        if (recs == null) {
            throw new NullPointerException("E_RENDER_SEAL:null recs");
        }
        Map<MatouId, Object> states =
                new LinkedHashMap<MatouId, Object>();
        states.put(RenderStates.eye(vocab), eye.clone());
        states.put(RenderStates.matrix(vocab), viewProjection.clone());
        states.put(RenderStates.recs(vocab), Collections.unmodifiableList(
                new ArrayList<Rec>(recs)));
        return states;
    }

    /**
     * Enclosing sphere radius of world-space hitboxes around an entity
     * origin: the furthest box corner decides, so the cull bound never
     * clips a limb the hit-tester still serves. Translation-invariant —
     * the same boxes around any origin seal the same radius, so tick-pos
     * boxes bound the interpolated draw. Loud on null or empty boxes:
     * a boundless record would silently never draw (radius 0 culls
     * everything past the near plane), and a model without boxes is
     * broken content (tripwired by {@code ModelWireCheck}).
     */
    public static float boundRadius(List<BoneBox> boxes,
            double x, double y, double z) {
        if (boxes == null) {
            throw new NullPointerException("E_RENDER_SEAL:null boxes");
        }
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("E_RENDER_SEAL:empty boxes"
                    + " (a boundless record never draws — broken model)");
        }
        double furthest = 0.0;
        for (int i = 0; i < boxes.size(); i++) {
            BoneBox bone = boxes.get(i);
            if (bone == null) {
                throw new NullPointerException("E_RENDER_SEAL:null box <"
                        + i + ">");
            }
            double[] xs = {bone.box.minX - x, bone.box.maxX - x};
            double[] ys = {bone.box.minY - y, bone.box.maxY - y};
            double[] zs = {bone.box.minZ - z, bone.box.maxZ - z};
            for (int cx = 0; cx < 2; cx++) {
                for (int cy = 0; cy < 2; cy++) {
                    for (int cz = 0; cz < 2; cz++) {
                        double d = xs[cx] * xs[cx] + ys[cy] * ys[cy]
                                + zs[cz] * zs[cz];
                        if (d > furthest) {
                            furthest = d;
                        }
                    }
                }
            }
        }
        return (float) StrictMath.sqrt(furthest);
    }
}
