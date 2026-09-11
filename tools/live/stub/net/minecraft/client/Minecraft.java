package net.minecraft.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.WorldSettings;

/**
 * B3 compile stub: shape-only 1.7.10 client surface. Never runs (compile
 * classpath only). The single Minecraft stub — a second one under
 * tools/autoplay/stub would be a duplicate-class error (same trouvaille
 * as the 1122 visual tranche: the companion had its own Minecraft stub).
 * Two consumers share it: the client-only forge renderer needs
 * getMinecraft/theWorld/renderViewEntity (first executed by the direct
 * client proof, loud at runtime until then), the dev-only autoplay
 * companion needs launchIntegratedServer/shutdown (pinned by
 * tools/autoplay/want.txt against the sha1-pinned srg-mcp.srg).
 *
 * <p>Renderer anchors are srg-mcp.srg-derived, never recalled (same
 * derive discipline as the 1122 narrow map): {@code theWorld} is the
 * WorldClient-typed field (SRG {@code field_71441_e}),
 * {@code renderViewEntity} is an EntityLivingBase-typed field (SRG
 * {@code field_71451_h} — a field on 1614, the 1122
 * {@code getRenderViewEntity()} method does not exist here; the
 * EntityLivingBase descriptor is runtime truth measured via javap on the
 * pinned vanilla primary ({@code bao.i} is {@code sv}, and the runtime
 * deobfuscation data maps {@code sv} to EntityLivingBase — an
 * Entity-typed fieldref dies with NoSuchFieldError at the first frame,
 * found live). The renderer narrows it to Entity by cast (every member
 * it reads is declared on Entity, so Reobf hits directly — a
 * LivingBase-owned ref would walk off the stripped stubs and pass
 * through to die linking).
 * {@code getMinecraft} is SRG {@code func_71410_x}. All pinned by
 * tools/run-live.sh. Class names are identical in searge and MCP, so the
 * WorldClient descriptor needs no mapping.
 */
public class Minecraft {
    // True type is WorldClient — a World-typed fieldref would die linking
    // (JVM field resolution matches the descriptor exactly). The renderer
    // reads loadedEntityList through the declaring World type (owner
    // discipline: stubs never ship, a WorldClient-owned ref walks nowhere
    // in Reobf and passes through to die linking live — 1122 fix 6988515).
    public WorldClient theWorld;
    public EntityLivingBase renderViewEntity;

    public static Minecraft getMinecraft() {
        return null;
    }

    public void launchIntegratedServer(String folderName, String worldName, WorldSettings worldSettings) {
    }

    public void shutdown() {
    }
}
