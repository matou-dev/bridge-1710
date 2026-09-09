import fr.iamacat.bridge.ForgeContent;
import fr.iamacat.example1.ExamplePack;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** B3 probe: union of decided cells over ticks 0..T (pure, no MC). */
public final class CellUnion {
    public static void main(String[] a) throws Exception {
        String owned = a[0];
        String scatter = a[1];
        int ticks = Integer.parseInt(a[2]);
        String out = a[3];
        Map<String, String> args = new HashMap<String, String>();
        args.put("ownedFile", owned);
        args.put("scatterFile", scatter);
        ExamplePack pack = new ExamplePack();
        pack.configure(args);
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
