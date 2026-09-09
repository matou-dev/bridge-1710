package fr.iamacat.bridge;

import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * B2 pure orchestrator: one pack per tick — seal states into a Snapshot,
 * run every job, merge owned-first, land cells on a {@link CellSink}.
 * Pure Java, zero Minecraft: the FML side only supplies packs and sinks.
 * Java 8, zero deps beyond matou-spi.
 */
public final class ForgeContent {
    private ForgeContent() {}

    /**
     * Owned-first merge with dedup over N decisions. Generalizes the 2-way
     * content merge contract (see the gate comparateur against
     * {@code AdditiveScatterJob.merge}). A null decision contributes
     * nothing (SpiBridge parity: null applies nothing); a null cell is
     * corrupt data and refused.
     */
    public static List<String> merge(List<List<String>> decisions) {
        if (decisions == null) {
            throw new NullPointerException("E_BRIDGE_MERGE:null");
        }
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (List<String> decision : decisions) {
            if (decision == null) {
                continue;
            }
            for (String cell : decision) {
                if (cell == null) {
                    throw new NullPointerException(
                            "E_BRIDGE_MERGE:null cell");
                }
                seen.add(cell);
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(seen));
    }

    /** Seal, decide every job in order, merge. Never null. */
    public static List<String> decideAll(ContentPack pack, long tick) {
        if (pack == null) {
            throw new NullPointerException("E_BRIDGE_PACK:null");
        }
        Map<MatouId, Object> states = pack.states(tick);
        if (states == null) {
            throw new NullPointerException("E_BRIDGE_PACK:null states");
        }
        List<MatouJob<List<String>>> jobs = pack.jobs();
        if (jobs == null) {
            throw new NullPointerException("E_BRIDGE_PACK:null jobs");
        }
        Snapshot snap = ForgeSnapshot.snapshot(tick, states);
        List<List<String>> decisions = new ArrayList<List<String>>(
                jobs.size());
        for (MatouJob<List<String>> job : jobs) {
            if (job == null) {
                throw new NullPointerException("E_BRIDGE_PACK:null job");
            }
            decisions.add(job.decide(snap));
        }
        return merge(decisions);
    }

    /** Decide then land verbatim on the sink. */
    public static void applyAll(ContentPack pack, long tick, CellSink sink) {
        if (sink == null) {
            throw new NullPointerException("E_BRIDGE_SINK:null");
        }
        ForgeCells.applyCells(decideAll(pack, tick), sink);
    }
}
