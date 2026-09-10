package net.minecraft.entity;

import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

/**
 * B3 compile stub: shape-only vanilla living type used as the
 * living-drop event entity by the loot hook in forge/ (MatouBridgeMod
 * records dim-0 mob kills). Never runs (compile classpath only). Used as
 * a type only — no member is called, so no pin is owned here (the
 * {@code LivingDropsEvent(} constructor is pinned by
 * tools/run-live.sh against the 1614 universal).
 *
 * <p>The spawn hp seam (hub decisions/SPAWN.md hp tranche) lands the
 * content hp through {@code getEntityAttribute} /
 * {@code setBaseValue} plus {@code setHealth}, and reads it back with
 * {@code getMaxHealth} — all pinned to the 1.7.10 SRG by
 * tools/run-live.sh. Inherited vanilla members are always called through
 * this declaring stub type in forge/ and the companion alike (owner
 * discipline, measured live: NoSuchFieldError worldObj) — reobf maps
 * {@code net/minecraft/} owners directly, while a beast-typed call
 * would walk off the in-jar chain and die linking.
 */
public class EntityLivingBase extends Entity {
    public IAttributeInstance getEntityAttribute(
            IAttribute attribute) {
        return null;
    }

    public float getMaxHealth() {
        return 0.0f;
    }

    public void setHealth(float health) {
    }
}
