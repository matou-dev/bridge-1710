package net.minecraft.network;

/**
 * B3 compile stub: shape-only vanilla server net handler for the
 * dev-only autoplay combat leg (tools/autoplay/, never shipped). Only
 * {@code setPlayerLocation} rides the reobf map (same searge
 * {@code func_147364_a (DDDFF)V} vanilla /tp calls, pinned by
 * tools/autoplay/want.txt). Never runs (compile classpath only).
 */
public class NetHandlerPlayServer {
    public void setPlayerLocation(double x, double y, double z, float yaw,
            float pitch) {
    }
}
