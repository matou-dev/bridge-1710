package fr.iamacat.autoplay;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;

/**
 * Autoplay companion (DEV ONLY, never ships): drives the scripted client
 * proof without a human at the keyboard. On the first client tick it joins
 * the pre-seeded flat world (run-client.sh preseeds saves/&lt;world&gt;,
 * refusing loudly when absent), then counts loaded ticks near spawn and
 * shuts the game down cleanly. World == pure union is judged afterwards
 * by hub tools/verify-client-save.sh — this mod never places a union
 * block, so any foreign block fails loudly there, never here silently.
 *
 * <p>1.7.10 shape (measured, not assumed): the client joins by folder name
 * through {@code launchIntegratedServer(folder, name, null)} — null
 * settings are safe because the pre-seeded level.dat already exists (the
 * integrated server only consults settings when creating a fresh world,
 * same call the Singleplayer screen makes). The searge triple behind the
 * three calls (func_71410_x / func_71371_a / func_71400_g) is pinned by
 * tools/autoplay/want.txt against the sha1-pinned srg-mcp.srg — same
 * searge numbers and descriptors as the 1122-pinned triple.
 *
 * <p>1.7.10 bus (measured, not assumed): tick events flow on the FML bus —
 * {@code FMLCommonHandler.instance().bus().register(this)}, same as the
 * bridge itself (B3 server proof green). Registering on
 * {@code MinecraftForge.EVENT_BUS} — the 1122 companion shape, green there —
 * receives nothing on 1.7.10: the first live run booted 5 mods then sat
 * silent to the 600s watchdog. The FML bus stub already ships in
 * tools/live/stub (compile classpath only).
 *
 * <p>Stop clock: SERVER ticks, not client ticks. The bridge applies on the
 * server thread, and on one JVM the client can out-tick a loaded server
 * (or burn client ticks on the loading screen while no server tick has
 * run yet) — stopping on client ticks under-counts the addressed union
 * (measured: 967/1274 on the first 1122 run). WAIT_SERVER_TICKS overshoots
 * the 4000-tick union on purpose (slow start re-lands the same
 * deterministic cells — the union is a fixed point, same practice as the
 * 1165 WAIT_TICKS). The server handler only counts; the client handler
 * owns the shutdown call (same thread as the proven 1165 quit path), so
 * the counter crossing threads is volatile — explicitly, never by luck.
 * World name follows AUTOPLAY_WORLD (default matou), matching verify.
 *
 * <p>Spike proof (SPIKE=1, DEV ONLY): at SPIKE_MINE_TICK dim-0 ticks
 * the companion places one stone at an isolated coords outside the union
 * slices (8,10,8 — the verdict reads y=63..65 only, so the spike cell can
 * never pollute world == pure union), clears it, and posts that harvest
 * as a {@code BreakEvent} authored by the joined player — then polls the
 * cell back to stone.
 * The harvest is simulated, honestly: placing + clearing plus a bus post
 * exercises the shipped hook ({@code onBreak} reads world, dim and block
 * only) through the live seal ({@code RepopSeal}), the pure
 * {@code RepopJob} and the live sink — that seam is what the spike owns.
 * The post carries the real joined player (the BreakEvent constructor
 * itself reads it via ForgeHooks.canHarvestBlock — null NPEs, measured
 * live); what is NOT re-proven is vanilla firing the event on a genuine
 * player harvest (Forge-owned, shape-pinned in universal-pin.txt). A repop observed
 * before the delay, or never, fails loudly (E_SPIKE_PROOF) and shuts the
 * game down for post-mortem — the save keeps the air hole, the verifier
 * and the y=10 anvil spot-check refuse it. Without SPIKE=1 nothing here
 * runs and the proof is byte-for-byte the proven union run.
 */
@Mod(modid = AutoplayMod.MODID, name = "MatouAutoplay", version = "0.0-dev",
        acceptableRemoteVersions = "*")
public class AutoplayMod {
    public static final String MODID = "matouautoplay";
    static final int WAIT_SERVER_TICKS = 4600;
    static final String WORLD = System.getenv().getOrDefault("AUTOPLAY_WORLD", "matou");
    static final boolean SPIKE = "1".equals(System.getenv("SPIKE"));
    static final int SPIKE_X = 8;
    static final int SPIKE_Y = 10;
    static final int SPIKE_Z = 8;
    static final String SPIKE_BLOCK = "minecraft:stone";
    static final int SPIKE_MINE_TICK = 1000;
    static final int SPIKE_TIMEOUT = 600;

    volatile int serverTicks = 0;
    volatile int worldTicks = 0;
    volatile boolean mined = false;
    volatile boolean repopped = false;
    volatile boolean spikeFailed = false;
    volatile int mineTick = -1;
    volatile int repopTick = -1;
    World world = null;
    boolean foreignNoted = false;
    boolean playerNoted = false;
    boolean joined = false;
    boolean done = false;

    public AutoplayMod() {
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTicks++;
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != Side.SERVER
                || event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!SPIKE) {
            return;
        }
        if (event.world.provider.dimensionId != 0) {
            if (!foreignNoted) {
                foreignNoted = true;
                System.out.println("[MatouAutoplay] note spike-proof : "
                        + "ignoring non-zero-dim world ticks (the integrated "
                        + "server ticks dim 0/1/-1 from boot)");
            }
            return;
        }
        if (world == null) {
            world = event.world;
            System.out.println("[MatouAutoplay] spike armed <"
                    + SPIKE_X + "," + SPIKE_Y + "," + SPIKE_Z + ":"
                    + SPIKE_BLOCK + "> mineAt=" + SPIKE_MINE_TICK
                    + " (SPIKE=1)");
        }
        worldTicks++;
        if (!mined && !spikeFailed && worldTicks >= SPIKE_MINE_TICK) {
            mine();
        } else if (mined && !repopped && !spikeFailed) {
            poll();
        }
    }

    private void fail(String what) {
        spikeFailed = true;
        System.out.println("[MatouAutoplay] FAIL spike-proof : " + what);
    }

    private void mine() {
        // The BreakEvent constructor reads the player
        // (ForgeHooks.canHarvestBlock — null NPEs, measured live), so the
        // harvest is authored by the joined player, not forged from null.
        // Absent player (not joined yet) postpones the mine, loudly once;
        // a player that never shows fails the proof instead of mining
        // authorless. Checked before touching the world: a postponed mine
        // leaves no hole behind.
        if (world.playerEntities == null || world.playerEntities.isEmpty()) {
            if (!playerNoted) {
                playerNoted = true;
                System.out.println("[MatouAutoplay] note spike-proof : "
                        + "player absent at mine tick, postponing");
            }
            if (worldTicks > SPIKE_MINE_TICK + SPIKE_TIMEOUT) {
                fail("player never joined (no harvest author)");
            }
            return;
        }
        Block stone = Block.getBlockFromName(SPIKE_BLOCK);
        if (stone == null) {
            fail("unknown <" + SPIKE_BLOCK + "> (want vanilla stone)");
            return;
        }
        if (!world.isAirBlock(SPIKE_X, SPIKE_Y, SPIKE_Z)) {
            fail("spike cell occupied before place (want air, "
                    + "proof needs an isolated coords)");
            return;
        }
        if (!world.setBlock(SPIKE_X, SPIKE_Y, SPIKE_Z, stone)) {
            fail("place refused (setBlock false at worldTick "
                    + worldTicks + ")");
            return;
        }
        if (world.isAirBlock(SPIKE_X, SPIKE_Y, SPIKE_Z)) {
            fail("place invisible (still air after setBlock)");
            return;
        }
        if (!world.setBlockToAir(SPIKE_X, SPIKE_Y, SPIKE_Z)) {
            fail("clear refused (setBlockToAir false)");
            return;
        }
        if (!world.isAirBlock(SPIKE_X, SPIKE_Y, SPIKE_Z)) {
            fail("clear invisible (not air after setBlockToAir)");
            return;
        }
        // The harvest is authored by the joined player (fetched above —
        // the BreakEvent constructor reads it, null NPEs live).
        EntityPlayer player =
                (EntityPlayer) world.playerEntities.get(0);
        MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(SPIKE_X,
                SPIKE_Y, SPIKE_Z, world, stone, 0, player));
        mined = true;
        mineTick = worldTicks;
        System.out.println("[MatouAutoplay] spike mined <" + SPIKE_X + ","
                + SPIKE_Y + "," + SPIKE_Z + ":" + SPIKE_BLOCK
                + "> at worldTick " + mineTick);
    }

    private void poll() {
        if (!world.isAirBlock(SPIKE_X, SPIKE_Y, SPIKE_Z)) {
            repopped = true;
            repopTick = worldTicks;
            System.out.println("[MatouAutoplay] spike repopped <" + SPIKE_X
                    + "," + SPIKE_Y + "," + SPIKE_Z + ":" + SPIKE_BLOCK
                    + "> at worldTick " + repopTick + " (elapsed "
                    + (repopTick - mineTick) + ", want >= 200)");
        } else if (worldTicks > mineTick + SPIKE_TIMEOUT) {
            fail("timeout (still air " + SPIKE_TIMEOUT
                    + " ticks after mine at worldTick " + mineTick + ")");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!joined) {
            joined = true;
            System.out.println("[MatouAutoplay] joining world <" + WORLD + ">");
            mc.launchIntegratedServer(WORLD, WORLD, null);
            return;
        }
        if (SPIKE && spikeFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] spike FAILED, shutting down");
            mc.shutdown();
            return;
        }
        if (serverTicks >= WAIT_SERVER_TICKS && (!SPIKE || repopped)
                && !done) {
            done = true;
            System.out.println("[MatouAutoplay] done after " + serverTicks + " server ticks, shutting down");
            mc.shutdown();
        }
    }
}
