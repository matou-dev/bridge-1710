package fr.iamacat.bridge.model;

import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.MatouModelParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Sealed per-mob combat tables (hub
     * {@code decisions/VIRTUAL_HITBOXES.md}, combat-policy tranche):
     * the content tables transported at wire time
     * ({@code MatouBridgeMod.wireCombat}), never bridge constants. Mob
     * to bone-to-multiplier, plus mob to reach. The shipped asset
     * always carries a {@code head} bone (tripwired by
     * {@code ModelWireCheck}), struck at the sealed multiplier. A read
     * before the seal refuses loudly — an unsealed default would be
     * silent combat behaviour.
     */
    private static volatile Map<String, Map<String, Float>> sealedCombat;

    /** Sealed per-mob reach attributes (same seal as {@link #sealedCombat}). */
    private static volatile Map<String, Double> sealedReach;

    /**
     * Seals the content combat tables once at wire time (parse-once,
     * beside the tables — never on the tick path). Loud on null/empty
     * maps, a mob funding no weakspot (or vice versa), or a missing or
     * non-positive mult or reach.
     */
    public static void sealCombat(
            Map<String, Map<String, Float>> perMobWeakspots,
            Map<String, Double> perMobReach) {
        if (perMobWeakspots == null) {
            throw new NullPointerException("E_COMBAT_POLICY:null "
                    + "weakspots (want the sealed content tables)");
        }
        if (perMobReach == null) {
            throw new NullPointerException("E_COMBAT_POLICY:null "
                    + "reach (want the sealed content reaches)");
        }
        if (perMobWeakspots.isEmpty()) {
            throw new IllegalArgumentException("E_COMBAT_POLICY:empty "
                    + "weakspots (a table nobody pays would be a "
                    + "silent no-op)");
        }
        if (perMobReach.isEmpty()) {
            throw new IllegalArgumentException("E_COMBAT_POLICY:empty "
                    + "reach (an unreached mob would be a silent "
                    + "no-op)");
        }
        Map<String, Map<String, Float>> weak =
                new LinkedHashMap<String, Map<String, Float>>();
        for (Map.Entry<String, Map<String, Float>> e
                : perMobWeakspots.entrySet()) {
            String mob = e.getKey();
            Map<String, Float> rows = e.getValue();
            if (mob == null || mob.isEmpty()
                    || rows == null || rows.isEmpty()
                    || !perMobReach.containsKey(mob)) {
                throw new IllegalArgumentException(
                        "E_COMBAT_POLICY:lonely mob <" + mob + "> (every "
                                + "mob funds a weakspot — an unfunded "
                                + "mob would be a silent no-op)");
            }
            for (Map.Entry<String, Float> row : rows.entrySet()) {
                Float mult = row.getValue();
                if (mult == null || !Float.isFinite(mult.floatValue())
                        || mult.floatValue() <= 0.0F) {
                    throw new IllegalArgumentException(
                            "E_COMBAT_POLICY:bad mult <" + mob + "/"
                                    + row.getKey() + "> (positive f32, "
                                    + "never defaulted)");
                }
            }
            weak.put(mob, Collections.unmodifiableMap(
                    new LinkedHashMap<String, Float>(rows)));
        }
        Map<String, Double> reach =
                new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> e : perMobReach.entrySet()) {
            String mob = e.getKey();
            Double at = e.getValue();
            if (!weak.containsKey(mob)) {
                throw new IllegalArgumentException(
                        "E_COMBAT_POLICY:lonely mob <" + mob + "> "
                                + "(every reach funds a weakspot — an "
                                + "unfunded mob would be a silent "
                                + "no-op)");
            }
            if (at == null || Double.isNaN(at.doubleValue())
                    || Double.isInfinite(at.doubleValue())
                    || at.doubleValue() <= 0.0d) {
                throw new IllegalArgumentException(
                        "E_COMBAT_POLICY:bad reach <" + mob + "> "
                                + "(positive finite f64, never "
                                + "defaulted)");
            }
            reach.put(mob, at);
        }
        sealedReach = Collections.unmodifiableMap(reach);
        sealedCombat = Collections.unmodifiableMap(weak);
    }

    /**
     * Short instance names of every sealed mob, in seal order
     * (unmodifiable, never empty), or the loud unwired refusal.
     */
    public static Set<String> combatMobs() {
        Map<String, Map<String, Float>> hit = sealedCombat;
        if (hit == null) {
            throw new IllegalStateException("E_COMBAT_POLICY:unwired "
                    + "(combat tables never sealed — want wireCombat)");
        }
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(hit.keySet()));
    }

    /**
     * Sealed weakspot table for one mob, or the loud unwired /
     * null / unknown refusal (never 1.0x).
     */
    public static Map<String, Float> combatWeakspots(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_COMBAT_POLICY:null mob "
                    + "(want a sealed mob — see combatMobs)");
        }
        Map<String, Map<String, Float>> hit = sealedCombat;
        if (hit == null) {
            throw new IllegalStateException("E_COMBAT_POLICY:unwired "
                    + "(combat tables never sealed — want wireCombat)");
        }
        Map<String, Float> rows = hit.get(mob);
        if (rows == null) {
            throw new IllegalArgumentException(
                    "E_COMBAT_POLICY:unknown mob <" + mob + "> (want one "
                            + "of " + hit.keySet() + " — never "
                            + "defaulted)");
        }
        return rows;
    }

    /**
     * Sealed reach attribute for one mob, or the loud unwired /
     * null / unknown refusal (never defaulted).
     */
    public static double combatReach(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_COMBAT_POLICY:null mob "
                    + "(want a sealed mob — see combatMobs)");
        }
        Map<String, Double> hit = sealedReach;
        if (hit == null || sealedCombat == null) {
            throw new IllegalStateException("E_COMBAT_POLICY:unwired "
                    + "(combat tables never sealed — want wireCombat)");
        }
        Double at = hit.get(mob);
        if (at == null) {
            throw new IllegalArgumentException(
                    "E_COMBAT_POLICY:unknown mob <" + mob + "> (want one "
                            + "of " + hit.keySet() + " — never "
                            + "defaulted)");
        }
        return at.doubleValue();
    }

    /**
     * Sealed weakspot table, or the loud unwired refusal (never 1.0x).
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #combatWeakspots(String)} per mob).
     */
    public static Map<String, Float> combatWeakspots() {
        Map<String, Map<String, Float>> hit = sealedCombat;
        if (hit == null) {
            throw new IllegalStateException("E_COMBAT_POLICY:unwired "
                    + "(combat tables never sealed — want wireCombat)");
        }
        if (hit.size() != 1) {
            throw new IllegalArgumentException(
                    "E_COMBAT_POLICY:multi sole-view <" + hit.keySet()
                            + "> (the legacy view serves one mob — use "
                            + "combatWeakspots(mob))");
        }
        return hit.values().iterator().next();
    }

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
