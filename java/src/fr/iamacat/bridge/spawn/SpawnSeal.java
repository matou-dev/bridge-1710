package fr.iamacat.bridge.spawn;

import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.SpawnStates;
import fr.iamacat.spi.StateVocabulary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spawn tranche: per-tick seal of bridge-owned census state plus the wired
 * spawn table into snapshot states. Pure Java, zero Minecraft.
 *
 * <p>The forge side calls {@link #seal} beside {@code pack.states(tick)},
 * wraps the merge with {@code ForgeSnapshot.snapshot(tick, ...)} — the
 * snapshot choke point stays the single factory — and runs the pure
 * {@code SpawnJob} over it. Neither the store nor the range is ever
 * shared: only sealed copies cross the seam, so a later record can never
 * rewrite a decision already taken. State ids resolve through the
 * pack-served {@link StateVocabulary} (T3 registry, hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): the seal shares the job's
 * ids with no bridge-to-content compile edge. Java 8, zero deps beyond
 * matou-spi.
 *
 * <p>Per-mob dispatch (second-beast tranche, hub
 * {@code decisions/VIRTUAL_HITBOXES.md}): the seal takes ordered
 * per-mob tables — {@code TABLE} an ordered list of qualified mob refs,
 * {@code CAP}/{@code BUDGET} maps of qualified ref to number,
 * {@code Y} a map of qualified ref to {@code [yMin, yMax]} number pair
 * (the shapes the per-mob {@code SpawnJob} consumes: sole-mob seals
 * keep the legacy single shapes, per-mob seals carry the list and maps,
 * shapes never mixed). A single-mob seal emits states IDENTICAL to the
 * legacy single-mob call (same string, same longs, same pair — the gate
 * holds both paths equal), so sole-mob content seals zero behaviour
 * change.
 */
public final class SpawnSeal {
    private SpawnSeal() {}

    /**
     * Seal view for one tick: census (entity id to spawn cell) plus table
     * (content mob ref) plus cap plus budget plus y ({@code [yMin, yMax]}
     * longs) — roles resolved through {@code vocab} in seal order, so
     * live and verdict replay agree. The forge side serves the pack's
     * vocabulary at wire time (parse-once, never on the tick path).
     *
     * @throws NullPointerException when vocab, store or mob is null.
     * @throws IllegalArgumentException when mob is bare, cap or budget
     *         is not positive, the range is unordered, or the vocabulary
     *         carries no spawn roles.
     */
    public static Map<MatouId, Object> seal(StateVocabulary vocab,
            SpawnStore store, String mob,
            long cap, long budget, long yMin, long yMax) {
        return seal(vocab, store, Collections.singletonList(mob),
                Collections.singletonMap(mob, Long.valueOf(cap)),
                Collections.singletonMap(mob, Long.valueOf(budget)),
                Collections.singletonMap(mob, Arrays.asList(
                        Long.valueOf(yMin), Long.valueOf(yMax))));
    }

    /**
     * Per-mob seal view for one tick: census plus the ordered per-mob
     * tables ({@code table} qualified refs in seal order, {@code cap} /
     * {@code budget} qualified ref to number, {@code y} qualified ref to
     * {@code [yMin, yMax]} numbers) — roles resolved through
     * {@code vocab} in seal order, so live and verdict replay agree.
     * Every table mob funds every map and vice versa (a lonely mob or a
     * lonely entry refuses — an unfunded mob would be a silent no-op).
     * A single-mob seal emits the legacy shapes (mob string, cap/budget
     * longs, {@code [yMin, yMax]} long pair); a multi-mob seal emits the
     * per-mob shapes (mob list, cap/budget maps, y map of pairs).
     *
     * @throws NullPointerException when vocab, store, table, cap,
     *         budget, y or any table entry is null.
     * @throws IllegalArgumentException when the table is empty, any mob
     *         is bare or duplicated, a map misses or overfunds a table
     *         mob, any cap or budget is not a positive number, any y is
     *         not an ordered {@code [yMin, yMax]} number pair, or the
     *         vocabulary carries no spawn roles.
     */
    public static Map<MatouId, Object> seal(StateVocabulary vocab,
            SpawnStore store, List<String> table, Map<String, ?> cap,
            Map<String, ?> budget, Map<String, ?> y) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_SPAWN_SEAL:null vocabulary");
        }
        if (store == null) {
            throw new NullPointerException("E_SPAWN_SEAL:null store");
        }
        if (table == null) {
            throw new NullPointerException("E_SPAWN_SEAL:null table");
        }
        if (table.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_SPAWN_SEAL:empty table (one mob at least funds "
                            + "the census)");
        }
        Set<String> mobs = new LinkedHashSet<String>();
        for (String mob : table) {
            if (mob == null) {
                throw new NullPointerException("E_SPAWN_SEAL:null mob");
            }
            if (mob.isEmpty() || mob.indexOf(':') < 0) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:type <" + mob
                                + "> (want \"ns:mob\" content ref)");
            }
            if (!mobs.add(mob)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:dupe <" + mob + "> (one row per "
                                + "mob — a second row would be a quiet "
                                + "pick)");
            }
        }
        Map<String, Long> caps = numbers(cap, "cap", mobs);
        Map<String, Long> budgets = numbers(budget, "budget", mobs);
        Map<String, List<Long>> bands = bands(y, mobs);
        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(SpawnStates.census(vocab), store.sealed());
        if (mobs.size() == 1) {
            String sole = table.get(0);
            states.put(SpawnStates.table(vocab), sole);
            states.put(SpawnStates.cap(vocab), caps.get(sole));
            states.put(SpawnStates.budget(vocab), budgets.get(sole));
            states.put(SpawnStates.y(vocab), bands.get(sole));
        } else {
            states.put(SpawnStates.table(vocab),
                    Collections.unmodifiableList(
                            new ArrayList<String>(table)));
            states.put(SpawnStates.cap(vocab),
                    Collections.unmodifiableMap(caps));
            states.put(SpawnStates.budget(vocab),
                    Collections.unmodifiableMap(budgets));
            states.put(SpawnStates.y(vocab),
                    Collections.unmodifiableMap(bands));
        }
        return states;
    }

    /**
     * Per-mob number rule: every table mob funds exactly one positive
     * number, nothing else does. Values normalize to longs.
     */
    private static Map<String, Long> numbers(Map<String, ?> raw,
            String role, Set<String> mobs) {
        if (raw == null) {
            throw new NullPointerException(
                    "E_SPAWN_SEAL:null " + role);
        }
        for (String mob : mobs) {
            if (!raw.containsKey(mob)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:lonely mob <" + mob + "> (every "
                                + "table mob funds a " + role + " — an "
                                + "unfunded mob would be a silent "
                                + "no-op)");
            }
        }
        for (String key : raw.keySet()) {
            if (!mobs.contains(key)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:lonely " + role + " <" + key
                                + "> (every " + role + " funds a table "
                                + "mob — an unfunded entry would be a "
                                + "silent no-op)");
            }
        }
        Map<String, Long> out = new LinkedHashMap<String, Long>();
        for (String mob : mobs) {
            Object v = raw.get(mob);
            if (!(v instanceof Number)
                    || ((Number) v).longValue() <= 0L) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:range <" + role + " " + mob + "="
                                + v + "> (want " + role + " > 0)");
            }
            out.put(mob, Long.valueOf(((Number) v).longValue()));
        }
        return out;
    }

    /**
     * Per-mob band rule: every table mob funds exactly one ordered
     * {@code [yMin, yMax]} number pair, nothing else does. Pairs
     * normalize to long pairs.
     */
    private static Map<String, List<Long>> bands(Map<String, ?> raw,
            Set<String> mobs) {
        if (raw == null) {
            throw new NullPointerException("E_SPAWN_SEAL:null y");
        }
        for (String mob : mobs) {
            if (!raw.containsKey(mob)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:lonely mob <" + mob + "> (every "
                                + "table mob funds a y band — an "
                                + "unfunded mob would be a silent "
                                + "no-op)");
            }
        }
        for (String key : raw.keySet()) {
            if (!mobs.contains(key)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:lonely y <" + key + "> (every y "
                                + "band funds a table mob — an unfunded "
                                + "entry would be a silent no-op)");
            }
        }
        Map<String, List<Long>> out =
                new LinkedHashMap<String, List<Long>>();
        for (String mob : mobs) {
            Object v = raw.get(mob);
            if (!(v instanceof List)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:range <y " + mob + "=" + v
                                + "> (want [yMin, yMax] longs)");
            }
            List<?> pair = (List<?>) v;
            if (pair.size() != 2 || !(pair.get(0) instanceof Number)
                    || !(pair.get(1) instanceof Number)) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:range <y " + mob + "=" + v
                                + "> (want [yMin, yMax] longs)");
            }
            long yMin = ((Number) pair.get(0)).longValue();
            long yMax = ((Number) pair.get(1)).longValue();
            if (yMin < 0 || yMax < yMin) {
                throw new IllegalArgumentException(
                        "E_SPAWN_SEAL:range <[" + yMin + "," + yMax
                                + "]> (want 0 <= yMin <= yMax)");
            }
            out.put(mob, Collections.unmodifiableList(Arrays.asList(
                    Long.valueOf(yMin), Long.valueOf(yMax))));
        }
        return out;
    }
}
