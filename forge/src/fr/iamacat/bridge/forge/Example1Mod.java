package fr.iamacat.bridge.forge;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import fr.iamacat.bridge.Packs;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;

/**
 * Registration half of example1 (see hub decisions/REGISTRATION.md):
 * a second mod in the bridge jar under example1's own frozen modid
 * ({@code NAMES.md}, never a rename target). 1.7.10 FML prefixes every
 * {@code GameRegistry} name with the ACTIVE mod container (measured:
 * registering {@code example1:my_ore} from {@code matoubridge} lands
 * {@code matoubridge:example1:my_ore} with an "Illegal extra prefix"
 * warning), so the container owning the {@code example1:} prefix must
 * be the one registering. FML runs every mod's preInit before any
 * init, so names registered here always precede {@link MatouBridgeMod}
 * init-time binds, whatever the mod order. New refusals stay
 * registration-local ({@code E_REG_*}, never in the {@code E_FORGE_*}
 * parity catalog). Only this package may import {@code net.minecraft}
 * / {@code cpw.mods}.
 */
@Mod(modid = Example1Mod.MODID, name = "MatouExample1", version = "1.2.0",
        acceptableRemoteVersions = "*")
public final class Example1Mod {
    public static final String MODID = "example1";
    static final String PACKS_PATH = "config/matoubridge/packs.cfg";

    private final Map<String, Block> registered =
            new HashMap<String, Block>();

    /**
     * Registration: every packs.cfg wire naming a non-vanilla block
     * gets its content {@code BlockSpec} registered here, before any
     * init-time bind resolves it. Vanilla wires skip silently — the
     * bind-time resolve owns them, unchanged. A missing packs.cfg stays
     * passive (Q1 cohabitation), same as the bridge init.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File cfg = new File(PACKS_PATH);
        if (!cfg.isFile()) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(cfg.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("E_REG_PACKS:unreadable <"
                    + PACKS_PATH + "> (" + e.getMessage() + ")", e);
        }
        for (Packs.PackSpec spec : Packs.parseLines(lines)) {
            registerCustom(spec);
        }
    }

    /**
     * Verify + announce: the registry is only reliably queryable once
     * loading reaches init, so the resolve check and the numeric-ID line
     * the verdict greps live here, not in preInit. Binds need no order
     * against this: registration already happened one FML state ago.
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        for (Map.Entry<String, Block> e : registered.entrySet()) {
            if (Block.getBlockFromName(e.getKey()) != e.getValue()) {
                throw new IllegalStateException(
                        "E_REG_UNRESOLVED:registered but unresolvable <"
                                + e.getKey() + ">");
            }
            System.out.println("[MatouBridge] registered <" + e.getKey()
                    + "> id " + Block.getIdFromBlock(e.getValue()));
        }
    }

    private void registerCustom(Packs.PackSpec spec) {
        String want = spec.blockName;
        int colon = want.indexOf(':');
        if (colon < 0 || want.startsWith("minecraft:")) {
            return;
        }
        if (registered.containsKey(want)) {
            return;
        }
        if (Block.getBlockFromName(want) != null) {
            throw new IllegalArgumentException(
                    "E_REG_DUP:already registered <" + want + ">");
        }
        String shortName = want.substring(colon + 1);
        String ownedFile = spec.args.get("ownedFile");
        if (ownedFile == null) {
            throw new IllegalArgumentException("E_REG_NOSPEC:no ownedFile "
                    + "for custom <" + want + "> (operator must point at "
                    + "the content declaring it)");
        }
        float hardness = 0.0f;
        boolean opaque = true;
        boolean found = false;
        for (Object o : loadSpecs(ownedFile)) {
            if (!shortName.equals(specField(o, "name", ownedFile))) {
                continue;
            }
            if (found) {
                throw new IllegalArgumentException("E_REG_SPEC:dup <"
                        + shortName + "> in <" + ownedFile + ">");
            }
            Object h = specField(o, "hardness", ownedFile);
            Object op = specField(o, "opaque", ownedFile);
            if (!(h instanceof Float) || !(op instanceof Boolean)) {
                throw new IllegalArgumentException("E_REG_SPEC:shape <"
                        + ownedFile + "> (bad physics types)");
            }
            hardness = ((Float) h).floatValue();
            opaque = ((Boolean) op).booleanValue();
            found = true;
        }
        if (!found) {
            throw new IllegalArgumentException("E_REG_NOSPEC:no block <"
                    + shortName + "> in <" + ownedFile + "> for <" + want
                    + ">");
        }
        Block ore = new MatouBlock(shortName, hardness, opaque);
        // Short name on purpose: GameRegistry.registerBlock warns on any
        // qualified name ("Illegal extra prefix") even when the prefix is
        // already ours, then addPrefix keeps it verbatim — short in,
        // example1:my_ore out, zero warnings. Measured live.
        try {
            GameRegistry.registerBlock(ore, shortName);
        } catch (Exception e) {
            throw new IllegalArgumentException("E_REG_BLOCK:refused <"
                    + want + "> (" + e.getMessage() + ")", e);
        }
        registered.put(want, ore);
    }

    /**
     * Content specs, reached reflectively: the bridge stays content-blind
     * at build time (Q2 — same rule as {@code Packs.load}). Every
     * failure is coded E_REG_*, never a silent default.
     */
    private static List<?> loadSpecs(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.BlockSpec");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("E_REG_SPEC:missing "
                    + "example1 for <" + ownedFile + "> ("
                    + e.getMessage() + ")", e);
        }
        final Method fromFile;
        try {
            fromFile = cls.getMethod("fromFile", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape "
                    + "<fr.iamacat.example1.BlockSpec> ("
                    + e.getMessage() + ")", e);
        }
        try {
            Object out = fromFile.invoke(null, ownedFile);
            if (!(out instanceof List)) {
                throw new IllegalStateException("E_REG_SPEC:shape "
                        + "<fromFile> (want List)");
            }
            return (List<?>) out;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("E_REG_SPEC:unreadable <"
                    + ownedFile + "> (" + cause.getMessage() + ")", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape <"
                    + ownedFile + "> (" + e.getMessage() + ")", e);
        }
    }

    private static Object specField(Object spec, String getter,
            String ownedFile) {
        try {
            return spec.getClass().getMethod(getter).invoke(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("E_REG_SPEC:shape <"
                    + ownedFile + "> (no " + getter + ")", e);
        }
    }
}
