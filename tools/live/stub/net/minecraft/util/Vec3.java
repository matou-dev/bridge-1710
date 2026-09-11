package net.minecraft.util;

/**
 * B3 compile stub: shape-only vanilla look-vector component type. The
 * bridge combat hook reads the attacker look through
 * {@code Entity.getLookVec} and takes its components here — public
 * double fields ({@code field_72450_a/b/c = xCoord/yCoord/zCoord} in
 * srg-mcp.srg, pinned by tools/run-live.sh). 1.7.10 spells the class
 * {@code Vec3} with {@code xCoord} fields (no {@code Vec3d} here).
 * Never runs (compile classpath only, non-final fields per the
 * tools/live/stub convention — finals would fold into prod bytes).
 */
public class Vec3 {
    public double xCoord;
    public double yCoord;
    public double zCoord;
}
