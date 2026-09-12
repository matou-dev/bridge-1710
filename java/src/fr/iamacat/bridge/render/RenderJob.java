package fr.iamacat.bridge.render;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.RenderStates;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.render.Frustum;
import fr.iamacat.spi.render.InstanceBucket;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import java.util.List;
import java.util.Map;

/**
 * Render job: pure frustum-cull plan over bridge-sealed frame states
 * (hub {@code decisions/GPU_INSTANCING.md}, adapter tranche). Reads the
 * camera eye under {@link #EYE} ({@code double[3]} world position) plus
 * the row-major view-projection matrix under {@link #MATRIX}
 * ({@code float[16]}, see
 * {@link fr.iamacat.spi.render.ViewProjection}) plus the instance
 * records under {@link #RECS}, and emits the
 * {@link InstanceBucket#plan} buckets ({@code model + "\0" + texture}
 * to input indices) — the exact per-bucket sets the forge renderer
 * uploads and draws, one instanced draw each.
 *
 * <p>Bridge harness, same class as {@code RepopJob}: the plan inputs
 * are per-frame runtime reality (camera, matrices, living beasts),
 * never content tables, so no pack serves them and no
 * {@code PolicyPack} grows a camera-math accessor. The namespace rides
 * the frozen bridge modid (hub {@code NAMES.md} — every bridge ships
 * {@code matoubridge}, so the sealed ids are identical on all four
 * runtimes by construction, never re-pointed per version).
 * Pure like every job ({@code MatouJob} contract): the sealed copy
 * crosses the seam ({@link RenderSeal}), this job only reads the
 * snapshot. Java 8, zero deps beyond matou-spi.
 */
public final class RenderJob implements MatouJob<Map<String, List<Integer>>> {
    /**
     * Bridge-harness namespace (T3 registry: the seal resolves the
     * same vocabulary — hub {@code decisions/SPI_STATE_VOCABULARY.md},
     * explicit provision, never a static registry).
     */
    public static final String NAMESPACE = "matoubridge.render";

    private static final StateVocabulary VOCABULARY =
            RenderStates.vocabulary(NAMESPACE);

    /** Sealed camera eye: {@code double[3]} world position. */
    public static final MatouId EYE = RenderStates.eye(VOCABULARY);
    /** Sealed view-projection: {@code float[16]} row-major matrix. */
    public static final MatouId MATRIX = RenderStates.matrix(VOCABULARY);
    /** Sealed instance records: list of render records. */
    public static final MatouId RECS = RenderStates.recs(VOCABULARY);

    /** Shared render vocabulary (the seal resolves the same). */
    public static StateVocabulary vocabulary() {
        return VOCABULARY;
    }

    @Override
    public Map<String, List<Integer>> decide(Snapshot snap) {
        if (snap == null) {
            throw new NullPointerException("E_RENDER_JOB:null snapshot");
        }
        double[] eye = eyeOf(snap.require(EYE));
        float[] matrix = matrixOf(snap.require(MATRIX));
        List<Rec> recs = recsOf(snap.require(RECS));
        return InstanceBucket.plan(recs, eye[0], eye[1], eye[2],
                Frustum.of(matrix));
    }

    private static double[] eyeOf(Object raw) {
        if (!(raw instanceof double[])) {
            throw new IllegalArgumentException("E_RENDER_JOB:eye <"
                    + describe(raw) + "> (want double[3] world position)");
        }
        double[] eye = (double[]) raw;
        if (eye.length != 3) {
            throw new IllegalArgumentException("E_RENDER_JOB:eye <length "
                    + eye.length + "> (want double[3] world position)");
        }
        for (int i = 0; i < 3; i++) {
            if (!Double.isFinite(eye[i])) {
                throw new IllegalArgumentException("E_RENDER_JOB:eye <"
                        + eye[i] + "> (want finite world position)");
            }
        }
        return eye;
    }

    private static float[] matrixOf(Object raw) {
        if (!(raw instanceof float[])) {
            throw new IllegalArgumentException("E_RENDER_JOB:matrix <"
                    + describe(raw)
                    + "> (want float[16] row-major view-projection)");
        }
        float[] matrix = (float[]) raw;
        if (matrix.length != 16) {
            throw new IllegalArgumentException("E_RENDER_JOB:matrix <length "
                    + matrix.length
                    + "> (want float[16] row-major view-projection)");
        }
        return matrix;
    }

    @SuppressWarnings("unchecked")
    private static List<Rec> recsOf(Object raw) {
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("E_RENDER_JOB:recs <"
                    + describe(raw) + "> (want list of render records)");
        }
        List<?> recs = (List<?>) raw;
        for (int i = 0; i < recs.size(); i++) {
            if (!(recs.get(i) instanceof Rec)) {
                if (recs.get(i) == null) {
                    throw new NullPointerException(
                            "E_RENDER_JOB:recs null element <" + i + ">");
                }
                throw new IllegalArgumentException("E_RENDER_JOB:recs <"
                        + recs.get(i).getClass().getName()
                        + "> (want render records)");
            }
        }
        return (List<Rec>) recs;
    }

    private static String describe(Object raw) {
        if (raw == null) {
            return "null";
        }
        return raw.getClass().getName();
    }
}
