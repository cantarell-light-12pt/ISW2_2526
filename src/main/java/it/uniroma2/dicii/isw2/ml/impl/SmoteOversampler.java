package it.uniroma2.dicii.isw2.ml.impl;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;

import java.io.Serial;

/**
 * Balances the rows by synthesising the minority back up to the size of the majority, through the
 * Synthetic Minority Over-sampling TEchnique (Chawla et al., JAIR 16, 2002) as Weka's {@code SMOTE}
 * package implements it.
 * <p>
 * Every row it adds is a new one: a buggy row of the input, moved some random way along the segment
 * joining it to one of its nearest buggy neighbours. That is what sets it apart from drawing the buggy
 * rows again with replacement, which shows a model the very same few classes over and over and invites
 * it to learn them by name; the price is that the rows it adds describe classes no release of the
 * project ever held, and a model may learn the space between two faulty classes as faulty too. The rows
 * of the majority are kept, every one of them and once each.
 * <p>
 * The filter is told how many rows to synthesise as a percentage of the minority, which is set to what
 * leaves it at the size of the majority. The count is the filter's own to round: of a percentage that is
 * no whole multiple of a hundred, it synthesises the fractional part for a subset of the minority it
 * draws, so the two classes may come out a row apart. The draw is seeded, so two runs synthesise the same
 * rows.
 */
public class SmoteOversampler extends AbstractFilterBalancer {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Among how many of its nearest buggy neighbours a buggy row picks the one it is interpolated
     * towards. Five is the value the technique was defined with, and the filter's own default.
     */
    private static final int NEAREST_NEIGHBOURS = 5;

    /**
     * Which value of the label the filter synthesises rows of. Zero tells it to find the least common
     * one itself, which is the buggy class here but is read off the rows rather than assumed.
     */
    private static final String LEAST_COMMON_CLASS = "0";

    private static final double PERCENT = 100.0;

    private final int seed;

    public SmoteOversampler(int seed) {
        this.seed = seed;
    }

    @Override
    protected Filter resampler(Instances data) {
        SMOTE smote = new SMOTE();
        smote.setClassValue(LEAST_COMMON_CLASS);
        smote.setNearestNeighbors(NEAREST_NEIGHBOURS);
        smote.setRandomSeed(seed);
        smote.setPercentage(percentage(data));
        return smote;
    }

    /**
     * @param data the rows to balance
     * @return how many rows to synthesise, as a share of the minority, so that it comes out holding as
     * many rows as the majority holds now. The minority is the least common class that holds any rows,
     * which is the one the filter finds for itself
     */
    private static double percentage(Instances data) {
        int[] counts = classCounts(data);
        int minority = minorityCount(counts);
        return PERCENT * (majorityCount(counts) - minority) / minority;
    }

}
