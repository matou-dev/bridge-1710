package cpw.mods.fml.common;

import cpw.mods.fml.common.eventhandler.EventBus;
import cpw.mods.fml.relauncher.Side;

/** B3 compile stub, never runs (see Mod.java). {@code getSide} serves
 * the beast renderer mapping (client-only registration, presence pinned
 * against the provisioned 1614 universal by tools/run-live.sh) — drift
 * fails loudly. */
public final class FMLCommonHandler {
    public static FMLCommonHandler instance() {
        return null;
    }

    public EventBus bus() {
        return null;
    }

    public Side getSide() {
        return null;
    }
}
