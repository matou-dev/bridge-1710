package fr.iamacat.bridge;

import fr.iamacat.spi.ContentPack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B2 reflective pack loading: content packs are discovered by class name
 * from operator config, never by compile edge (Q2 — the bridge stays
 * content-blind at build time). Instantiation needs a public no-arg
 * constructor; packs wanting operator args implement
 * {@code ConfigurablePack}. Pure Java, zero Minecraft. Java 8, zero deps
 * beyond matou-spi.
 */
public final class Packs {
    private Packs() {}

    /**
     * One parsed config line: which pack, where to land it, under which
     * block name, with which operator args. The y range is owned by
     * {@code WorldCellSink} at bind time (single owner, FML side); here y
     * is only shape-checked as an integer.
     */
    public static final class PackSpec {
        public final String className;
        public final int y;
        public final String blockName;
        public final Map<String, String> args;

        PackSpec(String className, int y, String blockName,
                Map<String, String> args) {
            this.className = className;
            this.y = y;
            this.blockName = blockName;
            this.args = Collections.unmodifiableMap(args);
        }
    }

    /**
     * Parses config lines of shape
     * {@code <className> <y> <blockName> [k=v ...]}. Blank lines and
     * {@code #} comments are skipped; anything else malformed is refused
     * loudly, never defaulted.
     */
    public static List<PackSpec> parseLines(List<String> lines) {
        if (lines == null) {
            throw new NullPointerException("E_BRIDGE_PACKS:null lines");
        }
        List<PackSpec> out = new ArrayList<PackSpec>();
        int n = 0;
        for (String raw : lines) {
            n++;
            if (raw == null) {
                throw new NullPointerException(
                        "E_BRIDGE_PACKS:null line " + n);
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            out.add(parseLine(line, n));
        }
        return Collections.unmodifiableList(out);
    }

    private static PackSpec parseLine(String line, int n) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_PACKS:shape <" + line + "> at line " + n
                            + " (want <class> <y> <block> [k=v ...])");
        }
        int y;
        try {
            y = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_PACKS:y <" + tokens[1] + "> at line " + n
                            + " (want int)");
        }
        Map<String, String> args = new LinkedHashMap<String, String>();
        for (int i = 3; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(
                        "E_BRIDGE_PACKS:arg <" + tokens[i] + "> at line "
                                + n + " (want k=v)");
            }
            String key = tokens[i].substring(0, eq);
            if (args.containsKey(key)) {
                throw new IllegalArgumentException(
                        "E_BRIDGE_PACKS:dup key <" + key + "> at line "
                                + n);
            }
            args.put(key, tokens[i].substring(eq + 1));
        }
        return new PackSpec(tokens[0], y, tokens[2], args);
    }

    /**
     * Reflective instantiate by class name. No no-arg constructor, bad
     * type, or missing class: refused loudly with a named error.
     */
    public static ContentPack load(String className) {
        if (className == null) {
            throw new NullPointerException("E_BRIDGE_PACK:null class");
        }
        Class<?> cls;
        try {
            cls = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_PACK:unknown <" + className + ">");
        } catch (LinkageError e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_PACK:unloadable <" + className + "> ("
                            + e.getMessage() + ")");
        }
        if (!ContentPack.class.isAssignableFrom(cls)) {
            throw new IllegalArgumentException("E_BRIDGE_PACK:type <"
                    + className + "> (want ContentPack)");
        }
        try {
            return (ContentPack) cls.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_BRIDGE_PACK:no-noarg <"
                    + className + ">");
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_PACK:unbuildable <" + className + "> ("
                            + e.getMessage() + ")");
        }
    }
}
