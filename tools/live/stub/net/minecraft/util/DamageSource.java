package net.minecraft.util;

/**
 * B3 compile stub: shape-only vanilla damage type used as a constructor
 * argument by the dev-only autoplay loot proof (tools/autoplay/, never
 * shipped), which posts a simulated kill with a null source (the bridge
 * hook never reads it). Never runs (compile classpath only). Used as a
 * type only — no member is called, so no pin is owned here (the
 * {@code LivingDropsEvent(} constructor is pinned by
 * tools/autoplay/universal-pin.txt).
 */
public class DamageSource {
}
