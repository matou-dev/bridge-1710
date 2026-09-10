package net.minecraft.client.model;

/**
 * B3 compile stub: shape-only vanilla pig model used by {@code forge/}
 * sources. Never runs (compile classpath only). Serves the beast renderer
 * mapping in forge/ (hub decisions/SPAWN.md, custom entity tranche — both
 * ctors measured: notch {@code bhu} javaps as {@code ()} plus {@code
 * (float)} from the ForgeGradle 1614 cache). Drift fails loudly on the
 * owning side. Client-only: only ever referenced from
 * {@code @SideOnly(CLIENT)} code.
 */
public class ModelPig extends ModelBase {
    public ModelPig() {
    }

    public ModelPig(float scale) {
    }
}
