package fr.iamacat.bridge.wire;

import fr.iamacat.bridge.Packs.PackSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * T2 operator overrides (hub decisions/SPAWN.md operator-override
 * tranche, extended by the combat reach-override tranche in hub
 * decisions/VIRTUAL_HITBOXES.md): the operator tunes content-decided
 * spawn/loot/combat policy from {@code packs.cfg} without touching
 * {@code owned.matou}.
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
 * </ul>
 *
 * <p>Precedence: operator present wins, else content. Unknown
 * {@code spawn.*}/{@code loot.*}/{@code combat.*} keys refuse loudly (a
 * typo is never a silent default); other namespaces ({@code ownedFile},
 * {@code block.}, {@code veinblock.}, ...) are not this file's business.
 * Several wire lines carrying the same key with different values refuse
 * loudly (silent picks are defaults).
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
     * Effective spawn policy: content values with operator wins applied.
     *
     * @return {@code long[]{cap, budget, yMin, yMax}}, all validated.
     * @throws NullPointerException when specs is null.
     * @throws IllegalArgumentException on bad/multi/unknown operator
     *         values or a merged unordered band — never defaulted.
     */
    public static long[] effectiveSpawn(long cap, long budget, long yMin,
            long yMax, List<PackSpec> specs) {
        if (specs == null) {
            throw new NullPointerException("E_SPAWN_WIRE:null specs");
        }
        rejectUnknownSpawn(specs);
        Long oCap = positive(specs, SPAWN_CAP, "E_SPAWN_WIRE");
        Long oBudget = positive(specs, SPAWN_BUDGET, "E_SPAWN_WIRE");
        Long oYMin = nonNegative(specs, SPAWN_Y_MIN, "E_SPAWN_WIRE");
        Long oYMax = nonNegative(specs, SPAWN_Y_MAX, "E_SPAWN_WIRE");
        long eCap = oCap != null ? oCap.longValue() : cap;
        long eBudget = oBudget != null ? oBudget.longValue() : budget;
        long eYMin = oYMin != null ? oYMin.longValue() : yMin;
        long eYMax = oYMax != null ? oYMax.longValue() : yMax;
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
     * Effective combat reach: content reach with the operator win
     * applied (hub decisions/VIRTUAL_HITBOXES.md reach-override
     * tranche — the weakspot table itself stays content-only).
     *
     * @throws NullPointerException when specs is null.
     * @throws IllegalArgumentException on bad/multi/unknown operator
     *         values or a non-positive non-finite merge — never
     *         defaulted.
     */
    public static double effectiveCombatReach(double reach,
            List<PackSpec> specs) {
        if (specs == null) {
            throw new NullPointerException("E_COMBAT_WIRE:null specs");
        }
        rejectUnknownCombat(specs);
        Double o = positiveFinite(specs, COMBAT_REACH, "E_COMBAT_WIRE");
        double e = o != null ? o.doubleValue() : reach;
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

    /** Unknown {@code spawn.*} keys refuse (typos never vanish). */
    private static void rejectUnknownSpawn(List<PackSpec> specs) {
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_SPAWN_WIRE:null spec");
            }
            for (String key : spec.args.keySet()) {
                if (key != null && key.startsWith("spawn.")
                        && !key.equals(SPAWN_CAP)
                        && !key.equals(SPAWN_BUDGET)
                        && !key.equals(SPAWN_Y_MIN)
                        && !key.equals(SPAWN_Y_MAX)) {
                    throw new IllegalArgumentException(
                            "E_SPAWN_WIRE:unknown <" + key + "> (want "
                                    + "spawn.cap/spawn.budget/spawn.y_min/"
                                    + "spawn.y_max, never silent typos)");
                }
            }
        }
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

    /** Unknown {@code combat.*} keys refuse (typos never vanish). */
    private static void rejectUnknownCombat(List<PackSpec> specs) {
        for (PackSpec spec : specs) {
            if (spec == null) {
                throw new NullPointerException("E_COMBAT_WIRE:null spec");
            }
            for (String key : spec.args.keySet()) {
                if (key != null && key.startsWith("combat.")
                        && !key.equals(COMBAT_REACH)) {
                    throw new IllegalArgumentException(
                            "E_COMBAT_WIRE:unknown <" + key + "> (want "
                                    + "combat.reach, never silent typos)");
                }
            }
        }
    }
}
