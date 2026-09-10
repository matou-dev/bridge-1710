package net.minecraft.client.renderer.entity;

/**
 * B3 compile stub: shape-only vanilla renderer base used by {@code forge/}
 * sources. Never runs (compile classpath only). Serves the beast renderer
 * mapping in forge/ (the generic beast reuses the vanilla pig renderer
 * until the custom-renderer tranche — hub decisions/SPAWN.md). Class
 * names are identical in searge and MCP, constructors never remap, so no
 * mapping is ever needed — same practice as bridge-1122. Drift fails
 * loudly on the owning side. Client-only: only ever referenced from
 * {@code @SideOnly(CLIENT)} code.
 */
public class Render {
}
