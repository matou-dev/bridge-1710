package net.minecraft.entity.passive;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only vanilla pig used by {@code forge/} sources.
 * Never runs (compile classpath only). Serves the spawn landing in forge/
 * (MatouBridgeMod lands budgeted beasts as vanilla pigs carrying our loot
 * table — tranche 1, hub decisions/SPAWN.md) and the dev-only companion
 * proofs (tools/autoplay/, never shipped — both stub trees compile as one
 * unit, so this single shape serves both). Class names are identical
 * in searge and MCP, so no mapping is ever needed — same practice as
 * bridge-1122. Drift fails loudly on the owning side.
 */
public class EntityPig extends EntityLivingBase {
    public EntityPig(World world) {
    }
}
