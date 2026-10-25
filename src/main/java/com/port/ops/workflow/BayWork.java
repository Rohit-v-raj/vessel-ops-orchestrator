package com.port.ops.workflow;

/** Moves still to do in one bay. After a breakdown, the rest of a bay is a smaller BayWork. */
record BayWork(int bay, int moves) {
}
