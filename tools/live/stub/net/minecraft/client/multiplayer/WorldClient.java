package net.minecraft.client.multiplayer;

import net.minecraft.world.World;

/**
 * B3 compile stub for the 1.7.10 integrated-client world. Never runs
 * (compile classpath only). Exists so the merged Minecraft stub can
 * declare the true theWorld type: the field is WorldClient-typed
 * (srg-mcp.srg SRG {@code field_71441_e}, pinned by tools/run-live.sh —
 * same practice as bridge-1122). Class names are identical in searge
 * and MCP, so no mapping is ever needed. The renderer reads
 * loadedEntityList through the declaring World type, never through this
 * subclass (owner discipline — stubs never ship, the Reobf walk would
 * end at the missing stub and pass the name through to die live).
 */
public class WorldClient extends World {
}
