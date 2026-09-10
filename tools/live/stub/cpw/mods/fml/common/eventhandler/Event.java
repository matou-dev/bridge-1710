package cpw.mods.fml.common.eventhandler;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 event base (universal jar,
 * never obfuscated). Serves the dev-only autoplay spike proof
 * (tools/autoplay/, never shipped), which posts a simulated harvest to the
 * Forge bus, plus the spawn veto in forge/ (MatouBridgeMod cancels host
 * joins past the census cap — {@code setCanceled} pinned by
 * tools/autoplay/universal-pin.txt). Never runs (compile classpath only).
 */
public class Event {
    public void setCanceled(boolean cancel) {
    }

    public boolean isCanceled() {
        return false;
    }
}
