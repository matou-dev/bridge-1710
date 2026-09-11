package net.minecraft.util;

import net.minecraft.entity.Entity;

/**
 * B3 compile stub: shape-only vanilla damage type used as a constructor
 * argument by the dev-only autoplay loot proof (tools/autoplay/, never
 * shipped), which posts a simulated kill with a null source (the bridge
 * hook never reads it). Combat tranche: the bridge combat hook resolves
 * the attacker through {@code getEntity} (same searge
 * {@code func_76346_g ()->Entity} as the 1122 lead's
 * {@code getTrueSource} — the 1.7.10 MCP name, pinned to the 1.7.10 SRG
 * by tools/run-live.sh). Never runs (compile classpath only).
 */
public class DamageSource {
    public Entity getEntity() {
        return null;
    }
}
