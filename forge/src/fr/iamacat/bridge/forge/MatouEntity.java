package fr.iamacat.bridge.forge;

import fr.iamacat.bridge.model.BeastAnimation;
import fr.iamacat.bridge.model.BeastModel;
import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.Hittable;
import fr.iamacat.spi.model.MatouAnimation;
import fr.iamacat.spi.model.Molang;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Registration landing (see hub decisions/SPAWN.md, custom entity
 * tranche): the one generic Forge beast every content mob registers as.
 * Pig shape, AI and sounds are reused verbatim — the vanilla pig renderer
 * is mapped to this class until the custom-renderer tranche, never a
 * renderer per content. One generic subclass, never one per content,
 * never a shadow field. The census, the join veto, the reconcile poll
 * and the kill hook all match this class: vanilla pigs are a different
 * species now (ignored by the census, never vetoed). The content
 * {@code hp} lands on the beast's max-health attribute at every landing
 * ({@code MatouBridgeMod.landBeast}, hp tranche — read back tripwired,
 * never a silent default); the vanilla pig renderer mapping stays until
 * the custom-renderer tranche.
 *   * <p>Model tranche (hub decisions/MATOU_MODEL.md): the beast is a
 * {@code Hittable} over the shipped {@code my_beast.geo.json} shape —
 * world-space bone boxes ride the entity origin (feet), the head stays
 * the 2x weakspot. The combat tranche (hub
 * decisions/VIRTUAL_HITBOXES.md) reads them through
 * {@code MatouBridgeMod.onHurt}; this only serves the authoritative
 * shape both sides ray-test.
 *
 * <p>Mob identity (per-mob tranche, hub
 * {@code decisions/VIRTUAL_HITBOXES.md}): every beast carries its short
 * content mob name ({@code my_beast} — the combat-table key, never the
 * qualified cell ref), set by the landing before the spawn and persisted
 * through NBT. Entities without a stored tag (legacy saves, natural
 * paths) adopt the first sealed combat mob on first read with a one-line
 * note — that preserves the current single-mob behaviour for naturals,
 * never silently. Hit-time reads dispatch per mob through
 * {@link #hitWeakspots()}; the inherited {@code Hittable} default
 * {@code weakspotMultiplier} already resolves through it, so no override
 * duplicates that rule here.
 *
 * <p>1.7.10 shape: same superclass as the lead bridge
 * ({@code EntityPig}, {@code (World)} ctor — the spawn seam already lands
 * vanilla pigs through it, live-proven). Registration and rendering go
 * through the version-native calls in {@code Example1Mod}, never from
 * here. The persist helpers are the Pig-declared
 * {@code writeEntityToNBT}/{@code readEntityFromNBT} (PUBLIC here —
 * measured via javap against the pinned 1614 bytes: {@code wo} declares
 * {@code public b(dh)}/{@code public a(dh)}, notch for
 * {@code EntityPig/func_70014_b}/{@code func_70037_a} per notch-srg.srg —
 * while {@code Entity} keeps them {@code protected abstract}): the
 * {@code super} calls emit the direct-superclass owner
 * {@code EntityPig}, which srg-mcp.srg maps directly (same owner
 * discipline as the 1122 lead, hub
 * decisions/VIRTUAL_HITBOXES.md second-beast row). Only this package may
 * import {@code net.minecraft} / {@code cpw.mods}.
 */
public final class MatouEntity extends EntityPig implements Hittable {
    /** NBT tag carrying the short content mob name. */
    static final String NBT_MOB = "MatouMob";

    /** Short content mob name ({@code my_beast}), null until set. */
    private String mob;

    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands the vanilla shape. The mob
     * identity arrives via {@link #setMob} (landings) or NBT (loads).
     */
    public MatouEntity(World world) {
        super(world);
    }

    /**
     * Seals the short content mob name on this beast (called by the
     * landing before the spawn). Loud on null/empty — an unidentified
     * beast would be a silent census leak. Unknown-at-seal is NOT
     * checked here: the sealed readers ({@code BeastModel}, the spawn
     * seal) refuse unknown mobs loudly at their own choke points, never
     * defaulted.
     */
    public void setMob(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_SPAWN_MOB:null mob "
                    + "(want a sealed short mob name — see "
                    + "BeastModel.combatMobs)");
        }
        if (mob.isEmpty()) {
            throw new IllegalArgumentException("E_SPAWN_MOB:empty mob "
                    + "(want a sealed short mob name — never "
                    + "defaulted)");
        }
        this.mob = mob;
    }

    /** Short content mob name, or null before any set/load/adopt. */
    public String mob() {
        return mob;
    }

    /**
     * Short content mob name, adopting the first sealed combat mob (in
     * seal order) with a one-line note when unset — legacy saves and
     * natural paths keep the current single-mob behaviour, never
     * silently. The adoption memoizes: one line per entity, later reads
     * stay quiet. Loud when combat was never sealed (an unsealed read
     * would be a silent default) — every caller is wire-gated.
     */
    public String mobOrFirst() {
        if (mob == null) {
            mob = BeastModel.combatMobs().iterator().next();
            System.out.println("[MatouBridge] beast adopted mob <"
                    + mob + "> (no stored identity — first sealed mob)");
        }
        return mob;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (mob != null) {
            compound.setString(NBT_MOB, mob);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey(NBT_MOB)) {
            setMob(compound.getString(NBT_MOB));
        }
        // No tag (legacy/natural paths): mob stays null until
        // mobOrFirst() adopts with its note — the load path never
        // refuses, the hook path never defaults silently.
    }

    @Override
    public List<BoneBox> hitBoxes() {
        // Owner discipline (measured live on 1122 combat 2026-09-11:
        // bare posX reads owner MatouEntity, whose reobf walk dies at
        // the vanilla EntityPig link — hub decisions/LOOT.md): inherited
        // vanilla members go through the declaring stub type (Entity),
        // never the beast.
        // Animation tranche (hub decisions/MATOU_ANIMATION.md): the boxes
        // ride the sealed clip pose (head shots meet the turned head),
        // never bind. Clock is the entity age (ticksExisted / 20); the
        // walk-phase driver feeds query.modified_distance_moved from the
        // per-mob distance counter (distanceWalkedModified, current tick
        // value — the shipped walk clip drives its head off life_time
        // and its body off keyframes, so a standing mob poses as before
        // while a walking one phases any dist-driven channel).
        Entity self = this;
        double t = self.ticksExisted / 20.0;
        Molang.Ctx ctx = BeastAnimation.animCtx(t,
                self.distanceWalkedModified);
        MatouAnimation.AnimPose pose = BeastAnimation.poseFor(
                mobOrFirst(), t, ctx);
        return BeastModel.cached().model().placedPosedBoxes(
                self.posX, self.posY, self.posZ, pose);
    }

    @Override
    public Map<String, Float> hitWeakspots() {
        return BeastModel.combatWeakspots(mobOrFirst());
    }
}
