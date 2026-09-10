package fr.iamacat.autoplay;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import fr.iamacat.bridge.forge.MatouEntity;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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
 *
 * <p>Loot proof (LOOT=1, DEV ONLY): at LOOT_HARVEST_TICK dim-0 ticks the
 * companion harvests the registered ore at an isolated coords outside the
 * union slices (8,10,8 — place + clear + a {@code HarvestDropsEvent} post
 * authored by the joined player, spike honesty standard) and, five ticks
 * later, kills a spawned beast at (12,10,8) with a simulated
 * {@code LivingDropsEvent} post — then polls both spots for the
 * diamond carrier the bridge loot sink spawns per due drop. The posts are
 * simulated, honestly: place + clear + bus posts exercise the shipped
 * hooks ({@code onHarvest} reads world, dim and block only;
 * {@code onKill} reads the entity only) through the live seal
 * ({@code LootSeal}), the pure {@code LootJob} and the live sink — that
 * seam is what loot owns. What is NOT re-proven is vanilla firing the
 * events on a genuine harvest/kill (Forge-owned, shape-pinned in
 * universal-pin.txt). A carrier observed late, or never, fails loudly
 * (E_LOOT_PROOF) and shuts the game down for post-mortem. Without LOOT=1
 * nothing here runs and the proof is byte-for-byte the proven union run.
 *
 * <p>Spawn proof (SPAWN=1, DEV ONLY): the bridge itself lands budgeted
 * beasts (SPAWN=1 also arms {@code MatouBridgeMod.spawnTick} — one flag
 * drives both sides, so LOOT=1 runs stay spawn-free and their own beast
 * never meets the cap veto). The companion never spawns here: it polls
 * the loaded beasts the bridge landed up to cap, records the maximum seen
 * (past cap fails loudly — the veto owns that bound), kills the first
 * beast past SPAWN_KILL_TICK with a simulated {@code LivingDropsEvent}
 * post (loot honesty standard — the kill pays through the loot table,
   * proving the spawn-to-loot chain), then polls the diamond carrier at
   * the kill spot. The first living beast's max health is polled once
   * against SPAWN_HP (hp tranche — the bridge applies the content hp
   * per landing; a diverged read-back fails loudly here too). A missing
   * beast, a breached cap, or a missing carrier
   * fails loudly (E_SPAWN_PROOF) and shuts the game down for post-mortem.
 * Without SPAWN=1 nothing here runs and the proof is byte-for-byte the
 * proven union run.
 *
 * <p>Load order (measured live, never assumed): {@code
 * required-after:matoubridge} is load-bearing, not decorative. FML
 * constructs containers file by file with a ModClassLoader that keeps a
 * negative cache (cleared per container for its own ASM class list
 * only), and the HotSpot verifier loads frame-named classes ({@code
 * new}/{@code instanceof}/{@code checkcast} of {@code MatouEntity} all
 * over this companion) at {@code Class.forName} time. Without the
 * dependency this mod constructs first, the beast class misses while
 * its jar is still unsourced, the miss poisons the negative cache, and
 * the bridge then dies with a CNFE for a class sitting in its own jar
 * (first red run: UE matouautoplay + UE matoubridge, UC example1 whose
 * {@code .class} literals never trigger the load). Removing the
 * attribute re-arms that exact crash — loudly, never silently.
 */
@Mod(modid = AutoplayMod.MODID, name = "MatouAutoplay", version = "0.0-dev",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:matoubridge")
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
    static final boolean LOOT = "1".equals(System.getenv("LOOT"));
    static final int LOOT_ORE_X = 8;
    static final int LOOT_ORE_Y = 10;
    static final int LOOT_ORE_Z = 8;
    static final String LOOT_ORE_BLOCK = "example1:my_ore";
    static final int LOOT_BEAST_X = 12;
    static final int LOOT_BEAST_Y = 10;
    static final int LOOT_BEAST_Z = 8;
    static final int LOOT_HARVEST_TICK = 1000;
    static final int LOOT_BEAST_DELAY = 5;
    static final int LOOT_TIMEOUT = 600;
    static final boolean SPAWN = "1".equals(System.getenv("SPAWN"));
    /** Mirrors the content cap ({@code owned.matou mob my_beast cap},
     * transported by the bridge spawn wire): the companion counts
     * beasts, the content owns the bound — a drift here fails the proof
     * loudly instead of asserting a stale cap silently. */
    static final int SPAWN_CAP = 4;
    /** Mirrors the content hp ({@code owned.matou mob my_beast hp} via
     * {@code MatouBridgeMod} spawn wire): the companion polls the landed
     * max health, the bridge owns the value — a drift here fails the
     * proof loudly instead of asserting a stale hp silently. */
    static final float SPAWN_HP = 20.0f;
    static final int SPAWN_KILL_TICK = 1000;
    static final int SPAWN_TIMEOUT = 600;

    volatile int serverTicks = 0;
    volatile int worldTicks = 0;
    volatile boolean mined = false;
    volatile boolean repopped = false;
    volatile boolean spikeFailed = false;
    volatile int mineTick = -1;
    volatile int repopTick = -1;
    volatile int lootOreTick = -1;
    volatile int lootBeastTick = -1;
    volatile boolean oreDropped = false;
    volatile boolean beastDropped = false;
    volatile boolean lootFailed = false;
    volatile int oreDropTick = -1;
    volatile int beastDropTick = -1;
    volatile boolean pigSeen = false;
    volatile boolean hpSeen = false;
    volatile int maxPigs = 0;
    volatile int firstPigTick = -1;
    volatile boolean pigKilled = false;
    volatile int killTick = -1;
    volatile int killX = 0;
    volatile int killY = 0;
    volatile int killZ = 0;
    volatile boolean carrierDropped = false;
    volatile boolean spawnFailed = false;
    volatile int carrierTick = -1;
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
        if (!SPIKE && !LOOT && !SPAWN) {
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
            if (SPIKE) {
                System.out.println("[MatouAutoplay] spike armed <"
                        + SPIKE_X + "," + SPIKE_Y + "," + SPIKE_Z + ":"
                        + SPIKE_BLOCK + "> mineAt=" + SPIKE_MINE_TICK
                        + " (SPIKE=1)");
            }
            if (LOOT) {
                System.out.println("[MatouAutoplay] loot armed <ore "
                        + LOOT_ORE_X + "," + LOOT_ORE_Y + ","
                        + LOOT_ORE_Z + ":" + LOOT_ORE_BLOCK + " + beast "
                        + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                        + LOOT_BEAST_Z + "> harvestAt="
                        + LOOT_HARVEST_TICK + " (LOOT=1)");
            }
            if (SPAWN) {
                System.out.println("[MatouAutoplay] spawn armed <cap="
                        + SPAWN_CAP + "> killAt=" + SPAWN_KILL_TICK
                        + " (SPAWN=1)");
            }
        }
        worldTicks++;
        if (SPIKE) {
            if (!mined && !spikeFailed && worldTicks >= SPIKE_MINE_TICK) {
                mine();
            } else if (mined && !repopped && !spikeFailed) {
                poll();
            }
        }
        if (LOOT && !lootFailed) {
            lootTick();
        }
        if (SPAWN && !spawnFailed) {
            spawnTick();
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

    private void lootFail(String what) {
        lootFailed = true;
        System.out.println("[MatouAutoplay] FAIL loot-proof : " + what);
    }

    private void lootTick() {
        if (lootOreTick < 0 && worldTicks >= LOOT_HARVEST_TICK) {
            lootOre();
        } else if (lootOreTick >= 0 && lootBeastTick < 0
                && worldTicks >= lootOreTick + LOOT_BEAST_DELAY) {
            lootBeast();
        }
        if ((lootOreTick >= 0 && !oreDropped)
                || (lootBeastTick >= 0 && !beastDropped)) {
            lootPoll();
        }
        if (!(oreDropped && beastDropped)
                && worldTicks > LOOT_HARVEST_TICK + LOOT_TIMEOUT) {
            lootFail("timeout (oreDropped=" + oreDropped + " beastDropped="
                    + beastDropped + " " + LOOT_TIMEOUT
                    + " ticks after harvest at worldTick "
                    + LOOT_HARVEST_TICK + ")");
        }
    }

    /**
     * Joined player or null (postponed, loudly once): both simulated
     * events are authored by the joined player — the harvest-drops post
     * carries it like the spike break post, and an authorless kill proves
     * nothing. Checked before touching the world.
     */
    private EntityPlayer lootPlayer() {
        if (world.playerEntities == null
                || world.playerEntities.isEmpty()) {
            if (!playerNoted) {
                playerNoted = true;
                System.out.println("[MatouAutoplay] note loot-proof : "
                        + "player absent at harvest tick, postponing");
            }
            return null;
        }
        return (EntityPlayer) world.playerEntities.get(0);
    }

    private void lootOre() {
        EntityPlayer player = lootPlayer();
        if (player == null) {
            return;
        }
        Block ore = Block.getBlockFromName(LOOT_ORE_BLOCK);
        if (ore == null) {
            lootFail("unknown <" + LOOT_ORE_BLOCK + "> (want registered ore)");
            return;
        }
        if (!world.isAirBlock(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z)) {
            lootFail("loot cell occupied before place (want air)");
            return;
        }
        if (!world.setBlock(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z, ore)) {
            lootFail("place refused (setBlock false at worldTick "
                    + worldTicks + ")");
            return;
        }
        if (world.isAirBlock(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z)) {
            lootFail("place invisible (still air after setBlock)");
            return;
        }
        if (!world.setBlockToAir(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z)) {
            lootFail("clear refused (setBlockToAir false)");
            return;
        }
        if (!world.isAirBlock(LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z)) {
            lootFail("clear invisible (not air after setBlockToAir)");
            return;
        }
        MinecraftForge.EVENT_BUS.post(new BlockEvent.HarvestDropsEvent(
                LOOT_ORE_X, LOOT_ORE_Y, LOOT_ORE_Z, world, ore, 0, 0, 1.0f,
                new ArrayList<ItemStack>(), player, false));
        lootOreTick = worldTicks;
        System.out.println("[MatouAutoplay] loot ore harvested <"
                + LOOT_ORE_X + "," + LOOT_ORE_Y + "," + LOOT_ORE_Z + ":"
                + LOOT_ORE_BLOCK + "> at worldTick " + lootOreTick);
    }

    private void lootBeast() {
        EntityPlayer player = lootPlayer();
        if (player == null) {
            return;
        }
        // Custom entity tranche (hub decisions/SPAWN.md): the loot kill
        // lands on the registered beast, not a vanilla pig — every kill
        // still pays the single table entry (per-mob filtering stays a
        // re-opener).
        MatouEntity beast = new MatouEntity(world);
        // Owner discipline (measured live: reobf only walks in-jar
        // superclass chains, stub supertypes never ship): inherited calls
        // go through the declaring stub type, never the beast.
        Entity body = beast;
        body.setPositionAndRotation(LOOT_BEAST_X + 0.5, LOOT_BEAST_Y,
                LOOT_BEAST_Z + 0.5, 0.0f, 0.0f);
        if (!world.spawnEntityInWorld(beast)) {
            lootFail("beast spawn refused at worldTick " + worldTicks);
            return;
        }
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(beast, null,
                new ArrayList<EntityItem>(), 0, true, 0));
        body.setDead();
        lootBeastTick = worldTicks;
        System.out.println("[MatouAutoplay] loot beast killed <"
                + LOOT_BEAST_X + "," + LOOT_BEAST_Y + ","
                + LOOT_BEAST_Z + ":my_beast> at worldTick "
                + lootBeastTick);
    }

    private void lootPoll() {
        if (world.loadedEntityList == null) {
            return;
        }
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof EntityItem)) {
                continue;
            }
            EntityItem e = (EntityItem) o;
            ItemStack stack = e.getEntityItem();
            if (stack == null || stack.getItem() != Items.diamond) {
                continue;
            }
            if (!oreDropped && near(e, LOOT_ORE_X, LOOT_ORE_Y,
                    LOOT_ORE_Z)) {
                oreDropped = true;
                oreDropTick = worldTicks;
                System.out.println("[MatouAutoplay] loot ore dropped "
                        + "<diamond> at worldTick " + oreDropTick
                        + " (elapsed " + (oreDropTick - lootOreTick)
                        + ", want immediate)");
            }
            if (!beastDropped && near(e, LOOT_BEAST_X, LOOT_BEAST_Y,
                    LOOT_BEAST_Z)) {
                beastDropped = true;
                beastDropTick = worldTicks;
                System.out.println("[MatouAutoplay] loot beast dropped "
                        + "<diamond> at worldTick " + beastDropTick
                        + " (elapsed " + (beastDropTick - lootBeastTick)
                        + ", want immediate)");
            }
        }
    }

    private void spawnFail(String what) {
        spawnFailed = true;
        System.out.println("[MatouAutoplay] FAIL spawn-proof : " + what);
    }

    /**
     * Spawn proof tick: count the bridge-landed beasts (cap bound owned by
     * the bridge veto — past cap fails here), kill the first beast past
     * the kill tick through the loot seam, poll the carrier at the kill
     * spot. The companion never spawns: every beast here was decided by
     * the pure SpawnJob and landed by the bridge sink. Vanilla pigs are a
     * different species (ignored — counting them would breach a cap that
     * is not theirs).
     */
    private void spawnTick() {
        if (world.loadedEntityList == null) {
            return;
        }
        int pigs = 0;
        MatouEntity first = null;
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof MatouEntity)) {
                continue;
            }
            // Owner discipline (measured live on loot): inherited vanilla
            // members go through the declaring stub type, never the beast.
            // Dead beasts linger in the loaded list (measured: a corpse
            // counted past cap at worldTick 51) — the census counts the
            // living only, like the bridge release on the kill hook.
            Entity body = (MatouEntity) o;
            if (body.isDead) {
                continue;
            }
            pigs++;
            if (first == null) {
                first = (MatouEntity) o;
            }
        }
        if (pigs > maxPigs) {
            maxPigs = pigs;
            System.out.println("[MatouAutoplay] spawn census <" + pigs
                    + "> at worldTick " + worldTicks);
        }
        if (!pigSeen && pigs > 0) {
            pigSeen = true;
            firstPigTick = worldTicks;
            System.out.println("[MatouAutoplay] spawn first beast at "
                    + "worldTick " + firstPigTick);
        }
        if (!hpSeen && first != null) {
            // Owner discipline (measured live on loot): inherited vanilla
            // members go through the declaring stub type, never the beast.
            EntityLivingBase living = first;
            float hp = living.getMaxHealth();
            if (hp != SPAWN_HP) {
                spawnFail("hp diverged <want=" + SPAWN_HP + " got=" + hp
                        + "> at worldTick " + worldTicks);
                return;
            }
            hpSeen = true;
            System.out.println("[MatouAutoplay] spawn hp <" + hp
                    + "> at worldTick " + worldTicks);
        }
        if (pigs > SPAWN_CAP) {
            spawnFail("cap breached <" + pigs + " > " + SPAWN_CAP
                    + "> at worldTick " + worldTicks);
            return;
        }
        if (!pigKilled && pigSeen && first != null
                && worldTicks >= SPAWN_KILL_TICK) {
            spawnKill(first);
        }
        if (pigKilled && !carrierDropped) {
            spawnPoll();
        }
        if (!pigSeen && worldTicks > SPAWN_KILL_TICK + SPAWN_TIMEOUT) {
            spawnFail("timeout (no bridge beast " + SPAWN_TIMEOUT
                    + " ticks after kill tick " + SPAWN_KILL_TICK + ")");
        } else if (pigKilled && !carrierDropped
                && worldTicks > killTick + SPAWN_TIMEOUT) {
            spawnFail("timeout (no carrier " + SPAWN_TIMEOUT
                    + " ticks after kill at worldTick " + killTick + ")");
        }
    }

    private void spawnKill(MatouEntity beast) {
        // Owner discipline (measured live on loot): inherited vanilla
        // members go through the declaring stub type, never the beast.
        Entity body = beast;
        killX = (int) Math.floor(body.posX);
        killY = (int) Math.floor(body.posY);
        killZ = (int) Math.floor(body.posZ);
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(beast, null,
                new ArrayList<EntityItem>(), 0, true, 0));
        body.setDead();
        pigKilled = true;
        killTick = worldTicks;
        System.out.println("[MatouAutoplay] spawn beast killed <"
                + killX + "," + killY + "," + killZ + ":my_beast> at "
                + "worldTick " + killTick);
    }

    private void spawnPoll() {
        if (world.loadedEntityList == null) {
            return;
        }
        for (Object o : world.loadedEntityList) {
            if (!(o instanceof EntityItem)) {
                continue;
            }
            EntityItem e = (EntityItem) o;
            ItemStack stack = e.getEntityItem();
            if (stack == null || stack.getItem() != Items.diamond) {
                continue;
            }
            if (near(e, killX, killY, killZ)) {
                carrierDropped = true;
                carrierTick = worldTicks;
                System.out.println("[MatouAutoplay] spawn beast dropped "
                        + "<diamond> at worldTick " + carrierTick
                        + " (elapsed " + (carrierTick - killTick)
                        + ", want immediate)");
                return;
            }
        }
    }

    private static boolean near(Entity e, int x, int y, int z) {
        return Math.abs(e.posX - (x + 0.5)) < 3.0
                && Math.abs(e.posY - (y + 0.5)) < 3.0
                && Math.abs(e.posZ - (z + 0.5)) < 3.0;
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
        if (LOOT && lootFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] loot FAILED, shutting down");
            mc.shutdown();
            return;
        }
        if (SPAWN && spawnFailed && !done) {
            done = true;
            System.out.println("[MatouAutoplay] spawn FAILED, shutting down");
            mc.shutdown();
            return;
        }
        if (serverTicks >= WAIT_SERVER_TICKS && (!SPIKE || repopped)
                && (!LOOT || (oreDropped && beastDropped))
                && (!SPAWN || (pigSeen && carrierDropped))
                && !done) {
            done = true;
            System.out.println("[MatouAutoplay] done after " + serverTicks + " server ticks, shutting down");
            mc.shutdown();
        }
    }
}
