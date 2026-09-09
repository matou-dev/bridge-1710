package fr.iamacat.bridge.forge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import fr.iamacat.bridge.ForgeSnapshot;
import fr.iamacat.bridge.SpiBridge;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.HashMap;
import java.util.Map;

/**
 * B1 Forge wiring (10.13.4.1614): FML server tick in, pure SPI decide,
 * bridge-owned apply. Passive cohabitation (Q1): the B1 demo job decides
 * nothing, so no world edit lands yet; content wiring (example1 cells via
 * {@link WorldCellSink}) follows in B2 on this same seam.
 *
 * <p>Only this package may import {@code net.minecraft} / {@code cpw.mods};
 * the pure gate ({@code tools/check.sh} etage 1) fails otherwise.
 */
@Mod(modid = MatouBridgeMod.MODID, name = "MatouBridge", version = "0.1",
        acceptableRemoteVersions = "*")
public final class MatouBridgeMod {
    public static final String MODID = "matoubridge";

    /** B1 passive job: proves decide runs on tick, applies nothing. */
    static final MatouJob<Object> NOOP = new MatouJob<Object>() {
        public Object decide(Snapshot snap) {
            return null;
        }
    };

    private final SpiBridge bridge = new SpiBridge();
    private long tick;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        Map<MatouId, Object> states = new HashMap<MatouId, Object>();
        states.put(MatouId.parse("matou:tick"), Long.valueOf(tick));
        bridge.tick(ForgeSnapshot.snapshot(tick, states), NOOP);
        tick++;
    }
}
