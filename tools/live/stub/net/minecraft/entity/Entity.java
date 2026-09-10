package net.minecraft.entity;

import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only vanilla entity API used by {@code forge/}
 * sources. Never runs (compile classpath only). {@code worldObj} plus
 * {@code posX/posY/posZ} serve the loot kill hook in forge/
 * (MatouBridgeMod records dim-0 mob kills, pinned to the 1.7.10 SRG by
 * tools/run-live.sh); the type serves the drop sink
 * ({@code spawnEntityInWorld}) and the living-drop event. Drift fails
 * loudly on the owning side.
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

    public void setPositionAndRotation(double x, double y, double z,
            float yaw, float pitch) {
    }

    public void setDead() {
    }
}
