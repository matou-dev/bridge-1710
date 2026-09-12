package fr.iamacat.bridge.model;

import fr.iamacat.spi.model.MatouAnimation;
import fr.iamacat.spi.model.Molang;
import fr.iamacat.spi.model.MatouAnimationParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridge-side holder of the beast animation clips (hub
 * decisions/MATOU_ANIMATION.md, consumer tranche): loads the operator
 * {@code my_beast.animation.json} once, serves the parsed clips and the
 * wire-time clip selection per mob to the thin forge call-sites. Zero MC
 * imports — the forge renderer and entity stay thin, the parsing, eval
 * and refusals stay testable here.
 */
public final class BeastAnimation {
    /**
     * Operator animation path, deployed by {@code tools/run-live.sh} next
     * to the geo asset and replaceable by hand (same rule as packs.cfg:
     * the harness never clobbers a hand-tuned file it did not write —
     * the client script copies only when missing).
     */
    public static final String ANIM_PATH = "config/matoubridge/my_beast.animation.json";

    /**
     * Wire-time clip selection (content-driven later, hub
     * decisions/MATOU_ANIMATION.md): mob to clip name, sealed once at
     * wire time beside the combat tables — never on the tick path. One
     * clip per tick per mob today, multi-clip layering is the named
     * follow-up, never an overloaded signature.
     */
    private static volatile Map<String, String> sealedClip;

    /**
     * Seals the per-mob clip selection once at wire time (parse-once,
     * beside the tables — never on the tick path). Loud on null/empty
     * maps or null/empty mob or clip names — an unsealed default would
     * be silent motion. Clip existence rides {@link #clipFor} (the file
     * loads there, never here — the seal stays pure like
     * {@code BeastModel.sealCombat}, and a swapped file missing the
     * sealed clip refuses at first use, never silently).
     */
    public static void sealClip(Map<String, String> perMobClip) {
        if (perMobClip == null) {
            throw new NullPointerException("E_ANIM_WIRE:null "
                    + "clips (want the wire-time mob to clip table)");
        }
        if (perMobClip.isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_WIRE:empty "
                    + "clips (a table nobody plays would be a silent "
                    + "no-op)");
        }
        Map<String, String> sealed = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : perMobClip.entrySet()) {
            String mob = e.getKey();
            String clip = e.getValue();
            if (mob == null || clip == null) {
                throw new NullPointerException("E_ANIM_WIRE:null "
                        + "mob or clip (want sealed names — never "
                        + "defaulted)");
            }
            if (mob.isEmpty() || clip.isEmpty()) {
                throw new IllegalArgumentException("E_ANIM_WIRE:empty "
                        + "mob or clip <" + mob + "/" + clip + "> (never "
                        + "defaulted)");
            }
            sealed.put(mob, clip);
        }
        sealedClip = Collections.unmodifiableMap(sealed);
    }

    /**
     * Selected clip for one mob from an explicit animation file (pure —
     * the gate battery exercises the shipped asset without touching the
     * production path).
     */
    public static MatouAnimation clipFor(BeastAnimation anims, String mob) {
        if (mob == null) {
            throw new NullPointerException("E_ANIM_WIRE:null mob "
                    + "(want a sealed mob — see sealClip)");
        }
        Map<String, String> hit = sealedClip;
        if (hit == null) {
            throw new IllegalStateException("E_ANIM_WIRE:unwired "
                    + "(clip table never sealed — want sealClip)");
        }
        String name = hit.get(mob);
        if (name == null) {
            throw new IllegalArgumentException("E_ANIM_WIRE:unknown "
                    + "mob <" + mob + "> (want one of " + hit.keySet()
                    + " — never defaulted)");
        }
        MatouAnimation clip = anims.clips().get(name);
        if (clip == null) {
            throw new IllegalArgumentException("E_ANIM_WIRE:unknown "
                    + "clip <" + name + "> (want one of "
                    + anims.clips().keySet() + " — never defaulted)");
        }
        return clip;
    }

    /**
     * Selected clip for one mob, or the loud unwired / null / unknown
     * refusal (never a default clip — a swapped file missing the sealed
     * clip refuses here, never silently). The seal reads before the file
     * so an unsealed read refuses unwired even when the file is absent.
     */
    public static MatouAnimation clipFor(String mob) {
        if (mob == null) {
            throw new NullPointerException("E_ANIM_WIRE:null mob "
                    + "(want a sealed mob — see sealClip)");
        }
        if (sealedClip == null) {
            throw new IllegalStateException("E_ANIM_WIRE:unwired "
                    + "(clip table never sealed — want sealClip)");
        }
        return clipFor(cached(), mob);
    }

    /**
     * Evaluated pose for one mob from an explicit animation file (pure).
     */
    public static MatouAnimation.AnimPose poseFor(BeastAnimation anims,
            String mob, double time, Molang.Ctx ctx) {
        return clipFor(anims, mob).evaluate(time, ctx);
    }

    /**
     * Walk-phase eval context (hub decisions/MATOU_ANIMATION.md,
     * walk-phase driver tranche): the clip clock stays the entity age
     * ({@code time} rides both {@code anim_time} and {@code life_time},
     * same as before) while {@code query.modified_distance_moved} reads
     * the caller's per-mob distance ({@code distMoved} in blocks, the
     * vanilla {@code distanceWalkedModified} counter on the entity, or
     * its partialTicks interpolation on the render path). {@code delta}
     * stays the fixed tick step. Pure — the gate battery covers it
     * without MC.
     */
    public static Molang.Ctx animCtx(double time, double distMoved) {
        return new Molang.Ctx(time, time, distMoved, 0.05, null);
    }

    /**
     * Per-frame walk-distance interpolation (render path only): the
     * previous-tick counter eased toward the current one over
     * {@code partialTicks}, same shape as the position interpolation
     * beside it. Pure — hitboxes read the current tick value directly,
     * never through here.
     */
    public static double interpDistMoved(double prev, double cur,
            double partialTicks) {
        return prev + (cur - prev) * partialTicks;
    }

    /**
     * Evaluated pose for one mob at caller time (s) — pure, delegates
     * to the sealed clip (SPI single-clip eval, never multi-clip).
     */
    public static MatouAnimation.AnimPose poseFor(String mob, double time,
            Molang.Ctx ctx) {
        return poseFor(cached(), mob, time, ctx);
    }

    private static volatile BeastAnimation cached;

    private final Map<String, MatouAnimation> clips;

    private BeastAnimation(Map<String, MatouAnimation> clips) {
        this.clips = clips;
    }

    /** Parses the animation file at {@code path}: loud on any failure. */
    public static BeastAnimation load(String path) {
        if (path == null) {
            throw new NullPointerException("E_ANIM_GEO:null path (want an animation file)");
        }
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            throw new IllegalStateException("E_ANIM_GEO:unreadable <"
                    + path + "> (" + e.getMessage() + ")", e);
        }
        Map<String, MatouAnimation> clips = MatouAnimationParser.parse(
                new String(bytes, StandardCharsets.UTF_8));
        return new BeastAnimation(clips);
    }

    /** Process-wide beast animation, loaded once from {@link #ANIM_PATH}. */
    public static BeastAnimation cached() {
        BeastAnimation hit = cached;
        if (hit == null) {
            synchronized (BeastAnimation.class) {
                hit = cached;
                if (hit == null) {
                    hit = load(ANIM_PATH);
                    cached = hit;
                }
            }
        }
        return hit;
    }

    public Map<String, MatouAnimation> clips() {
        return clips;
    }
}
