package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.util.DamageSource;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 living-drops API
 * (universal jar, never obfuscated). The type serves the loot kill hook
 * in forge/ (MatouBridgeMod records dim-0 mob kills, pinned by
 * tools/run-live.sh); the constructor serves the dev-only autoplay loot
 * proof (tools/autoplay/, never shipped), which posts a simulated kill
 * (pinned by tools/autoplay/universal-pin.txt). Never runs (compile
 * classpath only) — drift fails loudly.
 */
public class LivingDropsEvent extends LivingEvent {
    public final DamageSource source = null;
    public final java.util.ArrayList<EntityItem> drops = null;
    public final int lootingLevel;
    public final boolean recentlyHit;
    public final int specialDropValue;

    public LivingDropsEvent(EntityLivingBase entity, DamageSource source,
            java.util.ArrayList<EntityItem> drops, int lootingLevel,
            boolean recentlyHit, int specialDropValue) {
        super(entity);
        this.lootingLevel = lootingLevel;
        this.recentlyHit = recentlyHit;
        this.specialDropValue = specialDropValue;
    }
}
