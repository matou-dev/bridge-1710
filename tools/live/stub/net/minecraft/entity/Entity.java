package net.minecraft.entity;

import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only vanilla entity API used by {@code forge/}
 * sources. Never runs (compile classpath only). {@code worldObj} plus
 * {@code posX/posY/posZ} serve the loot kill hook in forge/
 * (MatouBridgeMod records dim-0 mob kills, pinned to the 1.7.10 SRG by
 * tools/run-live.sh); {@code getEntityId} serves the spawn census in
 * forge/ (MatouBridgeMod records landings under their id, same SRG pin);
 * {@code isDead} serves the dev-only companion spawn proof
 * (tools/autoplay/, never shipped — dead beasts linger in the loaded
 * list, the census counts the living only, pinned by
 * tools/autoplay/want.txt); the type serves the drop sink ({@code spawnEntityInWorld}) and the
 * living-drop event. Drift fails loudly on the owning side.
 *
 * <p>Renderer tranche (hub decisions/MATOU_MODEL.md, ported from 1122):
 * {@code lastTickPosX/Y/Z} plus {@code rotationYaw/Pitch} serve the
 * client-only {@code InstancedMeshRenderer} interpolation (pinned to the
 * 1.7.10 SRG by tools/run-live.sh, same searge practice as every row
 * above — SRG {@code field_70142_S}, {@code field_70137_T},
 * {@code field_70136_U}, {@code field_70177_z}, {@code field_70125_A}).
 *
 * <p>NEVER final on a primitive field here (see the spike note on
 * tools/live/stub/net/minecraftforge/event/world/BlockEvent.java):
 * non-final fields read live, final primitives with initializers would
 * fold the stub fiction into prod bytes.
 */
public class Entity {
    public World worldObj;
    public double posX;
    public double posY;
    public double posZ;
    public double lastTickPosX;
    public double lastTickPosY;
    public double lastTickPosZ;
    public float rotationYaw;
    public float rotationPitch;
    public boolean isDead;
    /**
     * Animation compile stub: 1614 {@code Entity} declares the age
     * counter the skinned renderer and posed hitboxes read for the clip
     * clock ({@code ticksExisted}, pinned to the 1.7.10 SRG by
     * tools/run-live.sh like every other Entity row above). Never runs.
     */
    public int ticksExisted;
    /**
     * Walk-phase compile stub (hub decisions/MATOU_ANIMATION.md,
     * walk-phase driver tranche): 1614 {@code Entity} declares the
     * cumulative walk-distance counters the skinned renderer and posed
     * hitboxes feed to {@code query.modified_distance_moved}
     * ({@code distanceWalkedModified} is {@code field_70140_Q F} and
     * {@code prevDistanceWalkedModified} is {@code field_70141_P F},
     * both measured against the pinned 1614 SRG like every other
     * Entity row in tools/run-live.sh). The renderer interpolates
     * prev-to-cur over partialTicks, the hitboxes read the current
     * tick value. Never runs.
     */
    public float distanceWalkedModified;
    public float prevDistanceWalkedModified;

    public void setPositionAndRotation(double x, double y, double z,
            float yaw, float pitch) {
    }

    public int getEntityId() {
        return 0;
    }

    public void setDead() {
    }

    /**
     * Combat compile stub: 1614 {@code Entity} declares the attacker
     * eye/look surface the bridge combat hook reads through this
     * declaring type (owner discipline — same searge as the 1122 lead:
     * {@code getLookVec} is {@code func_70040_Z ()->Vec3},
     * {@code getEyeHeight} is {@code func_70047_e ()F}, pinned to the
     * 1.7.10 SRG by tools/run-live.sh). Never runs.
     */
    public Vec3 getLookVec() {
        return null;
    }

    public float getEyeHeight() {
        return 0.0f;
    }
}
