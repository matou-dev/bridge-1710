package cpw.mods.fml.common.gameevent;

import cpw.mods.fml.relauncher.Side;
import net.minecraft.world.World;

/**
 * B3 compile stub, never runs (see Mod.java). Mirrors the 1614 shape:
 * side/phase live on the TickEvent parent, world on WorldTickEvent.
 */
public class TickEvent {
    public enum Type {
        WORLD, PLAYER, CLIENT, SERVER, RENDER
    }

    public enum Phase {
        START, END
    }

    public final Type type;
    public final Side side;
    public final Phase phase;

    public TickEvent(Type type, Side side, Phase phase) {
        this.type = type;
        this.side = side;
        this.phase = phase;
    }

    public static class WorldTickEvent extends TickEvent {
        public final World world;

        public WorldTickEvent(Side side, Phase phase, World world) {
            super(Type.WORLD, side, phase);
            this.world = world;
        }
    }
}
