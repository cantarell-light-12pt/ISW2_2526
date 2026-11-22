package it.uniroma2.dicii.isw2.ml.impl;

import java.util.Comparator;
import java.util.List;

/**
 * Reads how much of the defects a model would have led a reviewer to, if the reviewer could only afford
 * to inspect a share of the code — NPofB, the share of buggy classes found within that share of the size.
 * <p>
 * The classes are inspected in order of <b>bug density</b>, the probability the model gave the buggy
 * class over the size of the class, densest first. Ranking by the probability alone would spend the
 * budget on the large classes a model is confident about, which are few and expensive; ranking by
 * density is what makes the measure about <i>effort</i>, since a small class the model finds somewhat
 * risky is a better use of the next line read than a huge one it finds very risky. Two classes equally
 * dense go smaller first, the cheaper inspection, and then in the order they were handed in.
 * <p>
 * A class is counted only if it fits whole in the budget: the walk stops before the first class that
 * would take it past the share, rather than counting a class nobody would have finished reading.
 */
final class EffortAwareRanking {

    /**
     * The order the classes are inspected in: densest first, the smaller of two equally dense ones
     * first. {@code List.sort} is stable, so two rows equal on both keep the order they came in.
     */
    private static final Comparator<RankedRow> INSPECTION_ORDER =
            Comparator.comparingDouble(EffortAwareRanking::density).reversed()
                    .thenComparingDouble(RankedRow::size);

    private EffortAwareRanking() {
        // Ranks rows, and holds none
    }

    /**
     * @param rows        the rows the model was asked about, each with what it answered and its size
     * @param effortShare which share of the total size the reviewer can afford to inspect
     * @return the share of the buggy rows found within that share of the size, or {@code NaN} if none of
     * the rows is buggy — there is then nothing a ranking could find, which is not the same as having
     * found nothing
     */
    static double buggyShareFound(List<RankedRow> rows, double effortShare) {
        long buggy = rows.stream().filter(RankedRow::buggy).count();
        if (buggy == 0) {
            return Double.NaN;
        }
        double budget = effortShare * rows.stream().mapToDouble(RankedRow::size).sum();
        List<RankedRow> ranked = rows.stream().sorted(INSPECTION_ORDER).toList();
        double spent = 0;
        long found = 0;
        for (RankedRow row : ranked) {
            spent += row.size();
            if (spent > budget) {
                break;
            }
            if (row.buggy()) {
                found++;
            }
        }
        return (double) found / buggy;
    }

    /**
     * @param row a row the model was asked about
     * @return how likely it is buggy per unit of size. A class of no size costs nothing to inspect, so
     * it goes before every other one the model gave any chance of being buggy, and after every other one
     * if it gave it none
     */
    private static double density(RankedRow row) {
        if (row.size() > 0) {
            return row.buggyProbability() / row.size();
        }
        return row.buggyProbability() > 0 ? Double.POSITIVE_INFINITY : 0;
    }

}
