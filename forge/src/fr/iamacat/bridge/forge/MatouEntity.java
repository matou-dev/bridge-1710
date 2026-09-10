package fr.iamacat.bridge.forge;

import net.minecraft.entity.passive.EntityPig;
import net.minecraft.world.World;

/**
 * Registration landing (see hub decisions/SPAWN.md, custom entity
 * tranche): the one generic Forge beast every content mob registers as.
 * Pig shape, AI and sounds are reused verbatim — the vanilla pig renderer
 * is mapped to this class until the custom-renderer tranche, never a
 * renderer per content. One generic subclass, never one per content,
 * never a shadow field. The census, the join veto, the reconcile poll
 * and the kill hook all match this class: vanilla pigs are a different
 * species now (ignored by the census, never vetoed). Tranche 1 keeps
 * vanilla pig health: the content {@code hp} rides the spec unapplied
 * until the attribute seam lands (re-opener, never a silent default).
 * Only this package may import {@code net.minecraft} /
 * {@code cpw.mods}.
 */
public final class MatouEntity extends EntityPig {
    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands the vanilla shape.
     */
    public MatouEntity(World world) {
        super(world);
    }
}
