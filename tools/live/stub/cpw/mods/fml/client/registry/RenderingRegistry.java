package cpw.mods.fml.client.registry;

import net.minecraft.client.renderer.entity.Render;

/**
 * Forge compile stub: shape-only Forge client API used by {@code forge/}
 * sources. Never runs (compile classpath only). {@code
 * registerEntityRenderingHandler} serves the beast renderer mapping
 * (vanilla pig renderer reused until the custom-renderer tranche,
 * presence-pinned against the provisioned 1614 universal by
 * tools/run-live.sh) — drift fails loudly. Client-only: only ever
 * referenced from {@code @SideOnly(CLIENT)} code.
 */
public class RenderingRegistry {
    public static void registerEntityRenderingHandler(Class entityClass,
            Render renderer) {
    }
}
