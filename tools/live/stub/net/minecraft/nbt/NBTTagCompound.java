package net.minecraft.nbt;

/**
 * B3 compile stub: shape-only vanilla string-tag surface used by
 * {@code forge/} sources. Never runs (compile classpath only). Serves the
 * spawn-identity persist in forge/ ({@code MatouEntity} writes/reads one
 * string tag under {@code MatouMob} — second-beast tranche, hub
 * decisions/VIRTUAL_HITBOXES.md; owner discipline, hub
 * decisions/LOOT.md). 1614 {@code NBTTagCompound} declares
 * {@code hasKey} ({@code func_74764_b}), {@code getString}
 * ({@code func_74779_i}) and {@code setString} ({@code func_74778_a}),
 * all public — measured via javap + notch-srg.srg against the pinned
 * bytes, pinned to the 1.7.10 SRG by tools/run-live.sh (same grep
 * discipline as every stub row — a stub the SRG does not know fails
 * loudly there, never a silent default). Class names are identical in
 * searge and MCP, so no mapping is ever needed. Drift fails loudly on
 * the owning side.
 */
public class NBTTagCompound {
    public boolean hasKey(String key) {
        return false;
    }

    public String getString(String key) {
        return "";
    }

    public void setString(String key, String value) {
    }
}
