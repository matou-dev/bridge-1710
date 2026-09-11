package net.minecraftforge.event.entity.living;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 hurt-event API
 * (universal jar, never obfuscated). 1614 spells hurt as PUBLIC FIELDS
 * ({@code source} + {@code ammount} — the Forge typo mirrored verbatim),
 * the hurt entity on the {@code LivingEvent} base behind
 * {@code entityLiving}; there are no getters (measured via javap against
 * the 1614 universal, pinned by tools/run-live.sh). The bridge combat
 * hook refines the amount through them. Never runs (compile classpath
 * only).
 */
public class LivingHurtEvent extends LivingEvent {
    public final DamageSource source;
    public float ammount;

    public LivingHurtEvent(EntityLivingBase entity, DamageSource source,
            float ammount) {
        super(entity);
        this.source = source;
        this.ammount = ammount;
    }
}
