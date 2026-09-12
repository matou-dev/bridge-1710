package fr.iamacat.bridge.model;

import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.wire.OperatorPolicy;
import fr.iamacat.example1.CombatTable;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import fr.iamacat.spi.model.MatouModel;
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
        testShippedTexture();
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
