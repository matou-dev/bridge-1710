package net.minecraft.entity.player;

import net.minecraft.network.NetHandlerPlayServer;

/**
 * B3 compile stub: shape-only vanilla server-player type for the
 * dev-only autoplay combat leg (tools/autoplay/, never shipped). Only
 * the {@code playerNetServerHandler} link rides the reobf map (same
 * searge {@code field_71135_a} as vanilla /tp reads, pinned by
 * tools/autoplay/want.txt) — no other member is ever touched. Never
 * runs (compile classpath only).
 */
public class EntityPlayerMP extends EntityPlayer {
    public NetHandlerPlayServer playerNetServerHandler;
}
