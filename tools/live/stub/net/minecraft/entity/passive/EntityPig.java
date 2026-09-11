package net.minecraft.entity.passive;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
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
 *
 * <p>Spawn-identity compile stub (second-beast tranche, hub
 * decisions/VIRTUAL_HITBOXES.md): 1614 {@code EntityPig} declares the
 * concrete persist helpers the bridge beast extends with its short mob
 * name ({@code writeEntityToNBT} is {@code func_70014_b},
 * {@code readEntityFromNBT} is {@code func_70037_a}, both PUBLIC here —
 * measured via javap + notch-srg.srg against the pinned bytes; the
 * {@code protected abstract} pair lives one level up on {@code Entity}
 * and a super call there would emit an unmappable intermediate owner).
 * Pinned to the 1.7.10 SRG by tools/run-live.sh, same grep discipline
 * as every row above. Never runs.
 */
public class EntityPig extends EntityLivingBase {
    public EntityPig(World world) {
    }

    public void writeEntityToNBT(NBTTagCompound compound) {
    }

    public void readEntityFromNBT(NBTTagCompound compound) {
    }
}
