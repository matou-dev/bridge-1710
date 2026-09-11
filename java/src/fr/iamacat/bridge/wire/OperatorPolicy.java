package fr.iamacat.bridge.wire;

import fr.iamacat.bridge.Packs.PackSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T2 operator overrides (hub decisions/SPAWN.md operator-override
 * tranche, extended by the combat reach-override tranche and the
 * per-mob override tranche in hub decisions/VIRTUAL_HITBOXES.md): the
 * operator tunes content-decided spawn/loot/combat policy from
 * {@code packs.cfg} without touching {@code owned.matou}.
 *
 * <p>Placement: this package is the shared base below the
 * {@code spawn}/{@code loot}/{@code combat} leaves (all import down,
 * never sideways),
 * and beside the SPI seam (no SPI change, no re-pin — the vocabulary is
 * bridge-owned operator domain, content names stay in example1).
 *
 * <p>Vocabulary (one {@code k=v} arg per wire line, must agree across
 * lines):
 * <ul>
 *   <li>{@code spawn.cap} — living cap (positive u32, default content
 *   cap).</li>
 *   <li>{@code spawn.budget} — landings per tick (positive u32, default
 *   content budget).</li>
 *   <li>{@code spawn.y_min} / {@code spawn.y_max} — ordinate band
 *   (u32, {@code 0 <= y_min <= y_max}, each defaulting to its content
 *   side independently; a merged inversion refuses).</li>
 *   <li>{@code loot.count} — items per harvest (positive u32, default
 *   content drop_count).</li>
 *   <li>{@code combat.reach} — eye-to-hitVec ray-test cutoff in blocks
 *   (positive finite f64, default content reach). Weakspot multipliers
 *   stay content-only (damage balance, same split as {@code spawnHp} —
 *   no operator key, never a quiet knob).</li>
 *   <li>{@code spawn.cap.<mob>} / {@code spawn.budget.<mob>} /
 *   {@code spawn.y_min.<mob>} / {@code spawn.y_max.<mob>} /
 *   {@code combat.reach.<mob>} — per-mob wins (same value shapes as
 *   their global key, short mob names with the sealed-maps spelling).
 *   {@code loot.count} stays global (damage/count balance, same split
 *   as the content-only weakspot multipliers — no per-mob count knob,
 *   never a quiet one).</li>
 * </ul>
 *
 * <p>Precedence per mob: the per-mob key wins, else the global key,
 * else content. Unknown {@code spawn.*}/{@code loot.*}/
 * {@code combat.*} keys refuse loudly (a typo is never a silent
 * default — that covers bad bases with or without a suffix as well as
 * well-formed per-mob keys naming an unsealed mob); other namespaces
 * ({@code ownedFile}, {@code block.}, {@code veinblock.}, ...) are not
 * this file's business. Several wire lines carrying the same key with
 * different values refuse loudly (silent picks are defaults).
 *
 * <p>Loot scope rides no key: the ore is the operator wire-block column
 * ({@link #wireBlocks}), one entry per distinct wire block in line
 * order. The forge side resolves each to a block (unknown refuses under
 * the existing {@code E_LOOT_ORE} code).
 *
 * <p>Overrides are validated when consumed (an owned file wires the
 * subsystem); without owned content spawn/loot stay passive and the keys
 * are ignored like any other unused arg (Q1 cohabitation). Pure,
 * Java 8, zero Minecraft, zero deps beyond matou-spi.
 */
public final class OperatorPolicy {
    /** Operator living-cap override (positive u32). */
    public static final String SPAWN_CAP = "spawn.cap";
    /** Operator per-tick-budget override (positive u32). */
    public static final String SPAWN_BUDGET = "spawn.budget";
    /** Operator ordinate-floor override (u32). */
    public static final String SPAWN_Y_MIN = "spawn.y_min";
    /** Operator ordinate-ceiling override (u32). */
    public static final String SPAWN_Y_MAX = "spawn.y_max";
    /** Operator items-per-harvest override (positive u32). */
    public static final String LOOT_COUNT = "loot.count";
    /** Operator eye-to-hitVec cutoff override (positive finite f64). */
    public static final String COMBAT_REACH = "combat.reach";

    private OperatorPolicy() {}

    /**
     * Effective spawn policy for one mob: content values with operator
     * wins applied (per-mob tranche, hub
     * decisions/VIRTUAL_HITBOXES.md — the per-mob key wins, else the
     * global key, else content).
     *
     * @param mob short mob name (the sealed-maps spelling).
     * @param mobs sealed short mob names (every per-mob suffix across
     *        the specs must name one — a typo'd mob is never a silent
     *        content fallback).
     * @return {@code long[]{cap, budget, yMin, yMax}}, all validated.
     * @throws NullPointerException when mob, mobs or specs is null.
     * @throws IllegalArgumentException on an unsealed mob, on
     *         bad/multi/unknown operator values or a merged unordered
     *         band — never defaulted.
     */
    public static long[] effectiveSpawn(long cap, long budget, long yMin,
            long yMax, String mob, Collection<String> mobs,
            List<PackSpec> specs) {
        if (mob == null) {
            throw new NullPointerException("E_SPAWN_WIRE:null mob");
        }
        if (mobs == null) {
            throw new NullPointerException("E_SPAWN_WIRE:null mobs");
        }
        if (specs == null) {
            throw new NullPointerException("E_SPAWN_WIRE:null specs");
        }
        if (!mobs.contains(mob)) {
            throw new IllegalArgumentException("E_SPAWN_WIRE:unknown mob <"
                    + mob + "> (want one of " + mobs
                    + " — sealed mobs only, never defaulted)");
        }
        rejectUnknownSpawn(specs, mobs);
        Long gCap = positive(specs, SPAWN_CAP, "E_SPAWN_WIRE");
        Long gBudget = positive(specs, SPAWN_BUDGET, "E_SPAWN_WIRE");
        Long gYMin = nonNegative(specs, SPAWN_Y_MIN, "E_SPAWN_WIRE");
        Long gYMax = nonNegative(specs, SPAWN_Y_MAX, "E_SPAWN_WIRE");
        Long mCap = positive(specs, SPAWN_CAP + "." + mob,
                "E_SPAWN_WIRE");
        Long mBudget = positive(specs, SPAWN_BUDGET + "." + mob,
                "E_SPAWN_WIRE");
        Long mYMin = nonNegative(specs, SPAWN_Y_MIN + "." + mob,
                "E_SPAWN_WIRE");
        Long mYMax = nonNegative(specs, SPAWN_Y_MAX + "." + mob,
                "E_SPAWN_WIRE");
        long eCap = mCap != null ? mCap.longValue()
                : gCap != null ? gCap.longValue() : cap;
        long eBudget = mBudget != null ? mBudget.longValue()
                : gBudget != null ? gBudget.longValue() : budget;
        long eYMin = mYMin != null ? mYMin.longValue()
                : gYMin != null ? gYMin.longValue() : yMin;
        long eYMax = mYMax != null ? mYMax.longValue()
                : gYMax != null ? gYMax.longValue() : yMax;
        if (eCap <= 0) {
            throw new IllegalArgumentException("E_SPAWN_WIRE:range <cap="
                    + eCap + "> (want > 0, content or override)");
        }
        if (eBudget <= 0) {
            throw new IllegalArgumentException("E_SPAWN_WIRE:range <budget="
                    + eBudget + "> (want > 0, content or override)");
        }
        if (eYMin < 0 || eYMax < eYMin) {
            throw new IllegalArgumentException("E_SPAWN_WIRE:bad y <"
                    + eYMin + ".." + eYMax
                    + "> (want 0 <= y_min <= y_max, content or override)");
        }
        return new long[]{eCap, eBudget, eYMin, eYMax};
    }

    /**
     * Effective items per harvest: content count with the operator win
     * applied.
     *
     * @throws NullPointerException when specs is null.
     * @throws IllegalArgumentException on bad/multi/unknown operator
     *         values or a non-positive merge — never defaulted.
     */
    public static long effectiveLootCount(long count,
            List<PackSpec> specs) {
        if (specs == null) {
            throw new NullPointerException("E_LOOT_WIRE:null specs");
        }
        rejectUnknownLoot(specs);
        Long o = positive(specs, LOOT_COUNT, "E_LOOT_WIRE");
        long e = o != null ? o.longValue() : count;
        if (e <= 0) {
            throw new IllegalArgumentException("E_LOOT_WIRE:range <count="
                    + e + "> (want > 0, content or override)");
        }
        return e;
    }

    /**
     * Effective items per harvest per kind: content counts with the
     * operator win applied uniformly (hub {@code decisions/LOOT.md},
     * distinct-drops tranche — the {@code loot.count} key stays
     * global, so a present win pays every kind alike, else each kind
     * keeps its content number; per-kind loot operator keys stay a
     * named follow-up, never a quiet suffix here).
     *
     * @param content harvest kind to content count (positive entries).
     * @throws NullPointerException when content or specs is null.
     * @throws IllegalArgumentException on bad/multi/unknown operator
     *         values or a non-positive merge — never defaulted.
     */
    public static Map<String, Long> effectiveLootCounts(
            Map<String, Long> content, List<PackSpec> specs) {
        if (content == null) {
            throw new NullPointerException("E_LOOT_WIRE:null content");
        }
        if (specs == null) {
            throw new NullPointerException("E_LOOT_WIRE:null specs");
        }
        rejectUnknownLoot(specs);
        Long o = positive(specs, LOOT_COUNT, "E_LOOT_WIRE");
        Map<String, Long> effective = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, Long> e : content.entrySet()) {
            long count = e.getValue() == null ? -1L
                    : e.getValue().longValue();
            long merged = o != null ? o.longValue() : count;
            if (merged <= 0) {
                throw new IllegalArgumentException("E_LOOT_WIRE:range <"
                        + e.getKey() + " count=" + merged
                        + "> (want > 0, content or override)");
            }
            effective.put(e.getKey(), Long.valueOf(merged));
        }
        return Collections.unmodifiableMap(effective);
    }

    /**
     * Effective combat reach for one mob: content reach with the
     * operator win applied (hub decisions/VIRTUAL_HITBOXES.md
     * reach-override tranche for the global key, per-mob tranche for
     * the suffixed key — the weakspot table itself stays
     * content-only).
     *
     * @param mob short mob name (the sealed-maps spelling).
     * @param mobs sealed short mob names (every per-mob suffix across
     *        the specs must name one — a typo'd mob is never a silent
     *        content fallback).
     * @throws NullPointerException when mob, mobs or specs is null.
     * @throws IllegalArgumentException on an unsealed mob, on
     *         bad/multi/unknown operator values or a non-positive
     *         non-finite merge — never defaulted.
     */
    public static double effectiveCombatReach(double reach, String mob,
            Collection<String> mobs, List<PackSpec> specs) {
        if (mob == null) {
            throw new NullPointerException("E_COMBAT_WIRE:null mob");
        }
        if (mobs == null) {
            throw new NullPointerException("E_COMBAT_WIRE:null mobs");
        }
        if (specs == null) {
            throw new NullPointerException("E_COMBAT_WIRE:null specs");
        }
        if (!mobs.contains(mob)) {
            throw new IllegalArgumentException(
                    "E_COMBAT_WIRE:unknown mob <" + mob + "> (want one of "
                            + mobs + " — sealed mobs only, never defaulted)");
        }
        rejectUnknownCombat(specs, mobs);
        Double g = positiveFinite(specs, COMBAT_REACH, "E_COMBAT_WIRE");
        Double m = positiveFinite(specs, COMBAT_REACH + "." + mob,
                "E_COMBAT_WIRE");
        double e = m != null ? m.doubleValue()
                : g != null ? g.doubleValue() : reach;
        if (!(e > 0.0) || Double.isNaN(e) || Double.isInfinite(e)) {
            throw new IllegalArgumentException("E_COMBAT_WIRE:range <reach="
                    + e + "> (want positive finite f64, content or override)");
        }
        return e;
    }

    /**
     * Ordered distinct wire-block names across all specs (the loot ore
     * scope). A read-through, never a copy owner.
     *
     * @throws NullPointerException when specs or an entry is null.
     * @throws IllegalArgumentException on a bare block name — never
     *         defaulted.
     */
    public static List<String> wireBlocks(List<PackSpec> specs) {
        if (specs == null) {
            throw new NullPointerException("E_LOOT_WIRE:null specs");
        }
        List<String> out = new ArrayList<String>();
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_LOOT_WIRE:null spec");
            }
            String name = spec.blockName;
            if (name == null || name.isEmpty()
                    || name.indexOf(':') < 0) {
                throw new IllegalArgumentException("E_LOOT_WIRE:type <"
                        + name + "> (want \"ns:block\" wire block)");
            }
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * True when any wire line carries the known key (log suffix duty —
     * the forge side names what the operator overrode).
     *
     * @throws NullPointerException when specs is null.
     */
    public static boolean present(List<PackSpec> specs, String key) {
        if (specs == null) {
            throw new NullPointerException("E_WIRE:null specs");
        }
        if (key == null) {
            throw new NullPointerException("E_WIRE:null key");
        }
        for (PackSpec spec : specs) {
            if (spec != null && spec.args.get(key) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Single agreed value for a key across all wire lines: null when
     * absent everywhere, the value when every carrier agrees, loud
     * otherwise.
     */
    private static String singleValue(List<PackSpec> specs, String key,
            String code) {
        String found = null;
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException(code + ":null spec");
            }
            String v = spec.args.get(key);
            if (v == null) {
                continue;
            }
            if (found == null) {
                found = v;
            } else if (!found.equals(v)) {
                throw new IllegalArgumentException(code + ":multi <" + key
                        + ": " + found + " vs " + v
                        + "> (one operator policy per bridge)");
            }
        }
        return found;
    }

    /** Positive-u32 operator arg, null when absent. Loud, never 0. */
    private static Long positive(List<PackSpec> specs, String key,
            String code) {
        String raw = singleValue(specs, key, code);
        if (raw == null) {
            return null;
        }
        try {
            long v = Long.parseLong(raw);
            if (v <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return Long.valueOf(v);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException(code + ":bad <" + key + "="
                    + raw + "> (want positive u32, never defaulted)");
        }
    }

    /** Non-negative-u32 operator arg, null when absent. Loud, never -1. */
    private static Long nonNegative(List<PackSpec> specs, String key,
            String code) {
        String raw = singleValue(specs, key, code);
        if (raw == null) {
            return null;
        }
        try {
            long v = Long.parseLong(raw);
            if (v < 0) {
                throw new NumberFormatException("negative");
            }
            return Long.valueOf(v);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException(code + ":bad <" + key + "="
                    + raw + "> (want u32 >= 0, never defaulted)");
        }
    }

    /** Positive-finite-f64 operator arg, null when absent. Loud, never 0/NaN. */
    private static Double positiveFinite(List<PackSpec> specs, String key,
            String code) {
        String raw = singleValue(specs, key, code);
        if (raw == null) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw);
            if (!(v > 0.0) || Double.isNaN(v) || Double.isInfinite(v)) {
                throw new NumberFormatException("non-positive-or-infinite");
            }
            return Double.valueOf(v);
        } catch (NumberFormatException bad) {
            throw new IllegalArgumentException(code + ":bad <" + key + "="
                    + raw + "> (want positive finite f64, never defaulted)");
        }
    }

    /** Unknown {@code spawn.*} keys refuse (typos never vanish —
     * global or {@code .<mob>}-suffixed known bases only, and every
     * suffix names a sealed mob). */
    private static void rejectUnknownSpawn(List<PackSpec> specs,
            Collection<String> mobs) {
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_SPAWN_WIRE:null spec");
            }
            for (String key : spec.args.keySet()) {
                if (key != null && key.startsWith("spawn.")
                        && !knownSpawnKey(key, mobs)) {
                    throw new IllegalArgumentException(
                            "E_SPAWN_WIRE:unknown <" + key + "> (want "
                                    + "spawn.cap/spawn.budget/spawn.y_min/"
                                    + "spawn.y_max plus .<mob> per-mob keys "
                                    + "over " + mobs
                                    + ", never silent typos)");
                }
            }
        }
    }

    /**
     * True for the global spawn keys plus well-formed per-mob keys over
     * sealed mobs ({@code spawn.<base>.<mob>}, one dot, non-empty
     * suffix).
     */
    private static boolean knownSpawnKey(String key,
            Collection<String> mobs) {
        if (key.equals(SPAWN_CAP) || key.equals(SPAWN_BUDGET)
                || key.equals(SPAWN_Y_MIN) || key.equals(SPAWN_Y_MAX)) {
            return true;
        }
        String[] bases = {SPAWN_CAP, SPAWN_BUDGET, SPAWN_Y_MIN,
            SPAWN_Y_MAX};
        for (String base : bases) {
            if (key.startsWith(base + ".")) {
                String suffix = key.substring(base.length() + 1);
                return !suffix.isEmpty() && suffix.indexOf('.') < 0
                        && mobs.contains(suffix);
            }
        }
        return false;
    }

    /** Unknown {@code loot.*} keys refuse (typos never vanish). */
    private static void rejectUnknownLoot(List<PackSpec> specs) {
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_LOOT_WIRE:null spec");
            }
            for (String key : spec.args.keySet()) {
                if (key != null && key.startsWith("loot.")
                        && !key.equals(LOOT_COUNT)) {
                    throw new IllegalArgumentException(
                            "E_LOOT_WIRE:unknown <" + key + "> (want "
                                    + "loot.count, never silent typos)");
                }
            }
        }
    }

    /** Unknown {@code combat.*} keys refuse (typos never vanish —
     * the global key or a {@code .<mob>}-suffixed key over sealed
     * mobs only). */
    private static void rejectUnknownCombat(List<PackSpec> specs,
            Collection<String> mobs) {
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_COMBAT_WIRE:null spec");
            }
            for (String key : spec.args.keySet()) {
                if (key != null && key.startsWith("combat.")
                        && !knownCombatKey(key, mobs)) {
                    throw new IllegalArgumentException(
                            "E_COMBAT_WIRE:unknown <" + key + "> (want "
                                    + "combat.reach plus .<mob> per-mob keys "
                                    + "over " + mobs
                                    + ", never silent typos)");
                }
            }
        }
    }

    /**
     * True for the global combat key plus well-formed per-mob keys over
     * sealed mobs ({@code combat.reach.<mob>}, one dot, non-empty
     * suffix).
     */
    private static boolean knownCombatKey(String key,
            Collection<String> mobs) {
        if (key.equals(COMBAT_REACH)) {
            return true;
        }
        if (key.startsWith(COMBAT_REACH + ".")) {
            String suffix = key.substring(COMBAT_REACH.length() + 1);
            return !suffix.isEmpty() && suffix.indexOf('.') < 0
                    && mobs.contains(suffix);
        }
        return false;
    }
}
