package net.minecraft.entity;

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
    public boolean isDead;

    public void setPositionAndRotation(double x, double y, double z,
            float yaw, float pitch) {
    }

    public int getEntityId() {
        return 0;
    }

    public void setDead() {
    }
}
