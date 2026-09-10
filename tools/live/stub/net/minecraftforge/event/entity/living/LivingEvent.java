package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityEvent;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 living-event API
 * (universal jar, never obfuscated). {@code entityLiving} serves the loot
 * kill hook in forge/ (MatouBridgeMod records dim-0 mob kills, pinned by
 * tools/run-live.sh). Never runs (compile classpath only) — drift fails
 * loudly.
 */
public class LivingEvent extends EntityEvent {
    public final EntityLivingBase entityLiving = null;

    public LivingEvent(EntityLivingBase entity) {
    }
}
