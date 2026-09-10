package net.minecraftforge.event.entity;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.entity.Entity;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 entity-event API
 * (universal jar, never obfuscated). Base of the living events the loot
 * hook reads in forge/ (MatouBridgeMod records dim-0 mob kills, pinned by
 * tools/run-live.sh) plus the join event the spawn veto reads in forge/
 * (MatouBridgeMod refuses host joins past the census cap — {@code entity}
 * pinned by tools/autoplay/universal-pin.txt). Never runs (compile
 * classpath only). Owner discipline: the joined entity is read through
 * this declaring type, never through the subclass.
 */
public class EntityEvent extends Event {
    public Entity entity;
}
