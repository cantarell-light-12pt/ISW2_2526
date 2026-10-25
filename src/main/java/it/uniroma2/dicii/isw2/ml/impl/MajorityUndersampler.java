package it.uniroma2.dicii.isw2.ml.impl;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SpreadSubsample;

import java.io.Serial;

/**
 * Balances the rows by cutting the majority down to the size of the minority, through Weka's
 * {@code SpreadSubsample}.
 * <p>
 * What comes out is roughly twice the buggy rows and nothing else — of the some 2500 training rows of
 * the trimmed dataset, the four fifths that are not buggy are drawn down to the fifth that is, and the
 * rest are simply gone. That is the price of the technique rather than a fault in it: most of what a
 * model could have learnt about the classes nobody ever fixed a defect in is in the rows it discards,
 * so a recall bought here is usually paid for in precision. The draw is seeded, so two runs cut the same
 * rows.
 */
public class MajorityUndersampler extends AbstractFilterBalancer {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The largest ratio the filter may leave between the most and the least common class. One means they
     * come out the same size, which is the whole point of asking for it.
     */
    private static final double UNIFORM = 1.0;

    private final int seed;

    public MajorityUndersampler(int seed) {
        this.seed = seed;
    }

    @Override
    protected Filter resampler(Instances data) {
        SpreadSubsample subsample = new SpreadSubsample();
        subsample.setDistributionSpread(UNIFORM);
        subsample.setRandomSeed(seed);
        return subsample;
    }

}
