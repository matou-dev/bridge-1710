package fr.iamacat.bridge.model;

import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import fr.iamacat.spi.model.MatouModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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
        testTempRoundtrip();
        testRefusals();
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
        BeastModel.sealWeakspots(Collections.singletonMap("head",
                Float.valueOf(2.0F)));
        check(BeastModel.combatWeakspots().get("head").floatValue()
                        == 2.0F,
                "head weakspot 2x");
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

    private static void testRefusals() {
        assertThrows(() -> BeastModel.load(null), "E_MODEL_GEO:null");
        assertThrows(() -> BeastModel.load("tools/live/no-such-beast.geo.json"),
                "E_MODEL_GEO:unreadable");
        assertThrows(() -> BeastModel.load("../example1/content/owned.matou"),
                "E_MODEL_JSON:syntax");
    }
}
