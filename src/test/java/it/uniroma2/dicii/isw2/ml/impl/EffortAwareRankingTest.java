package it.uniroma2.dicii.isw2.ml.impl;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the classes are inspected in order of bug density, that a class is only counted if it
 * fits whole in the budget, and that the share is of the buggy classes.
 */
public class EffortAwareRankingTest {

    private static final double SHARE = 0.20;

    private static final double TOLERANCE = 1e-9;

    /**
     * A huge class the model is all but sure of is a worse use of the budget than a small one it is
     * only somewhat worried about: by probability alone the huge one would come first and exhaust the
     * budget on its own, by density the small one does.
     */
    @Test
    public void testTheClassesAreInspectedByDensityRatherThanByProbability() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.99, 190, false),
                new RankedRow(0.40, 20, true),
                new RankedRow(0.01, 790, false));
        // The budget is 200 of the 1000. By probability the 190 lines would go first and leave no room
        // for the buggy class; by density (.02 against .005) the buggy class goes first and fits
        assertEquals(1.0, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * Reading part of a class finds nothing in the part not read, so the walk stops before the first
     * class that would take it past the budget instead of counting it.
     */
    @Test
    public void testAClassCrossingTheBudgetIsNotCounted() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.90, 150, true),
                new RankedRow(0.50, 100, true),
                new RankedRow(0.00, 750, false));
        // Budget 200: the first (density .006) fits at 150, the second (.005) would reach 250
        assertEquals(0.5, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * A class exactly filling what is left of the budget fits, and is counted.
     */
    @Test
    public void testAClassExactlyFillingTheBudgetIsCounted() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.90, 200, true),
                new RankedRow(0.00, 800, false));
        assertEquals(1.0, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * Of two classes equally dense, the smaller is the cheaper inspection and goes first.
     */
    @Test
    public void testOfTwoEquallyDenseClassesTheSmallerGoesFirst() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.75, 150, false),
                new RankedRow(0.50, 100, true),
                new RankedRow(0.00, 750, false));
        // Both are .005 dense: the smaller, buggy one fits at 100, the larger would reach 250
        assertEquals(1.0, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * A class of no size costs nothing to inspect, so it is inspected first if the model gave it any
     * chance of being buggy, and it never exhausts the budget.
     */
    @Test
    public void testAClassOfNoSizeCostsNothing() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.90, 1000, false),
                new RankedRow(0.10, 0, true));
        assertEquals(1.0, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * With no buggy class there is nothing a ranking could find, which is not the same as having found
     * nothing: the share is no number, which the report leaves blank and no verdict picks.
     */
    @Test
    public void testWithoutABuggyClassTheShareIsNoNumber() {
        List<RankedRow> rows = List.of(new RankedRow(0.90, 10, false), new RankedRow(0.10, 90, false));
        assertTrue(Double.isNaN(EffortAwareRanking.buggyShareFound(rows, SHARE)));
    }

    /**
     * A ranking putting every buggy class last finds none of them within the budget.
     */
    @Test
    public void testARankingPuttingTheBuggyClassesLastFindsNone() {
        List<RankedRow> rows = List.of(
                new RankedRow(0.90, 100, false),
                new RankedRow(0.80, 100, false),
                new RankedRow(0.10, 400, true),
                new RankedRow(0.10, 400, true));
        assertEquals(0.0, EffortAwareRanking.buggyShareFound(rows, SHARE), TOLERANCE);
    }

    /**
     * The rows handed in are the caller's, and ranking them must not reorder them.
     */
    @Test
    public void testTheRowsHandedInAreNotReordered() {
        List<RankedRow> rows = new ArrayList<>(List.of(
                new RankedRow(0.10, 100, true),
                new RankedRow(0.90, 100, false)));
        List<RankedRow> before = List.copyOf(rows);
        EffortAwareRanking.buggyShareFound(rows, SHARE);
        assertEquals(before, rows);
    }

}
