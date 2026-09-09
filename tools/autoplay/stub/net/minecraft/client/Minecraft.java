package net.minecraft.client;

import net.minecraft.world.WorldSettings;

/**
 * Autoplay compile stub: shape-only 1.7.10 client surface used by the
 * dev-only companion (tools/autoplay/, never shipped, never runs here).
 * Every member below is pinned by tools/autoplay/want.txt against the
 * sha1-pinned srg-mcp.srg (searge era: the runtime itself is searge-named,
 * so no vanilla javap is possible — same practice as the B3 live
 * pin_method checks in tools/run-live.sh). Drift fails loudly, never
 * silently. Same practice as bridge-1122.
 */
public class Minecraft {
    public static Minecraft getMinecraft() {
        return null;
    }

    public void launchIntegratedServer(String folderName, String worldName, WorldSettings worldSettings) {
    }

    public void shutdown() {
    }
}
