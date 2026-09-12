package net.minecraftforge.client.event;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.ChunkCache;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 RenderWorldEvent.Pre.
 * Never runs (compile classpath only). 1614 shape is the per-pass
 * chunk-render event {@code Pre(WorldRenderer, ChunkCache, RenderBlocks,
 * int)} (measured via javap on the provisioned universal — the
 * {@code onPreRenderWorld} hook posts it around chunk rendering, while
 * the camera matrices are still active). Pinned by tools/run-live.sh
 * against the universal, same as every Forge member. The instanced
 * overlay captures the GL matrices here (they are dead by
 * RenderWorldLastEvent time on 1614 — measured live) and draws at Last.
 */
public abstract class RenderWorldEvent extends Event {
    public RenderWorldEvent() {
    }

    public static class Pre extends RenderWorldEvent {
        public Pre(WorldRenderer renderer, ChunkCache chunkCache,
                RenderBlocks renderBlocks, int pass) {
        }
    }
}
