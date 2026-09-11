package fr.iamacat.bridge.model;

import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.MatouModelParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bridge-side holder of the generic beast shape (hub
 * decisions/MATOU_MODEL.md, consumer tranche): loads the operator
 * {@code my_beast.geo.json} once, serves the baked renderer mesh and the
 * world-space hitboxes to the thin forge call-sites. Zero MC imports —
 * the forge renderer and entity stay two-liners, the parsing, bake and
 * refusals stay testable here.
 */
public final class BeastModel {
    /**
     * Operator model path, deployed by {@code tools/run-live.sh} next to
     * packs.cfg and replaceable by hand (same rule as packs.cfg: the
     * harness never clobbers a hand-tuned file it did not write — the
     * client script copies only when missing).
     */
    public static final String GEO_PATH = "config/matoubridge/my_beast.geo.json";

    /**
     * Beast-local weakspot table until content-driven weakspots land: the
     * shipped asset always carries a {@code head} bone (tripwired by
     * {@code ModelWireCheck}), struck at 2x. Never a silent default — a
     * renamed bone refuses at load, not at hit time.
     */
    public static final Map<String, Float> WEAKSPOTS =
            Collections.singletonMap("head", Float.valueOf(2.0F));

    private static volatile BeastModel cached;

    private final MatouModel model;
    private final float[] mesh;

    private BeastModel(MatouModel model) {
        this.model = model;
        this.mesh = model.bakeMesh();
    }

    /** Parses the model file at {@code path}: loud on any failure. */
    public static BeastModel load(String path) {
        if (path == null) {
            throw new NullPointerException("E_MODEL_GEO:null path (want a model file)");
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            throw new IllegalStateException("E_MODEL_GEO:unreadable <"
                    + path + "> (" + e.getMessage() + ")", e);
        }
        MatouModel parsed = MatouModelParser.parse(
                new String(bytes, StandardCharsets.UTF_8));
        boolean head = false;
        for (int i = 0; i < parsed.bones.size(); i++) {
            if (parsed.bones.get(i).name.equals("head")) {
                head = true;
            }
        }
        if (!head) {
            throw new IllegalStateException("E_MODEL_GEO:no-head <"
                    + path + "> (the beast weakspot table names head)");
        }
        return new BeastModel(parsed);
    }

    /** Process-wide beast, loaded once from {@link #GEO_PATH}. */
    public static BeastModel cached() {
        BeastModel hit = cached;
        if (hit == null) {
            synchronized (BeastModel.class) {
                hit = cached;
                if (hit == null) {
                    hit = load(GEO_PATH);
                    cached = hit;
                }
            }
        }
        return hit;
    }

    public MatouModel model() {
        return model;
    }

    /** Baked renderer mesh (pos3, uv2, normal3 interleaved): a copy. */
    public float[] mesh() {
        return mesh.clone();
    }

    /** World-space hitboxes at the entity origin (feet). */
    public List<BoneBox> boxesAt(double x, double y, double z) {
        return model.placedBoxes(x, y, z);
    }
}
