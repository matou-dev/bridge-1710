package fr.iamacat.bridge;

import fr.iamacat.bridge.Packs.PackSpec;
import fr.iamacat.example1.AdditiveScatterJob;
import fr.iamacat.example1.ExamplePack;
import fr.iamacat.example1.OwnedVeinJob;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * B2 end-to-end gate (no JUnit): real example1 jobs from source content
 * files, sealed snapshots, merged decisions, landed on a recording fake
 * world. Any violation prints {@code FAIL bridge-content : ...} and exits
 * 1. Run by tools/check.sh against the {@code ../spi} + {@code ../example1}
 * sibling checkouts. Zero Minecraft.
 */
public final class ForgeContentCheck {
    private ForgeContentCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL bridge-content : " + what);
            System.exit(1);
        }
        System.out.println("ok bridge-content : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-content : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL bridge-content : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok bridge-content : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL bridge-content : accepted " + what);
        System.exit(1);
    }

    private static void expectUOE(Runnable r, String what) {
        try {
            r.run();
        } catch (UnsupportedOperationException e) {
            System.out.println("ok bridge-content : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL bridge-content : accepted " + what);
        System.exit(1);
    }

    public static void main(String[] args) {
        // --- reflective loader: content-blind at build time ---
        ContentPack pack = Packs.load("fr.iamacat.example1.ExamplePack");
        check(pack.namespace().equals("example1"), "load example1");
        expectIAE(new Runnable() {
            public void run() {
                Packs.load("java.lang.String");
            }
        }, "non-pack type");
        expectIAE(new Runnable() {
            public void run() {
                Packs.load("fr.iamacat.nope.Nope");
            }
        }, "unknown class");
        expectNPE(new Runnable() {
            public void run() {
                Packs.load(null);
            }
        }, "null class");

        // --- config lines: strict shape, comments skipped ---
        List<PackSpec> specs = Packs.parseLines(Arrays.asList(
                "# example1 wire", "",
                "fr.iamacat.example1.ExamplePack 64 minecraft:stone"
                        + " ownedFile=a scatterFile=b"));
        check(specs.size() == 1, "one wire");
        PackSpec spec = specs.get(0);
        check(spec.className.equals("fr.iamacat.example1.ExamplePack")
                && spec.y == 64
                && spec.blockName.equals("minecraft:stone")
                && spec.args.get("ownedFile").equals("a")
                && spec.args.size() == 2, "wire fields");
        expectIAE(new Runnable() {
            public void run() {
                Packs.parseLines(Collections.singletonList("just a"));
            }
        }, "short line");
        expectIAE(new Runnable() {
            public void run() {
                Packs.parseLines(Collections.singletonList(
                        "fr.iamacat.example1.ExamplePack high minecraft:stone"));
            }
        }, "bad y");
        expectIAE(new Runnable() {
            public void run() {
                Packs.parseLines(Collections.singletonList(
                        "fr.iamacat.example1.ExamplePack 64 minecraft:stone x"));
            }
        }, "bare arg");
        expectIAE(new Runnable() {
            public void run() {
                Packs.parseLines(Collections.singletonList(
                        "fr.iamacat.example1.ExamplePack 64 minecraft:stone"
                                + " k=1 k=2"));
            }
        }, "dup key");
        expectNPE(new Runnable() {
            public void run() {
                Packs.parseLines(null);
            }
        }, "null lines");

        // --- end to end: source files -> jobs -> merge -> fake world ---
        final ExamplePack ex = ExamplePack.fromFiles(
                "../example1/content/owned.matou",
                "../example1/content/additive.matou");
        final List<String> d1 = ForgeContent.decideAll(ex, 7L);
        check(d1.equals(ForgeContent.decideAll(ex, 7L)), "e2e pure");
        Snapshot snap = new Snapshot(7L, ex.states(7L));
        List<String> owned = new OwnedVeinJob().decide(snap);
        List<String> additive = new AdditiveScatterJob().decide(snap);
        check(d1.subList(0, owned.size()).equals(owned),
                "e2e owned verbatim");
        check(ForgeContent.merge(Arrays.asList(owned, additive)).equals(
                AdditiveScatterJob.merge(owned, additive)),
                "merge comparateur");
        check(d1.equals(AdditiveScatterJob.merge(owned, additive)),
                "e2e merge content");
        final List<String> landed = new ArrayList<String>();
        ForgeContent.applyAll(ex, 7L, new CellSink() {
            public void setCell(int x, int z) {
                landed.add(x + "," + z);
            }
        });
        check(landed.equals(d1), "e2e apply verbatim");
        expectNPE(new Runnable() {
            public void run() {
                ForgeContent.decideAll(null, 7L);
            }
        }, "null pack");
        expectNPE(new Runnable() {
            public void run() {
                ForgeContent.applyAll(ex, 7L, null);
            }
        }, "null sink");
        expectNPE(new Runnable() {
            public void run() {
                ForgeContent.merge(null);
            }
        }, "null merge");
        expectIAE(new Runnable() {
            public void run() {
                ForgeContent.decideAll(ex, -1L);
            }
        }, "negative tick");

        // --- wired pack: legacy decisions first, 18 volume cells after ---
        final ExamplePack wired = ExamplePack.fromFiles(
                "../example1/content/owned.matou",
                "../example1/content/additive.matou",
                "../example1/content/structure.matou");
        final List<String> w1 = ForgeContent.decideAll(wired, 7L);
        check(w1.equals(ForgeContent.decideAll(wired, 7L)),
                "wired e2e pure");
        check(w1.subList(0, d1.size()).equals(d1),
                "wired keeps legacy first");
        int vols = 0;
        for (String c : w1) {
            if (c.indexOf(':') >= 0) {
                vols++;
            }
        }
        check(vols == 18, "wired 18 volume cells");
        final List<String> landedW = new ArrayList<String>();
        final CellSink legacySink = new CellSink() {
            public void setCell(int x, int z) {
                landedW.add(x + "," + z);
            }
        };
        expectUOE(new Runnable() {
            public void run() {
                ForgeContent.applyAll(wired, 7L, legacySink);
            }
        }, "2d sink on wired pack");

        System.out.println("ok bridge-content : all");
    }
}
