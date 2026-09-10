package cpw.mods.fml.common.registry;

/**
 * Forge compile stub: shape-only Forge API used by {@code forge/}
 * sources. Never runs (compile classpath only). {@code registerModEntity}
 * serves the beast preInit and {@code lookupModSpawn} serves the init-time
 * registration tripwire (both presence-pinned against the provisioned
 * 1614 universal by tools/run-live.sh, measured by javap — the lookup
 * walks the superclass chain and ignores its boolean flag) — drift fails
 * loudly.
 */
public class EntityRegistry {
    public static void registerModEntity(Class entityClass, String name,
            int id, Object mod, int trackingRange, int updateFrequency,
            boolean sendsVelocityUpdates) {
    }

    public static EntityRegistry instance() {
        return null;
    }

    public EntityRegistration lookupModSpawn(Class entityClass,
            boolean ignored) {
        return null;
    }

    public static class EntityRegistration {
    }
}
