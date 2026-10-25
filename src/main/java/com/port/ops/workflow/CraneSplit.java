package com.port.ops.workflow;

import com.port.ops.model.MoveList.BayMoves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many quay cranes a ship gets, and which bays each one works.
 *
 * This is plain computation: the same move list always gives the same split.
 * So it can live in workflow code, which is the simplest place for it, and
 * needs no activity. (If it read a database it would have to be an activity.)
 */
final class CraneSplit {

    private CraneSplit() {}

    /** One crane per {@code movesPerCrane} moves, rounded up, from 1 to {@code maxCranes}. */
    static int craneCount(int totalMoves, int movesPerCrane, int maxCranes) {
        int wanted = (totalMoves + movesPerCrane - 1) / movesPerCrane;
        return Math.max(1, Math.min(maxCranes, wanted));
    }

    /**
     * Cuts the bays, in bay order, into one contiguous range per crane with
     * about the same number of moves each. Contiguous because the cranes run
     * on one rail along the quay and cannot pass each other.
     *
     * Returns the cranes in the order given; a {@link LinkedHashMap}, so that
     * iterating it is deterministic.
     */
    static Map<String, List<BayWork>> split(List<BayMoves> bays, List<String> cranes) {
        List<BayWork> work = bays.stream()
                .filter(b -> b.moves() > 0)
                .sorted(Comparator.comparingInt(BayMoves::bay))
                .map(b -> new BayWork(b.bay(), b.moves()))
                .toList();
        Map<String, List<BayWork>> split = new LinkedHashMap<>();
        cranes.forEach(c -> split.put(c, new ArrayList<>()));

        int n = Math.min(cranes.size(), work.size());
        double total = work.stream().mapToInt(BayWork::moves).sum();
        int crane = 0;
        double cumulative = 0;
        for (int i = 0; i < work.size(); i++) {
            BayWork bay = work.get(i);
            List<BayWork> current = split.get(cranes.get(crane));
            boolean shareReached = cumulative + bay.moves() / 2.0 > total * (crane + 1) / n;
            boolean onlyEnoughBaysLeft = work.size() - i <= n - crane - 1;
            if (crane < n - 1 && !current.isEmpty() && (shareReached || onlyEnoughBaysLeft)) {
                crane++;
            }
            split.get(cranes.get(crane)).add(bay);
            cumulative += bay.moves();
        }
        return split;
    }
}
