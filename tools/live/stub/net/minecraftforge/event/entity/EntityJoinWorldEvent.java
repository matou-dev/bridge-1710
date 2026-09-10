package net.minecraftforge.event.entity;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/**
 * B3 compile stub: shape-only Forge 1.7.10-1614 entity-join API
 * (universal jar, never obfuscated). Serves the spawn veto in forge/
 * (MatouBridgeMod refuses host joins past the census cap, pinned by
 * tools/autoplay/universal-pin.txt via hub tools/run-client.sh). The
 * joined entity lives on the {@link EntityEvent} base (measured by javap
 * against the pinned universal — this subclass only adds the world), so
 * the veto reads it through the base. Never runs (compile classpath
 * only) — drift fails loudly.
 */
public class EntityJoinWorldEvent extends EntityEvent {
    public final World world;

    public EntityJoinWorldEvent(Entity entity, World world) {
        this.entity = entity;
        this.world = world;
    }
}
