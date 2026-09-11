package fr.iamacat.bridge.forge;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import fr.iamacat.bridge.Packs;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.model.ModelPig;
import net.minecraft.client.renderer.entity.RenderPig;
import net.minecraft.item.Item;

/**
 * Registration half of example1 (see hub decisions/REGISTRATION.md):
 * a second mod in the bridge jar under example1's own frozen modid
 * ({@code NAMES.md}, never a rename target). 1.7.10 FML prefixes every
 * {@code GameRegistry} name with the ACTIVE mod container (measured:
 * registering {@code example1:my_ore} from {@code matoubridge} lands
 * {@code matoubridge:example1:my_ore} with an "Illegal extra prefix"
 * warning), so the container owning the {@code example1:} prefix must
 * be the one registering. The same preInit registers the generic
 * beast (hub decisions/SPAWN.md, custom entity tranche) from the sealed
 * spawn-table mob list — one generic registration covers every sealed
 * mob (the NBT identity distinguishes them at runtime, hub
 * decisions/VIRTUAL_HITBOXES.md second-beast row), pig shape and
 * renderer reused, vanilla pigs never carry our census anymore. FML runs
 * every mod's preInit before any init, so names registered here always
 * precede {@link MatouBridgeMod} init-time binds, whatever the mod
 * order. New refusals stay registration-local ({@code E_REG_*}, never
 * in the {@code E_FORGE_*} parity catalog). Only this package may import
 * {@code net.minecraft} / {@code cpw.mods}.
 */
@Mod(modid = Example1Mod.MODID, name = "MatouExample1", version = "1.2.0",
        acceptableRemoteVersions = "*")
public final class Example1Mod {
    public static final String MODID = "example1";
    static final String PACKS_PATH = "config/matoubridge/packs.cfg";
    /**
     * Entity tranche: mod-local beast id (one generic beast, never one
     * per content — a second id would be a second entity).
     */
    static final int ENTITY_BEAST_ID = 0;
    /**
     * Entity tranche: pig-like tracking (range, update ticks, velocity).
     * Constants, never defaults: the proof watches beasts move and fall
     * like the pigs they replace.
     */
    static final int ENTITY_TRACKING_RANGE = 64;
    static final int ENTITY_UPDATE_TICKS = 1;
    static final boolean ENTITY_SENDS_VELOCITY = true;

    private final Map<String, Block> registered =
            new HashMap<String, Block>();
    private final Map<String, Item> registeredItems =
            new HashMap<String, Item>();
    private String registeredEntity;
    private List<String> registeredMobs;

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
        List<Packs.PackSpec> specs = Packs.parseLines(lines);
        for (Packs.PackSpec spec : specs) {
            registerCustom(spec);
        }
        registerItems(specs);
        registerBeast(specs);
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
        for (Map.Entry<String, Item> e : registeredItems.entrySet()) {
            int colon = e.getKey().indexOf(':');
            String shortName = e.getKey().substring(colon + 1);
            if (GameRegistry.findItem(MODID, shortName) != e.getValue()) {
                throw new IllegalStateException(
                        "E_REG_ITEM:unresolved <" + e.getKey() + ">");
            }
            System.out.println("[MatouBridge] registered-item <" + e.getKey()
                    + "> id " + Item.getIdFromItem(e.getValue()));
        }
        if (registeredEntity != null) {
            if (EntityRegistry.instance().lookupModSpawn(
                    MatouEntity.class, true) == null) {
                throw new IllegalStateException(
                        "E_REG_BEAST:unregistered <"
                                + registeredEntity + ">");
            }
            System.out.println("[MatouBridge] registered-entity <"
                    + join(registeredMobs) + ">");
            if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
                registerBeastRenderer();
            }
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
     * Entity registration: every sealed mob ref from the same owned content
     * the loot table and the spawn wire came from (parsed once, like
     * blocks — never on the tick path). One generic registration covers
     * every sealed mob: the entity name rides the first sealed mob's
     * short name (file order, so single-mob tables register
     * byte-identical bytes), and the per-mob census/dispatch reads each
     * beast's NBT identity — a second mod-local id would be a second
     * entity (see {@code ENTITY_BEAST_ID}). No owned file anywhere means
     * no beast (Q1 cohabitation), same passivity as the spawn wire.
     * Several distinct owned files refuse loudly — silent table picks are
     * defaults. The short mob name registers (no container prefixing on
     * the entity path to warn about — the name rides verbatim).
     */
    private void registerBeast(List<Packs.PackSpec> specs) {
        Set<String> owned = new HashSet<String>();
        for (Packs.PackSpec spec : specs) {
            String path = spec.args.get("ownedFile");
            if (path != null) {
                owned.add(path);
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        if (owned.size() > 1) {
            throw new IllegalArgumentException("E_REG_TABLE:multi <"
                    + owned + "> (one beast table per bridge)");
        }
        String ownedFile = owned.iterator().next();
        List<String> mobs = loadMobRefs(ownedFile);
        String first = mobs.get(0);
        int colon = first.indexOf(':');
        String shortName = first.substring(colon + 1);
        registeredEntity = first;
        registeredMobs = mobs;
        try {
            EntityRegistry.registerModEntity(MatouEntity.class, shortName,
                    ENTITY_BEAST_ID, this, ENTITY_TRACKING_RANGE,
                    ENTITY_UPDATE_TICKS, ENTITY_SENDS_VELOCITY);
        } catch (Exception e) {
            throw new IllegalArgumentException("E_REG_BEAST:refused <"
                    + first + "> (" + e.getMessage() + ")", e);
        }
    }

    private void registerItems(List<Packs.PackSpec> specs) {
        Set<String> owned = new HashSet<String>();
        for (Packs.PackSpec spec : specs) {
            String path = spec.args.get("ownedFile");
            if (path != null) {
                owned.add(path);
            }
        }
        for (String ownedFile : owned) {
            for (Object o : loadItemSpecs(ownedFile)) {
                String shortName = (String) specField(o, "name", ownedFile);
                String want = MODID + ":" + shortName;
                if (registeredItems.containsKey(want)) {
                    continue;
                }
                if (GameRegistry.findItem(MODID, shortName) != null) {
                    throw new IllegalArgumentException(
                            "E_REG_ITEM:already registered <" + want + ">");
                }
                Object s = specField(o, "stack", ownedFile);
                if (!(s instanceof Integer)) {
                    throw new IllegalArgumentException(
                            "E_REG_SPEC:shape <" + ownedFile + "> (bad stack type)");
                }
                int stack = ((Integer) s).intValue();
                Item item = new MatouItem(shortName, stack);
                try {
                    GameRegistry.registerItem(item, shortName);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "E_REG_ITEM:refused <" + want + "> ("
                                    + e.getMessage() + ")", e);
                }
                registeredItems.put(want, item);
            }
        }
    }

    /**
     * Client-only renderer mapping: the generic beast reuses the vanilla
     * pig renderer until the custom-renderer tranche (main model plus the
     * saddle pass plus shadow, same values as the vanilla bootstrap).
     * Stripped on the server ({@code @SideOnly}), so dedicated servers
     * never resolve the client classes — a missing mapping would die
     * loudly on the client instead (null renderer at first tracked
     * spawn).
     *
     * <p>Model tranche (hub decisions/MATOU_MODEL.md, ported from 1122):
     * the instanced overlay draws the SPI-baked beast mesh on top — same
     * two-renderer shape as 1122 (pig renderer kept, instanced overlay
     * added), version-native bus call below.
     */
    @SideOnly(Side.CLIENT)
    private static void registerBeastRenderer() {
        RenderingRegistry.registerEntityRenderingHandler(
                MatouEntity.class,
                new RenderPig(new ModelPig(), new ModelPig(0.5F), 0.7F));
        InstancedMeshRenderer.initClient();
    }

    /**
     * Content mob refs, reached reflectively: the bridge stays content-blind
     * at build time (Q2 — same rule as {@code loadSpecs}). Every sealed
     * mob enumerates in file order through {@code SpawnTable.mobRefs} —
     * zero mobs already refuse there, never a quiet pick here. Every
     * failure is coded E_REG_*, never a silent default.
     */
    private static List<String> loadMobRefs(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.SpawnTable");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("E_REG_BEAST:missing "
                    + "example1 for <" + ownedFile + "> ("
                    + e.getMessage() + ")", e);
        }
        final Method fromFile;
        try {
            fromFile = cls.getMethod("fromFile", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape "
                    + "<fr.iamacat.example1.SpawnTable> ("
                    + e.getMessage() + ")", e);
        }
        try {
            Object table = fromFile.invoke(null, ownedFile);
            Object refs = table.getClass().getMethod("mobRefs")
                    .invoke(table);
            if (!(refs instanceof List) || ((List<?>) refs).isEmpty()) {
                throw new IllegalStateException("E_REG_BEAST:shape "
                        + "<fromFile> (want non-empty qualified mob list)");
            }
            List<String> out = new ArrayList<String>();
            for (Object o : (List<?>) refs) {
                if (!(o instanceof String) || ((String) o).isEmpty()
                        || ((String) o).indexOf(':') < 0) {
                    throw new IllegalStateException("E_REG_BEAST:shape "
                            + "<fromFile> (want qualified mob refs)");
                }
                out.add((String) o);
            }
            return out;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException("E_REG_BEAST:unreadable <"
                    + ownedFile + "> (" + cause.getMessage() + ")", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape <"
                    + ownedFile + "> (" + e.getMessage() + ")", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("E_REG_BEAST:shape "
                    + "<fr.iamacat.example1.SpawnTable> ("
                    + e.getMessage() + ")", e);
        }
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

    private static List<?> loadItemSpecs(String ownedFile) {
        final Class<?> cls;
        try {
            cls = Class.forName("fr.iamacat.example1.ItemSpec");
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
                    + "<fr.iamacat.example1.ItemSpec> ("
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

    /**
     * Comma join for the multi-mob registration line (same separator as
     * {@code MatouBridgeMod.join} — one convention, not two; a single
     * mob joins to itself, so the single-mob line stays byte-identical).
     */
    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(p);
        }
        return out.toString();
    }
}
