package cpw.mods.fml.relauncher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Forge compile stub: shape-only Forge API used by {@code forge/}
 * sources. Never runs (compile classpath only). Marks the beast renderer
 * mapping client-only so dedicated servers strip it at load instead of
 * resolving client classes — drift fails loudly.
 *
 * Retention mirrors the pinned universal bytes (RUNTIME + TYPE / FIELD /
 * METHOD / CONSTRUCTOR, verified by javap on the 1614 universal): without
 * it javac files the annotation invisible, Forge 1.7.10 strips visible
 * annotations only, the method survives on dedicated servers and mod load
 * dies resolving client classes (NoClassDefFoundError: ModelPig, found
 * live on T4 bytes). tools/run-live.sh locks the visibility on the exact
 * compiled bytes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD,
        ElementType.CONSTRUCTOR})
public @interface SideOnly {
    Side value();
}
