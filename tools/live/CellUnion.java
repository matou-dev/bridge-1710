import fr.iamacat.bridge.ForgeContent;
import fr.iamacat.bridge.Packs;
import fr.iamacat.bridge.Packs.PackSpec;
import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.ContentPack;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** B3 probe: union of decided cells over ticks 0..T (pure, no MC). */
public final class CellUnion {
    public static void main(String[] a) throws Exception {
        String cfg = a[0];
        int ticks = Integer.parseInt(a[1]);
        String out = a[2];
        List<String> lines = Files.readAllLines(Paths.get(cfg),
                StandardCharsets.UTF_8);
        List<PackSpec> specs = Packs.parseLines(lines);
        if (specs.size() != 1) {
            throw new IllegalArgumentException(
                    "E_UNION_WIRE:count <" + specs.size() + "> (want 1)");
        }
        Map<String, String> args =
                new LinkedHashMap<String, String>(specs.get(0).args);
        ContentPack pack = Packs.load(specs.get(0).className);
        if (pack instanceof ConfigurablePack) {
            ((ConfigurablePack) pack).configure(args);
        }
        Map<String, Integer> seen = new HashMap<String, Integer>();
        for (int t = 0; t < ticks; t++) {
            List<String> cells = ForgeContent.decideAll(pack, t);
            for (String c : cells) {
                if (!seen.containsKey(c)) {
                    seen.put(c, t);
                }
            }
        }
        FileWriter w = new FileWriter(out);
        for (Map.Entry<String, Integer> e : seen.entrySet()) {
            w.write(e.getKey() + " " + e.getValue() + "\n");
        }
        w.close();
        System.out.println("ok union : " + seen.size() + " cells over " + ticks + " ticks");
    }
}
