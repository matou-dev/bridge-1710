package fr.iamacat.bridge.render;

import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.spi.LootStates;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.RenderStates;
import fr.iamacat.spi.Snapshot;
import fr.iamacat.spi.StateVocabulary;
import fr.iamacat.spi.hit.AABBd;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Render adapter gate (no JUnit): the bridge-owned job plus seal over
 * the SPI render vocabulary, pattern of the spike checks (store/seal
 * against job). Hand-computed frustum goldens lock the snapshot shapes
 * (identity view-projection is the unit clip cube), the culled-key
 * golden locks absent-means-culled, refusals prove nothing defaults.
 * Any violation prints {@code FAIL render-wire : ...} and exits 1.
 * Run by tools/check.sh.
 */
public final class RenderWireCheck {
    private RenderWireCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL render-wire : " + what);
            System.exit(1);
        }
        System.out.println("ok render-wire : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok render-wire : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL render-wire : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok render-wire : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL render-wire : accepted " + what);
        System.exit(1);
    }

    /** Identity view-projection, row-major: the unit clip cube. */
    private static float[] identity() {
        return new float[] {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f};
    }

    private static Snapshot frame(List<Rec> recs) {
        Map<MatouId, Object> states = RenderSeal.seal(
                RenderJob.vocabulary(), new double[] {0.0, 0.0, 0.0},
                identity(), recs);
        return ForgeSnapshot.snapshot(7L, states);
    }

    public static void main(String[] args) {
        // Vocabulary: bridge-harness namespace, render roles seal-ordered.
        check("matoubridge.render".equals(
                RenderJob.vocabulary().namespace()),
                "job vocabulary keeps its namespace");
        check(RenderJob.vocabulary().names().equals(
                Arrays.asList("eye", "matrix", "recs")),
                "job vocabulary seal-ordered");
        check(MatouId.of("matoubridge.render", "eye").equals(RenderJob.EYE),
                "job eye id shared with the seal");
        check(MatouId.of("matoubridge.render", "matrix").equals(
                RenderJob.MATRIX), "job matrix id shared with the seal");
        check(MatouId.of("matoubridge.render", "recs").equals(
                RenderJob.RECS), "job recs id shared with the seal");

        // Seal: defensive copies — later frame writes never move a seal.
        double[] eye = {1.0, 2.0, 3.0};
        float[] vp = identity();
        List<Rec> recs = new ArrayList<Rec>();
        recs.add(new Rec("my_beast", "tint", 0.0, 0.0, 0.0, 1f, 0f));
        Map<MatouId, Object> states = RenderSeal.seal(
                RenderJob.vocabulary(), eye, vp, recs);
        eye[0] = 9.0;
        vp[0] = 9f;
        recs.clear();
        check(Arrays.equals(new double[] {1.0, 2.0, 3.0},
                (double[]) states.get(RenderJob.EYE)),
                "seal copies the eye");
        check(((float[]) states.get(RenderJob.MATRIX))[0] == 1f,
                "seal copies the matrix");
        check(((List<?>) states.get(RenderJob.RECS)).size() == 1,
                "seal copies the records");
        try {
            ((List<?>) states.get(RenderJob.RECS)).add(null);
            check(false, "sealed records immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok render-wire : sealed records immutable");
        }

        // Seal refuses a foreign vocabulary loudly, never under null ids.
        final StateVocabulary foreign =
                LootStates.vocabulary("example1.loot");
        expectIAE(new Runnable() {
            @Override public void run() {
                RenderSeal.seal(foreign, new double[3], identity(),
                        new ArrayList<Rec>());
            }
        }, "render seal on loot vocabulary");

        // Bound radius: unit box corners decide (sqrt(3)), translated too.
        List<BoneBox> unit = Arrays.asList(new BoneBox("body",
                new AABBd(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0)));
        float radius = RenderSeal.boundRadius(unit, 0.0, 0.0, 0.0);
        check(Math.abs(radius - (float) StrictMath.sqrt(3.0)) < 1e-6f,
                "bound radius is the furthest corner");
        List<BoneBox> shifted = Arrays.asList(new BoneBox("body",
                new AABBd(10.0, 0.0, 0.0, 11.0, 1.0, 1.0)));
        check(Math.abs(RenderSeal.boundRadius(shifted, 10.0, 0.0, 0.0)
                - (float) StrictMath.sqrt(3.0)) < 1e-6f,
                "bound radius is translation-invariant");
        expectIAE(new Runnable() {
            @Override public void run() {
                RenderSeal.boundRadius(new ArrayList<BoneBox>(),
                        0.0, 0.0, 0.0);
            }
        }, "empty boxes");
        expectNPE(new Runnable() {
            @Override public void run() {
                RenderSeal.boundRadius(null, 0.0, 0.0, 0.0);
            }
        }, "null boxes");

        // Decide goldens: identity VP, eye at the origin. Same-key pair
        // stays bucketed in record order, the far record culls its key
        // away (absent means fully culled), the straddler is kept.
        List<Rec> scene = Arrays.asList(
                new Rec("my_beast", "tint", 0.0, 0.0, 0.0, 0.5f, 0f),
                new Rec("my_beast", "tint", 0.5, 0.0, 0.0, 0.5f, 90f),
                new Rec("my_brute", "tint", 0.0, 0.0, -5.0, 1f, 0f),
                new Rec("my_beast", "tint", 0.0, 0.0, 0.9, 0.5f, 0f));
        Map<String, List<Integer>> buckets =
                new RenderJob().decide(frame(scene));
        check(buckets.size() == 1, "culled key absent, one bucket kept");
        check(buckets.get("my_beast\0tint").equals(
                Arrays.asList(Integer.valueOf(0), Integer.valueOf(1),
                        Integer.valueOf(3))),
                "visible indices in record order");
        // Determinism: the same frame decides the same buckets.
        check(new RenderJob().decide(frame(scene)).equals(buckets),
                "frame decision deterministic");

        // Decide refusals: never a defaulted plan.
        expectNPE(new Runnable() {
            @Override public void run() {
                new RenderJob().decide(null);
            }
        }, "null snapshot");
        expectIAE(new Runnable() {
            @Override public void run() {
                Map<MatouId, Object> bare =
                        new java.util.LinkedHashMap<MatouId, Object>();
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, bare));
            }
        }, "missing eye");
        expectIAE(new Runnable() {
            @Override public void run() {
                Map<MatouId, Object> states =
                        new java.util.LinkedHashMap<MatouId, Object>();
                states.put(RenderJob.EYE, "0,0,0");
                states.put(RenderJob.MATRIX, new float[16]);
                states.put(RenderJob.RECS, new ArrayList<Rec>());
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, states));
            }
        }, "string eye");
        expectIAE(new Runnable() {
            @Override public void run() {
                Map<MatouId, Object> states =
                        new java.util.LinkedHashMap<MatouId, Object>();
                states.put(RenderJob.EYE, new double[] {0.0, 0.0});
                states.put(RenderJob.MATRIX, new float[16]);
                states.put(RenderJob.RECS, new ArrayList<Rec>());
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, states));
            }
        }, "short eye");
        expectIAE(new Runnable() {
            @Override public void run() {
                Map<MatouId, Object> states =
                        new java.util.LinkedHashMap<MatouId, Object>();
                states.put(RenderJob.EYE,
                        new double[] {0.0, Double.NaN, 0.0});
                states.put(RenderJob.MATRIX, new float[16]);
                states.put(RenderJob.RECS, new ArrayList<Rec>());
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, states));
            }
        }, "NaN eye");
        expectIAE(new Runnable() {
            @Override public void run() {
                Map<MatouId, Object> states =
                        new java.util.LinkedHashMap<MatouId, Object>();
                states.put(RenderJob.EYE, new double[3]);
                states.put(RenderJob.MATRIX, new float[15]);
                states.put(RenderJob.RECS, new ArrayList<Rec>());
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, states));
            }
        }, "short matrix");
        expectIAE(new Runnable() {
            @Override public void run() {
                List<Object> mixed = new ArrayList<Object>();
                mixed.add("my_beast");
                Map<MatouId, Object> states =
                        new java.util.LinkedHashMap<MatouId, Object>();
                states.put(RenderJob.EYE, new double[3]);
                states.put(RenderJob.MATRIX, new float[16]);
                states.put(RenderJob.RECS, mixed);
                new RenderJob().decide(ForgeSnapshot.snapshot(0L, states));
            }
        }, "string record");
        final List<Rec> hole = new ArrayList<Rec>();
        hole.add(null);
        expectNPE(new Runnable() {
            @Override public void run() {
                new RenderJob().decide(frame(hole));
            }
        }, "null record element");

        System.out.println("ok render-wire : all");
    }
}
