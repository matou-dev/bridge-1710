package net.minecraft.entity;

import net.minecraft.entity.ai.attributes.IAttribute;

/**
 * B3 compile stub: shape-only vanilla attribute holder used by the spawn
 * hp seam in forge/ (MatouBridgeMod lands the content hp on
 * {@code maxHealth}, hub decisions/SPAWN.md hp tranche). Never runs
 * (compile classpath only). Pinned to the 1.7.10 SRG by
 * tools/run-live.sh; drift fails loudly on the owning side.
 *
 * <p>NEVER final on a field here: non-final fields read live, final
 * statics with initializers would fold the stub fiction into prod bytes
 * (same rule as the Entity stub).
 */
public class SharedMonsterAttributes {
    public static IAttribute maxHealth;
}
