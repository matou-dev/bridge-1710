package net.minecraft.entity.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * B3 compile stub: shape-only vanilla player type used as a constructor
 * argument by the dev-only autoplay spike proof (tools/autoplay/, never
 * shipped), which posts a simulated harvest with a null player (the
 * bridge hook never reads it). Combat tranche: the autoplay combat leg
 * drives the genuine vanilla attack path through
 * {@code attackTargetEntityWithCurrentItem} (same searge
 * {@code func_71059_n (Entity)V} as the 1122 lead, pinned by
 * tools/autoplay/want.txt) — hence the living hierarchy below (the leg
 * also reads the attacker eye through the declaring {@code Entity}
 * type). Never runs (compile classpath only).
 */
public class EntityPlayer extends EntityLivingBase {
    public void attackTargetEntityWithCurrentItem(Entity target) {
    }
}
