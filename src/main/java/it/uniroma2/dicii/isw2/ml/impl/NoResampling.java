package it.uniroma2.dicii.isw2.ml.impl;

import weka.core.Instances;
import weka.filters.AllFilter;
import weka.filters.Filter;

import java.io.Serial;

/**
 * Hands the rows through as they are, which is what every figure the project has reported so far was
 * measured on and is therefore what the two sampling techniques are read against.
 * <p>
 * It is a branch of the same template rather than a shortcut around it because a technique that is
 * configured away still has to behave like one: the rows come back as a set of the caller's own, under
 * the same header, and a dataset naming no label is refused here exactly as it is refused there. Doing
 * it through the filter that filters nothing is what keeps that promise without a second path through
 * this package.
 */
public class NoResampling extends AbstractFilterBalancer {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected Filter resampler(Instances data) {
        return new AllFilter();
    }

}
