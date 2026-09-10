package cpw.mods.fml.relauncher;

/**
 * Forge compile stub: shape-only Forge API used by {@code forge/}
 * sources. Never runs (compile classpath only). Marks the beast renderer
 * mapping client-only so dedicated servers strip it at load instead of
 * resolving client classes — drift fails loudly.
 */
public @interface SideOnly {
    Side value();
}
