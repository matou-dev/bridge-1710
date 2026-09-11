package net.minecraftforge.client.event;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 RenderWorldLastEvent.
 * Never runs (compile classpath only). 1614 shape is a public
 * {@code partialTicks} field (measured via javap on the provisioned
 * universal: {@code public final float partialTicks;} — the 1122
 * {@code getPartialTicks()} getter does not exist here). Pinned by
 * tools/run-live.sh against the universal, same as every Forge member.
 */
public class RenderWorldLastEvent extends Event {
    public final float partialTicks;

    public RenderWorldLastEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }
}
