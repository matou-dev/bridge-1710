package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ModelBase;

/**
 * B3 compile stub: shape-only vanilla pig renderer used by {@code forge/}
 * sources. Never runs (compile classpath only). Serves the beast renderer
 * mapping in forge/ (hub decisions/SPAWN.md, custom entity tranche — the
 * descriptor {@code (ModelBase, ModelBase, float)} is measured, not
 * assumed: notch {@code boo} javaps as {@code (bhr, bhr, float)} from the
 * ForgeGradle 1614 cache, the second model is the saddle pass — a 2-arg
 * shape dies loudly at first tracked spawn, never silently). Drift fails
 * loudly on the owning side. Client-only: only ever referenced from
 * {@code @SideOnly(CLIENT)} code.
 */
public class RenderPig extends Render {
    public RenderPig(ModelBase mainModel, ModelBase saddleModel,
            float shadow) {
    }
}
