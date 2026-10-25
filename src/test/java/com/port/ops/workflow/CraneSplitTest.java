package com.port.ops.workflow;

import com.port.ops.model.MoveList.BayMoves;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CraneSplitTest {

    @Test
    void oneCranePerFourHundredMovesFromOneToThree() {
        assertThat(CraneSplit.craneCount(150, 400, 3)).isEqualTo(1);
        assertThat(CraneSplit.craneCount(401, 400, 3)).isEqualTo(2);
        assertThat(CraneSplit.craneCount(5000, 400, 3)).isEqualTo(3);
    }

    @Test
    void equalBaysAreSplitIntoEqualContiguousRanges() {
        List<BayMoves> bays = IntStream.range(0, 12).mapToObj(i -> new BayMoves(2 + 4 * i, 40, 45)).toList();

        Map<String, List<BayWork>> split = CraneSplit.split(bays, List.of("QC01", "QC02", "QC03"));

        assertThat(split.get("QC01")).extracting(BayWork::bay).containsExactly(2, 6, 10, 14);
        assertThat(split.get("QC02")).extracting(BayWork::bay).containsExactly(18, 22, 26, 30);
        assertThat(split.get("QC03")).extracting(BayWork::bay).containsExactly(34, 38, 42, 46);
    }

    @Test
    void bayOrderNotListOrderDecidesTheRanges() {
        List<BayMoves> bays = List.of(new BayMoves(30, 50, 50), new BayMoves(2, 50, 50),
                new BayMoves(14, 50, 50), new BayMoves(6, 0, 0));

        Map<String, List<BayWork>> split = CraneSplit.split(bays, List.of("QC01", "QC02"));

        // Bay 6 has no moves and is left out; the rest are cut in bay order.
        assertThat(split.get("QC01")).extracting(BayWork::bay).containsExactly(2, 14);
        assertThat(split.get("QC02")).extracting(BayWork::bay).containsExactly(30);
    }

    @Test
    void everyCraneGetsABayEvenWhenOneBayIsHuge() {
        List<BayMoves> bays = List.of(new BayMoves(2, 500, 500), new BayMoves(6, 5, 5), new BayMoves(10, 5, 5));

        Map<String, List<BayWork>> split = CraneSplit.split(bays, List.of("QC01", "QC02", "QC03"));

        assertThat(split.values()).allSatisfy(work -> assertThat(work).hasSize(1));
    }
}
