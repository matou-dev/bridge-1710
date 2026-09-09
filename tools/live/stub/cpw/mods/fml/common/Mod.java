package cpw.mods.fml.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * B3 compile stub: shape-only Forge API used by {@code forge/} sources.
 * Never runs. Forge classes are never obfuscated, so member names are
 * final; tools/run-live.sh still asserts their presence in the provisioned
 * 1614 universal jar — drift fails loudly.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Mod {
    String modid();

    String name() default "";

    String version() default "";

    String acceptableRemoteVersions() default "";

    @Retention(RetentionPolicy.RUNTIME)
    @interface EventHandler {
    }
}
