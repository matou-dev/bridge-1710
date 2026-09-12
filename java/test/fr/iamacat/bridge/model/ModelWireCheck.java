package fr.iamacat.bridge.model;

import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.example1.CombatTable;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import fr.iamacat.spi.model.MatouAnimation;
import fr.iamacat.spi.model.MatouAnimationParser;
import fr.iamacat.spi.model.MatouModel;
import fr.iamacat.spi.model.MatouModelParser;
import fr.iamacat.spi.model.Molang;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gate ModelWireCheck: the bridge-side model wiring without MC — the
 * shipped asset loads, bakes and resolves end to end, and every load
 * failure is coded. Java 8, zero MC imports.
 */
public final class ModelWireCheck {
    private ModelWireCheck() {}

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL model-wire-check : " + msg);
            System.exit(1);
        }
    }

    private static void assertThrows(Runnable r, String expectedFragment) {
        try {
            r.run();
            System.err.println("FAIL model-wire-check : expected exception containing <" + expectedFragment + ">");
            System.exit(1);
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || !msg.contains(expectedFragment)) {
                System.err.println("FAIL model-wire-check : got <" + msg + "> (want fragment <" + expectedFragment + ">)");
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        testShippedAsset();
        testRotatedAsset();
        testShippedTexture();
        testShippedAnimation();
        testWalkPhaseDriver();
        testBonePalette();
        testTripleFiles();
        testTempRoundtrip();
        testRefusals();
        testCombatReachOverride();
        testPerMobReachOverride();
        testPerMobCombat();
        System.out.println("ok model-wire-check : beast model wiring proven pure");
    }

    private static void testShippedAsset() {
        BeastModel beast = BeastModel.load("tools/live/my_beast.geo.json");
        check(beast.model().identifier.equals("geometry.my_beast"), "shipped identifier");
        check(beast.model().bones.size() == 2
                && beast.model().bones.get(0).name.equals("body")
                && beast.model().bones.get(1).name.equals("head"),
                "shipped bones body+head (weakspot table tripwire)");
        float[] mesh = beast.mesh();
        check(mesh.length == beast.model().cubeCount() * 36 * MatouModel.VERTEX_STRIDE,
                "shipped mesh = cubes x 36 stride-8 vertices");
        check(Math.abs(mesh[0] - (-0.5f)) < 1e-6
                && Math.abs(mesh[1]) < 1e-6
                && Math.abs(mesh[2] - 0.5f) < 1e-6,
                "shipped mesh first vertex = body front corner");
        List<BoneBox> at = beast.boxesAt(10.0, 64.0, -3.0);
        check(at.size() == 2, "two placed boxes");
        check(Math.abs(at.get(0).box.minX - 9.5) < 1e-9
                && Math.abs(at.get(0).box.maxY - 65.0) < 1e-9,
                "placed body follows the entity origin");
        RayHit hit = HitTester.test(at,
                new Vec3d(10.0, 64.5, 2.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(hit != null && hit.boneName.equals("body"), "pure ray resolves body");
        // Proof seal (mirrors content/owned.matou — the live seal happens
        // at wireCombat, never here): the holder transports, it never
        // owns a multiplier.
        Map<String, Map<String, Float>> weak =
                new LinkedHashMap<String, Map<String, Float>>();
        weak.put("my_beast", Collections.singletonMap("head",
                Float.valueOf(2.0F)));
        Map<String, Double> reach = new LinkedHashMap<String, Double>();
        reach.put("my_beast", Double.valueOf(4.0d));
        BeastModel.sealCombat(weak, reach);
        check(BeastModel.combatWeakspots().get("head").floatValue()
                        == 2.0F,
                "head weakspot 2x");
        check(BeastModel.combatMobs().equals(
                Collections.singleton("my_beast")),
                "sole combat mob");
        check(BeastModel.combatWeakspots("my_beast").get("head")
                        .floatValue() == 2.0F,
                "per-mob head weakspot 2x");
        check(BeastModel.combatReach("my_beast") == 4.0d,
                "per-mob reach 4.0");
    }

    /**
     * Rotated-content battery (rotation live-proof tranche, hub
     * decisions/MATOU_MODEL.md, ported from 1122): the proof asset is
     * the shipped beast plus a head-bone yaw of 45 degrees. The body
     * bakes bit-identical, the head mesh moves, and its box widens
     * conservatively over the rotated shape (never less) while the ray
     * still resolves the head. UVs and the texture grid are untouched.
     */
    private static void testRotatedAsset() throws Exception {
        BeastModel shipped = BeastModel.load("tools/live/my_beast.geo.json");
        BeastModel rotated = BeastModel.load("tools/live/my_beast_rotated.geo.json");
        check(rotated.model().identifier.equals("geometry.my_beast"), "rotated identifier");
        check(rotated.model().bones.size() == 2
                && rotated.model().bones.get(0).name.equals("body")
                && rotated.model().bones.get(1).name.equals("head"),
                "rotated bones body+head");
        float[] a = shipped.mesh();
        float[] b = rotated.mesh();
        check(b.length == a.length
                && b.length == rotated.model().cubeCount() * 36 * MatouModel.VERTEX_STRIDE,
                "rotated mesh = cubes x 36 stride-8 vertices");
        check(Math.abs(b[0] - (-0.5f)) < 1e-6
                && Math.abs(b[1]) < 1e-6
                && Math.abs(b[2] - 0.5f) < 1e-6,
                "rotated mesh first vertex = unrotated body front corner");
        check(!Arrays.equals(a, b), "head yaw moves mesh vertices");
        List<BoneBox> sa = shipped.boxesAt(0.0, 0.0, 0.0);
        List<BoneBox> ra = rotated.boxesAt(0.0, 0.0, 0.0);
        check(ra.size() == 2, "two rotated placed boxes");
        check(ra.get(0).box.minX == sa.get(0).box.minX
                && ra.get(0).box.maxX == sa.get(0).box.maxX
                && ra.get(0).box.minY == sa.get(0).box.minY
                && ra.get(0).box.maxY == sa.get(0).box.maxY
                && ra.get(0).box.minZ == sa.get(0).box.minZ
                && ra.get(0).box.maxZ == sa.get(0).box.maxZ,
                "unrotated body box is bit-identical");
        check(ra.get(1).box.minX <= sa.get(1).box.minX
                && ra.get(1).box.maxX >= sa.get(1).box.maxX
                && ra.get(1).box.minZ <= sa.get(1).box.minZ
                && ra.get(1).box.maxZ >= sa.get(1).box.maxZ,
                "rotated head box covers the unrotated head box");
        check(ra.get(1).box.minX < sa.get(1).box.minX
                && ra.get(1).box.maxX > sa.get(1).box.maxX,
                "45-degree yaw strictly widens the head box");
        check(Math.abs(ra.get(1).box.minX - (-0.397747564417433)) < 1e-9
                && Math.abs(ra.get(1).box.maxX - 0.397747564417433) < 1e-9
                && Math.abs(ra.get(1).box.minY - 0.96875) < 1e-9
                && Math.abs(ra.get(1).box.maxY - 1.53125) < 1e-9
                && Math.abs(ra.get(1).box.minZ - (-0.397747564417433)) < 1e-9
                && Math.abs(ra.get(1).box.maxZ - 0.397747564417433) < 1e-9,
                "rotated head box golden (45-degree yaw widens 9px to 4.5*sqrt(2))");
        RayHit head = HitTester.test(ra,
                new Vec3d(0.0, 1.25, 2.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(head != null && head.boneName.equals("head"), "pure ray resolves rotated head");
        BeastTexture tex = BeastTexture.load("tools/live/my_beast.png",
                rotated.model().textureWidth, rotated.model().textureHeight);
        check(tex.width() == 64 && tex.height() == 64, "rotated grid stays 64x64");
    }

    /**
     * Animation battery (consumer tranche, hub
     * decisions/MATOU_ANIMATION.md): the shipped walk clip loads,
     * evaluates (life-driven head yaw + keyframed body bob, loop wrap),
     * seals per mob, and proves the GPU contract (skinned stride-9
     * layout, identity deltas, posed boxes) — the CPU oracle stays the
     * gate, never the runtime.
     */
    private static void testShippedAnimation() throws Exception {
        BeastAnimation anim = BeastAnimation.load(
                "tools/live/my_beast.animation.json");
        check(anim.clips().size() == 1
                && anim.clips().containsKey("animation.beast.walk"),
                "shipped animation carries the walk clip");
        MatouAnimation walk = anim.clips().get("animation.beast.walk");
        check(walk.loop == MatouAnimation.Loop.LOOP, "walk loops");
        check(Math.abs(walk.length - 0.5) < 1e-12, "walk length defaults to last key");
        check(walk.bones.size() == 2
                && walk.bones.containsKey("body")
                && walk.bones.containsKey("head"),
                "walk animates body+head (shipped bones)");
        Molang.Ctx at0 = new Molang.Ctx(0.0, 0.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.0, at0).bones.get("head").rotX) < 1e-9,
                "walk head starts at 0");
        Molang.Ctx at30 = new Molang.Ctx(0.0, Math.PI / 6.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.0, at30).bones.get("head").rotX - 30.0) < 1e-9,
                "walk head reaches +30 at life pi/6");
        Molang.Ctx mid = new Molang.Ctx(0.25, 0.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.25, mid).bones.get("body").posY - 0.5) < 1e-9,
                "walk body midpoint lerps exact");
        Molang.Ctx wrap = new Molang.Ctx(0.6, 0.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.6, wrap).bones.get("body").posY - 0.2) < 1e-9,
                "walk loop wraps t mod length");
        assertThrows(() -> BeastAnimation.clipFor("my_beast"),
                "E_ANIM_WIRE:unwired");
        Map<String, String> sel = new LinkedHashMap<String, String>();
        sel.put("my_beast", "animation.beast.walk");
        BeastAnimation.sealClip(sel);
        check(BeastAnimation.clipFor(anim, "my_beast") == walk
                || BeastAnimation.clipFor(anim, "my_beast").name.equals(
                        "animation.beast.walk"),
                "sealed clip serves the walk");
        MatouAnimation.AnimPose pose30 = BeastAnimation.poseFor(
                anim, "my_beast", 0.0, at30);
        check(Math.abs(pose30.bones.get("head").rotX - 30.0) < 1e-9,
                "sealed poseFor drives the head");
        BeastModel shipped = BeastModel.load("tools/live/my_beast.geo.json");
        float[] mesh = shipped.mesh();
        float[] skinned = shipped.model().bakeSkinnedMesh();
        check(skinned.length == shipped.model().cubeCount() * 36 * 9,
                "skinned mesh = cubes x 36 stride-9 vertices");
        check(Math.abs(skinned[0] - mesh[0]) < 1e-9
                && Math.abs(skinned[1] - mesh[1]) < 1e-9
                && Math.abs(skinned[2] - mesh[2]) < 1e-9,
                "skinned bind positions equal the bake");
        check(skinned[8] == 0.0f, "first cube rides bone 0 (body)");
        check(skinned[36 * 9 + 8] == 1.0f, "second cube rides bone 1 (head)");
        Map<String, float[]> ident = shipped.model().poseDeltaMatrices(
                MatouAnimation.AnimPose.identity());
        check(ident.size() == 2, "two delta matrices");
        for (float[] m : ident.values()) {
            check(m.length == 16, "delta is 4x4");
            for (int i = 0; i < 16; i++) {
                float want = (i % 5 == 0) ? 1.0f : 0.0f;
                check(Math.abs(m[i] - want) < 1e-6, "identity pose yields identity deltas");
            }
        }
        MatouAnimation.AnimPose idPose = walk.evaluate(0.0, at0);
        check(Arrays.equals(shipped.model().bakePosedMesh(idPose),
                shipped.model().bakeMesh()),
                "identity pose bakes byte-identical (compat)");
        Map<String, float[]> posed = shipped.model().poseDeltaMatrices(pose30);
        float[] headM = posed.get("head");
        check(Math.abs(headM[5] - 1.0f) > 0.05f, "posed head delta moves");
        List<BoneBox> bind = shipped.boxesAt(0.0, 0.0, 0.0);
        List<BoneBox> pb = shipped.model().placedPosedBoxes(0.0, 0.0, 0.0, pose30);
        check(pb.size() == 2, "two posed boxes");
        check(pb.get(1).box.minX <= bind.get(1).box.minX
                && pb.get(1).box.maxX >= bind.get(1).box.maxX,
                "posed head covers the bind head");
        assertThrows(() -> BeastAnimation.load(null), "E_ANIM_GEO:null");
        assertThrows(() -> BeastAnimation.load("tools/live/no-such-beast.animation.json"),
                "E_ANIM_GEO:unreadable");
        assertThrows(() -> BeastAnimation.load("../example1/content/owned.matou"),
                "E_MODEL_JSON:syntax");
        assertThrows(() -> BeastAnimation.sealClip(null), "E_ANIM_WIRE:null");
        assertThrows(() -> BeastAnimation.sealClip(
                new LinkedHashMap<String, String>()), "E_ANIM_WIRE:empty");
        Map<String, String> bad = new LinkedHashMap<String, String>();
        bad.put("my_beast", "animation.beast.missing");
        BeastAnimation.sealClip(bad);
        assertThrows(() -> BeastAnimation.poseFor(anim, "my_beast", 0.0, at0),
                "E_ANIM_WIRE:unknown");
        BeastAnimation.sealClip(sel);
        assertThrows(() -> BeastAnimation.clipFor(anim, null), "E_ANIM_WIRE:null");
        assertThrows(() -> BeastAnimation.clipFor(anim, "nope"), "E_ANIM_WIRE:unknown");
        assertThrows(() -> BeastAnimation.clipFor(anim, null), "E_ANIM_WIRE:null");
        assertThrows(() -> BeastAnimation.clipFor(anim, "nope"), "E_ANIM_WIRE:unknown");
        assertThrows(() -> BeastAnimation.poseFor(anim, "nope", 0.0, at0),
                "E_ANIM_WIRE:unknown");
        Map<String, MatouAnimation> leg = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.beast.walk\": {\"loop\": false,"
                + " \"bones\": {\"leg\": {\"rotation\": [\"1.0\", 0.0, 0.0]}}}}}");
        final MatouAnimation.AnimPose legPose = leg.get("animation.beast.walk")
                .evaluate(0.0, Molang.zeroCtx());
        assertThrows(() -> shipped.model().poseDeltaMatrices(legPose),
                "E_ANIM_BONE:unknown");
    }

    /**
     * Walk-phase driver battery (hub decisions/MATOU_ANIMATION.md): the
     * holder builds the eval context from the per-mob distance (age
     * still rides anim_time + life_time, the tick step stays fixed)
     * and the render path interpolates prev-to-cur over partialTicks —
     * a dist-driven channel phases with distance while the shipped
     * life-driven head is untouched. Pure, zero MC.
     */
    private static void testWalkPhaseDriver() {
        Molang.Ctx ctx = BeastAnimation.animCtx(1.0, 2.0);
        check(Math.abs(ctx.animTime - 1.0) < 1e-12
                && Math.abs(ctx.lifeTime - 1.0) < 1e-12
                && Math.abs(ctx.distMoved - 2.0) < 1e-12
                && Math.abs(ctx.deltaTime - 0.05) < 1e-12,
                "animCtx carries age as time and distance as distMoved");
        check(Math.abs(BeastAnimation.interpDistMoved(0.0, 10.0, 0.5) - 5.0) < 1e-12,
                "walk distance interpolates at half ticks");
        check(Math.abs(BeastAnimation.interpDistMoved(3.0, 3.0, 0.7) - 3.0) < 1e-12,
                "standing mob holds its distance");
        check(Math.abs(BeastAnimation.interpDistMoved(0.0, 10.0, 0.0)) < 1e-12
                && Math.abs(BeastAnimation.interpDistMoved(0.0, 10.0, 1.0) - 10.0) < 1e-12,
                "walk distance eases from prev to cur across the tick");
        Map<String, MatouAnimation> strut = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.beast.strut\": {\"loop\": true,"
                + " \"animation_length\": 1.0,"
                + " \"bones\": {\"head\": {\"rotation\": "
                + "[\"math.sin(query.modified_distance_moved * 3.0) * 30.0\", 0.0, 0.0]}}}}}");
        MatouAnimation strutClip = strut.get("animation.beast.strut");
        Molang.Ctx stood = BeastAnimation.animCtx(0.0, 0.0);
        check(Math.abs(strutClip.evaluate(0.0, stood).bones.get("head").rotX) < 1e-9,
                "dist-driven channel rests at zero distance");
        Molang.Ctx strode = BeastAnimation.animCtx(0.0, Math.PI / 6.0);
        check(Math.abs(strutClip.evaluate(0.0, strode).bones.get("head").rotX - 30.0) < 1e-9,
                "dist-driven channel reaches +30 at dist pi/6");
        Molang.Ctx zero = new Molang.Ctx(0.0, 0.0, 0.0, 0.05, null);
        check(Math.abs(strutClip.evaluate(0.0, zero).bones.get("head").rotX) < 1e-9,
                "zero ctx still rests (wiring-only: shipped clip untouched)");
    }

    /**
     * Bone-palette battery (generic-palette tranche, hub
     * decisions/MATOU_ANIMATION.md): admission 1..MAX_BONES past the
     * old exact-2 guard, pack order (texel (column, bone) holds column
     * {@code c}), and a 3-bone model serving three deltas — the
     * shipped 2-bone beast stays admitted. Pure, zero MC.
     */
    private static void testBonePalette() {
        check(BeastAnimation.paletteBonesOrThrow(1) == 1, "one bone admitted");
        check(BeastAnimation.paletteBonesOrThrow(2) == 2,
                "shipped two bones admitted");
        check(BeastAnimation.paletteBonesOrThrow(BeastAnimation.MAX_BONES)
                == BeastAnimation.MAX_BONES, "ceiling admitted");
        assertThrows(() -> BeastAnimation.paletteBonesOrThrow(0), "E_ANIM_SKIN:bones");
        assertThrows(() -> BeastAnimation.paletteBonesOrThrow(
                BeastAnimation.MAX_BONES + 1), "E_ANIM_SKIN:bones");
        float[] seq = new float[16];
        for (int i = 0; i < 16; i++) {
            seq[i] = i + 1;
        }
        java.nio.FloatBuffer staged = java.nio.FloatBuffer.allocate(32);
        BeastAnimation.packPaletteInto(staged, seq);
        staged.flip();
        check(staged.get(0) == 1.0f && staged.get(1) == 5.0f
                && staged.get(2) == 9.0f && staged.get(3) == 13.0f,
                "texel (0, bone) holds column 0");
        check(staged.get(4) == 2.0f && staged.get(8) == 3.0f
                && staged.get(12) == 4.0f,
                "texels (1..3, bone) hold columns 1..3");
        MatouModel triple = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.triple\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": ["
                + "{\"name\": \"body\", \"pivot\": [0, 8, 0],"
                + " \"cubes\": [{\"origin\": [-8, 0, -8], \"size\": [16, 16, 16],"
                + " \"uv\": [0, 0]}]},"
                + "{\"name\": \"head\", \"parent\": \"body\", \"pivot\": [0, 20, 0],"
                + " \"cubes\": [{\"origin\": [-4, 16, -4], \"size\": [8, 8, 8],"
                + " \"uv\": [32, 0]}]},"
                + "{\"name\": \"arm\", \"parent\": \"body\", \"pivot\": [8, 16, 0],"
                + " \"cubes\": [{\"origin\": [8, 12, -2], \"size\": [4, 8, 4],"
                + " \"uv\": [40, 16]}]}"
                + "]}]}");
        check(BeastAnimation.paletteBonesOrThrow(triple.bones.size()) == 3,
                "three bones admitted");
        float[] skinned = triple.bakeSkinnedMesh();
        check(skinned[2 * 36 * 9 + 8] == 2.0f, "third bone rides index 2");
        Map<String, float[]> deltas = triple.poseDeltaMatrices(
                MatouAnimation.AnimPose.identity());
        check(deltas.size() == 3, "three palette matrices");
    }

    /**
     * Triple-bone proof battery (palette index-2 tranche, hub
     * decisions/MATOU_ANIMATION.md, ported from 1122): the proof-only
     * triple file pair is the shipped beast plus an arm child of the
     * body, animated by the same walk clip name with an arm channel.
     * The pair loads, the arm rides file-order index 2, the sealed clip
     * serves the arm pose, three deltas + three posed boxes flow —
     * index-2 fetch through the seal, gate-proven (live proves the N=3
     * draw path). The files swap as a pair: the shipped 2-bone mesh
     * refuses the triple pose loudly, never silently. Pure, zero MC.
     */
    private static void testTripleFiles() throws Exception {
        BeastModel triple = BeastModel.load(
                "tools/live/my_beast_triple.geo.json");
        check(triple.model().identifier.equals("geometry.my_beast"),
                "triple identifier (drop-in overlay of the shipped beast)");
        check(triple.model().bones.size() == 3
                && triple.model().bones.get(0).name.equals("body")
                && triple.model().bones.get(1).name.equals("head")
                && triple.model().bones.get(2).name.equals("arm"),
                "triple bones body+head+arm (file order)");
        check(BeastAnimation.paletteBonesOrThrow(
                triple.model().bones.size()) == 3,
                "three bones admitted");
        float[] skinned = triple.model().bakeSkinnedMesh();
        check(skinned.length == triple.model().cubeCount() * 36 * 9,
                "triple skinned mesh = cubes x 36 stride-9 vertices");
        check(skinned[8] == 0.0f, "first cube rides bone 0 (body)");
        check(skinned[36 * 9 + 8] == 1.0f, "second cube rides bone 1 (head)");
        check(skinned[2 * 36 * 9 + 8] == 2.0f, "third cube rides bone 2 (arm)");
        BeastAnimation anim = BeastAnimation.load(
                "tools/live/my_beast_triple.animation.json");
        check(anim.clips().size() == 1
                && anim.clips().containsKey("animation.beast.walk"),
                "triple animation carries the walk clip name");
        MatouAnimation walk = anim.clips().get("animation.beast.walk");
        check(Math.abs(walk.length - 0.5) < 1e-12,
                "triple walk length defaults to last key (shipped keys untouched)");
        check(walk.bones.size() == 3
                && walk.bones.containsKey("body")
                && walk.bones.containsKey("head")
                && walk.bones.containsKey("arm"),
                "triple walk animates body+head+arm (channels extended, never altered)");
        Molang.Ctx at0 = new Molang.Ctx(0.0, 0.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.0, at0).bones.get("arm").rotX) < 1e-9,
                "triple arm starts at 0");
        Molang.Ctx atArm = new Molang.Ctx(0.0, Math.PI / 6.0, 0.0, 0.05, null);
        check(Math.abs(walk.evaluate(0.0, atArm).bones.get("head").rotX - 30.0) < 1e-9,
                "triple head keeps the shipped +30 at life pi/6");
        check(Math.abs(walk.evaluate(0.0, atArm).bones.get("arm").rotX - 20.0) < 1e-9,
                "triple arm reaches +20 at life pi/6");
        Map<String, String> sel = new LinkedHashMap<String, String>();
        sel.put("my_beast", "animation.beast.walk");
        BeastAnimation.sealClip(sel);
        MatouAnimation.AnimPose poseArm = BeastAnimation.poseFor(
                anim, "my_beast", 0.0, atArm);
        check(Math.abs(poseArm.bones.get("arm").rotX - 20.0) < 1e-9,
                "sealed poseFor drives the arm (index-2 through the seal)");
        Map<String, float[]> deltas = triple.model().poseDeltaMatrices(poseArm);
        check(deltas.size() == 3, "three palette matrices");
        float[] armM = deltas.get("arm");
        check(Math.abs(armM[5] - 1.0f) > 0.05f, "posed arm delta moves");
        List<BoneBox> pb = triple.model().placedPosedBoxes(
                0.0, 0.0, 0.0, poseArm);
        check(pb.size() == 3, "three posed boxes");
        BeastModel shipped = BeastModel.load("tools/live/my_beast.geo.json");
        assertThrows(() -> shipped.model().poseDeltaMatrices(poseArm),
                "E_ANIM_BONE:unknown");
    }

    private static void testTempRoundtrip() throws Exception {
        Path tmp = Files.createTempFile("beast", ".geo.json");
        String json = "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.probe\","
                + " \"texture_width\": 16, \"texture_height\": 16},"
                + "\"bones\": [{\"name\": \"head\","
                + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [16, 16, 16]}]}]}]}";
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        BeastModel probe = BeastModel.load(tmp.toString());
        check(probe.model().identifier.equals("geometry.probe"), "temp asset loads");
        check(probe.boxesAt(0.0, 0.0, 0.0).size() == 1, "temp boxes place");
        Files.deleteIfExists(tmp);
    }

    /**
     * Texture battery (V2 tranche, hub decisions/MATOU_MODEL.md): the
     * shipped png decodes to the model grid with the painted texels in
     * PNG-native top-row-first order, and every load failure is coded.
     */
    private static void testShippedTexture() throws Exception {
        BeastTexture tex = BeastTexture.load("tools/live/my_beast.png", 64, 64);
        check(tex.width() == 64 && tex.height() == 64, "shipped texture is 64x64");
        byte[] rgba = tex.rgba();
        check(rgba.length == 64 * 64 * 4, "shipped texels are RGBA");
        check((rgba[0] & 0xFF) == 63 && (rgba[1] & 0xFF) == 163
                && (rgba[2] & 0xFF) == 77 && (rgba[3] & 0xFF) == 255,
                "first texel is the body green (top-row-first, never flipped)");
        int hi = (40 + 8 * 64) * 4;
        check((rgba[hi] & 0xFF) == 224 && (rgba[hi + 1] & 0xFF) == 122
                && (rgba[hi + 2] & 0xFF) == 47,
                "head texel is orange inside the (32,0) rect");
        check(tex.uploadBuffer().remaining() == rgba.length, "upload buffer carries all texels");
        assertThrows(() -> BeastTexture.load(null, 64, 64), "E_MODEL_TEX:null");
        assertThrows(() -> BeastTexture.load("tools/live/no-such-beast.png", 64, 64),
                "E_MODEL_TEX:unreadable");
        assertThrows(() -> BeastTexture.load("tools/live/my_beast.geo.json", 64, 64),
                "E_MODEL_TEX:unreadable");
        Path tmp = Files.createTempFile("beast", ".png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(
                8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", tmp.toFile());
        assertThrows(() -> BeastTexture.load(tmp.toString(), 64, 64), "E_MODEL_TEX:dims");
        Files.deleteIfExists(tmp);
    }

    private static void testRefusals() {
        assertThrows(() -> BeastModel.load(null), "E_MODEL_GEO:null");
        assertThrows(() -> BeastModel.load("tools/live/no-such-beast.geo.json"),
                "E_MODEL_GEO:unreadable");
        assertThrows(() -> BeastModel.load("../example1/content/owned.matou"),
                "E_MODEL_JSON:syntax");
    }

    private static Packs.PackSpec spec(String block, String... kv) {
        Map<String, String> args = new LinkedHashMap<String, String>();
        args.put("ownedFile", "o");
        for (int i = 0; i < kv.length; i += 2) {
            args.put(kv[i], kv[i + 1]);
        }
        return new Packs.PackSpec("fr.iamacat.example1.ExamplePack", 63,
                block, args);
    }

    private static List<Packs.PackSpec> specs(Packs.PackSpec... ss) {
        List<Packs.PackSpec> out = new ArrayList<Packs.PackSpec>();
        for (Packs.PackSpec s : ss) {
            out.add(s);
        }
        return out;
    }

    /**
     * Combat reach-override battery (reach-override tranche, hub
     * decisions/VIRTUAL_HITBOXES.md): absent means content, present
     * wins, bad refuses loudly — the same rule the forge
     * {@code wireCombat} consumes, exercised here through the shipped
     * {@code OperatorPolicy}. Same shape as the spawn/loot batteries in
     * {@code SpawnCheck}/{@code LootCheck}, no new gate.
     */
    private static void testCombatReachOverride() {
        CombatTable owned = CombatTable.fromFile(
                "../example1/content/owned.matou");
        check(owned.reach("my_beast") == 4.0
                && owned.reach("my_brute") == 5.0,
                "content reach is 4.0 beast 5.0 brute");
        double content = owned.reach("my_beast");
        List<String> mobs = Arrays.asList("my_beast", "my_brute");
        check(OperatorPolicy.effectiveCombatReach(content, "my_beast",
                mobs, specs(spec("example1:my_ore"))) == 4.0,
                "operator absent means content reach");
        check(OperatorPolicy.effectiveCombatReach(content, "my_beast",
                mobs, specs(spec("example1:my_ore", "combat.reach",
                        "5.0"))) == 5.0,
                "operator combat.reach wins over content");
        check(!OperatorPolicy.present(
                specs(spec("example1:my_ore")),
                OperatorPolicy.COMBAT_REACH),
                "absent reach key is not present");
        check(OperatorPolicy.present(
                specs(spec("example1:my_ore", "combat.reach", "5.0")),
                OperatorPolicy.COMBAT_REACH),
                "present reach key is present");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach", "0"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach", "-1"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach", "x"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach", "NaN"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach", "Infinity"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(
                        spec("example1:my_ore", "combat.reach", "5.0"),
                        spec("example1:my_ore", "combat.reach", "6.0"))),
                "E_COMBAT_WIRE:multi");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reah", "5.0"))),
                "E_COMBAT_WIRE:unknown");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(content,
                "my_beast", mobs, null), "E_COMBAT_WIRE:null");
    }

    /**
     * Per-mob combat reach battery (per-mob tranche, hub
     * decisions/VIRTUAL_HITBOXES.md): absent means content/global, the
     * per-mob key wins for its mob only, a global-plus-per-mob pair
     * resolves per mob, and every bad shape refuses loudly — the same
     * rule the forge {@code wireCombat} consumes, exercised here
     * through the shipped {@code OperatorPolicy}. Same shape as the
     * global battery above, no new gate.
     */
    private static void testPerMobReachOverride() {
        CombatTable owned = CombatTable.fromFile(
                "../example1/content/owned.matou");
        List<String> mobs = Arrays.asList("my_beast", "my_brute");
        List<Packs.PackSpec> bare = specs(spec("example1:my_ore"));
        check(OperatorPolicy.effectiveCombatReach(
                owned.reach("my_beast"), "my_beast", mobs, bare) == 4.0
                && OperatorPolicy.effectiveCombatReach(
                        owned.reach("my_brute"), "my_brute", mobs, bare)
                        == 5.0,
                "per-mob absent means content reach per mob");
        List<Packs.PackSpec> bruteOnly = specs(spec("example1:my_ore",
                "combat.reach.my_brute", "6.0"));
        check(OperatorPolicy.effectiveCombatReach(
                owned.reach("my_beast"), "my_beast", mobs, bruteOnly)
                        == 4.0
                && OperatorPolicy.effectiveCombatReach(
                        owned.reach("my_brute"), "my_brute", mobs,
                        bruteOnly) == 6.0,
                "per-mob combat.reach wins for its mob only");
        check(OperatorPolicy.present(bruteOnly,
                "combat.reach.my_brute"),
                "present per-mob reach key is present");
        check(!OperatorPolicy.present(bruteOnly,
                "combat.reach.my_beast"),
                "absent per-mob reach key is not present");
        List<Packs.PackSpec> agreed = specs(
                spec("example1:my_ore", "combat.reach.my_brute", "6.0"),
                spec("example1:my_ore", "combat.reach.my_brute", "6.0"));
        check(OperatorPolicy.effectiveCombatReach(
                owned.reach("my_brute"), "my_brute", mobs, agreed)
                        == 6.0,
                "agreed per-mob reach wins");
        List<Packs.PackSpec> both = specs(spec("example1:my_ore",
                "combat.reach", "5.0", "combat.reach.my_brute", "6.0"));
        check(OperatorPolicy.effectiveCombatReach(
                owned.reach("my_beast"), "my_beast", mobs, both) == 5.0
                && OperatorPolicy.effectiveCombatReach(
                        owned.reach("my_brute"), "my_brute", mobs, both)
                        == 6.0,
                "per-mob reach beats the global reach for its mob only");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_brute", mobs, specs(spec("example1:my_ore",
                        "combat.reach.my_brute", "0"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_brute", mobs, specs(spec("example1:my_ore",
                        "combat.reach.my_brute", "-1"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_brute", mobs, specs(spec("example1:my_ore",
                        "combat.reach.my_brute", "x"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_brute", mobs, specs(spec("example1:my_ore",
                        "combat.reach.my_brute", "NaN"))),
                "E_COMBAT_WIRE:bad");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_brute", mobs, specs(
                        spec("example1:my_ore",
                                "combat.reach.my_brute", "6.0"),
                        spec("example1:my_ore",
                                "combat.reach.my_brute", "7.0"))),
                "E_COMBAT_WIRE:multi");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reah.my_beast", "5.0"))),
                "E_COMBAT_WIRE:unknown");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach.nope", "5.0"))),
                "E_COMBAT_WIRE:unknown");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach.", "5.0"))),
                "E_COMBAT_WIRE:unknown");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_beast", mobs, specs(spec("example1:my_ore",
                        "combat.reach.my_beast.x", "5.0"))),
                "E_COMBAT_WIRE:unknown");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0, null,
                mobs, bare), "E_COMBAT_WIRE:null");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "my_beast", null, bare), "E_COMBAT_WIRE:null");
        assertThrows(() -> OperatorPolicy.effectiveCombatReach(4.0,
                "nope", mobs, bare), "E_COMBAT_WIRE:unknown");
    }

    /**
     * Per-mob combat seal battery (per-mob tranche, hub
     * decisions/VIRTUAL_HITBOXES.md): two mobs seal both tables, the
     * per-mob getters serve each, and the sole view plus null/unknown
     * reads refuse loudly — the same seal the forge {@code wireCombat}
     * consumes, exercised here without MC.
     */
    private static void testPerMobCombat() {
        Map<String, Map<String, Float>> weak =
                new LinkedHashMap<String, Map<String, Float>>();
        weak.put("my_beast", Collections.singletonMap("head",
                Float.valueOf(2.0F)));
        Map<String, Float> brute = new LinkedHashMap<String, Float>();
        brute.put("head", Float.valueOf(3.0F));
        brute.put("arm", Float.valueOf(1.0F));
        weak.put("my_brute", brute);
        Map<String, Double> reach = new LinkedHashMap<String, Double>();
        reach.put("my_beast", Double.valueOf(4.0d));
        reach.put("my_brute", Double.valueOf(5.0d));
        BeastModel.sealCombat(weak, reach);
        check(BeastModel.combatMobs().size() == 2
                && BeastModel.combatMobs().contains("my_beast")
                && BeastModel.combatMobs().contains("my_brute"),
                "two-mob seal serves both mobs");
        check(BeastModel.combatWeakspots("my_beast").size() == 1
                && BeastModel.combatWeakspots("my_beast").get("head")
                        .floatValue() == 2.0F,
                "sealed beast head 2x");
        check(BeastModel.combatWeakspots("my_brute").size() == 2
                && BeastModel.combatWeakspots("my_brute").get("head")
                        .floatValue() == 3.0F
                && BeastModel.combatWeakspots("my_brute").get("arm")
                        .floatValue() == 1.0F,
                "sealed brute head 3x arm 1x");
        check(BeastModel.combatReach("my_beast") == 4.0d
                && BeastModel.combatReach("my_brute") == 5.0d,
                "sealed per-mob reach");
        assertThrows(() -> BeastModel.combatWeakspots(),
                "E_COMBAT_POLICY:multi");
        assertThrows(() -> BeastModel.combatWeakspots(null),
                "E_COMBAT_POLICY:null");
        assertThrows(() -> BeastModel.combatWeakspots("nope"),
                "E_COMBAT_POLICY:unknown");
        assertThrows(() -> BeastModel.combatReach(null),
                "E_COMBAT_POLICY:null");
        assertThrows(() -> BeastModel.combatReach("nope"),
                "E_COMBAT_POLICY:unknown");
        assertThrows(() -> BeastModel.sealCombat(null, reach),
                "E_COMBAT_POLICY:null");
        assertThrows(() -> BeastModel.sealCombat(weak, null),
                "E_COMBAT_POLICY:null");
        assertThrows(() -> BeastModel.sealCombat(
                new LinkedHashMap<String, Map<String, Float>>(), reach),
                "E_COMBAT_POLICY:empty");
        assertThrows(() -> BeastModel.sealCombat(weak,
                new LinkedHashMap<String, Double>()),
                "E_COMBAT_POLICY:empty");
        Map<String, Map<String, Float>> lonely =
                new LinkedHashMap<String, Map<String, Float>>(weak);
        lonely.remove("my_brute");
        assertThrows(() -> BeastModel.sealCombat(lonely, reach),
                "E_COMBAT_POLICY:lonely");
        Map<String, Map<String, Float>> badMult =
                new LinkedHashMap<String, Map<String, Float>>();
        badMult.put("my_beast", Collections.singletonMap("head",
                Float.valueOf(0.0F)));
        Map<String, Double> oneReach =
                new LinkedHashMap<String, Double>();
        oneReach.put("my_beast", Double.valueOf(4.0d));
        assertThrows(() -> BeastModel.sealCombat(badMult, oneReach),
                "E_COMBAT_POLICY:bad");
        Map<String, Double> badReach =
                new LinkedHashMap<String, Double>();
        badReach.put("my_beast", Double.valueOf(0.0d));
        Map<String, Map<String, Float>> oneWeak =
                new LinkedHashMap<String, Map<String, Float>>();
        oneWeak.put("my_beast", Collections.singletonMap("head",
                Float.valueOf(2.0F)));
        assertThrows(() -> BeastModel.sealCombat(oneWeak, badReach),
                "E_COMBAT_POLICY:bad");
    }
}
