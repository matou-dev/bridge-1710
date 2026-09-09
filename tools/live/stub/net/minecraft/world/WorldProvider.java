package net.minecraft.world;

/**
 * B3 compile stub: shape-only vanilla API used by {@code forge/} sources.
 * Never runs (compile classpath only). {@code dimensionId} serves forge/
 * (pinned by tools/run-live.sh) plus the spike dim-0 filter (pinned by
 * tools/autoplay/want.txt) — drift fails loudly on the owning side.
 */
public class WorldProvider {
    public int dimensionId;
}
