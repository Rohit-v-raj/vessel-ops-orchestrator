package com.port.ops.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The shipping line's move list: how many containers to discharge and load in each bay. */
public record MoveList(List<BayMoves> bays) {

    public record BayMoves(int bay, int discharge, int load) {
        public int moves() {
            return discharge + load;
        }
    }

    public int totalMoves() {
        return bays.stream().mapToInt(BayMoves::moves).sum();
    }

    /**
     * Throws {@link IllegalArgumentException} saying what is wrong. Pure and
     * deterministic, so the workflow's update validator can call it.
     */
    public void check() {
        if (bays == null || bays.isEmpty()) {
            throw new IllegalArgumentException("The move list has no bays");
        }
        Set<Integer> seen = new HashSet<>();
        for (BayMoves b : bays) {
            if (b == null) {
                throw new IllegalArgumentException("The move list has an empty entry");
            }
            if (b.bay() < 1 || b.bay() > 99) {
                throw new IllegalArgumentException("Bay " + b.bay() + " is not a bay number from 1 to 99");
            }
            if (!seen.add(b.bay())) {
                throw new IllegalArgumentException("Bay " + b.bay() + " appears twice");
            }
            if (b.discharge() < 0 || b.load() < 0) {
                throw new IllegalArgumentException("Bay " + b.bay() + " has a negative count");
            }
        }
        if (totalMoves() == 0) {
            throw new IllegalArgumentException("The move list has no moves");
        }
    }
}
