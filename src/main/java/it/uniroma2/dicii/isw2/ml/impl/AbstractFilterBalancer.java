package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.DatasetBalancer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import lombok.extern.slf4j.Slf4j;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;

import java.io.Serial;
import java.util.Arrays;

/**
 * Groups the work every technique shares: checking that the rows say which of their attributes is the
 * label, counting the two classes, keeping the caller's rows out of the filter's way and reporting what
 * the resampling did. Subclasses only name the filter that does it.
 * <p>
 * All three techniques go through here, the one that resamples nothing included, so that switching
 * between them cannot change whether a dataset is accepted at all — a run that fails under one setting
 * and succeeds under another says something about the settings and nothing about the data.
 */
@Slf4j
public abstract class AbstractFilterBalancer implements DatasetBalancer {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * How many classes have to hold rows before there is anything to balance.
     */
    private static final int CLASSES_TO_BALANCE = 2;

    @Override
    public final Instances balance(Instances data) throws MlException {
        checkLabelled(data);
        int[] before = classCounts(data);
        if (populatedClasses(before) < CLASSES_TO_BALANCE) {
            log.warn("The {} rows to balance hold one class, {}: leaving them as they are",
                    data.numInstances(), Arrays.toString(before));
            // A copy all the same, so that a caller cannot tell this case apart from the others by
            // whether the rows it got back were its own
            return new Instances(data);
        }
        Instances balanced = resample(data);
        log.info("{} turned {} rows into {}, the classes going from {} to {}",
                getClass().getSimpleName(), data.numInstances(), balanced.numInstances(),
                Arrays.toString(before), Arrays.toString(classCounts(balanced)));
        return balanced;
    }

    /**
     * Names the filter that does the resampling.
     *
     * @param data the rows about to be balanced, which a filter sizing its sample against them has to
     *             read before it is built
     * @return it, configured but not yet told what its input looks like — that is done below, so that
     * the one checked exception the Weka filtering API throws is caught in one place
     */
    protected abstract Filter resampler(Instances data);

    /**
     * Runs the filter over the rows.
     *
     * @param data the rows to balance, which this does not write into
     * @return them resampled, as a set of its own
     * @throws MlException if the filter cannot be applied to them
     */
    private Instances resample(Instances data) throws MlException {
        try {
            Filter filter = resampler(data);
            filter.setInputFormat(data);
            return Filter.useFilter(data, filter);
        } catch (Exception e) {
            // Weka reports everything from a filter refusing its input format to a class it cannot find
            // as a bare Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to balance " + data.numInstances() + " rows through "
                    + getClass().getSimpleName(), e);
        }
    }

    /**
     * @param data the rows to balance
     * @return how many rows each value of the label holds, in the order the label declares its values.
     * The rows whose label is missing are counted under none of them
     */
    protected static int[] classCounts(Instances data) {
        int[] counts = new int[data.numClasses()];
        for (int i = 0; i < data.numInstances(); i++) {
            Instance row = data.instance(i);
            if (!row.classIsMissing()) {
                counts[(int) row.classValue()]++;
            }
        }
        return counts;
    }

    /**
     * @param counts how many rows each class holds
     * @return how many the largest of them holds, which is the size the smaller ones are drawn up to
     * when the balancing is done by oversampling
     */
    protected static int majorityCount(int[] counts) {
        return Arrays.stream(counts).max().orElse(0);
    }

    /**
     * @param counts how many rows each class holds
     * @return how many the smallest of them holds, among those that hold any — a value the label
     * declares and the rows never take is no minority to draw up, since there is nothing to draw it from
     */
    protected static int minorityCount(int[] counts) {
        return Arrays.stream(counts).filter(count -> count > 0).min().orElse(0);
    }

    /**
     * @param counts how many rows each class holds
     * @return how many of them hold any. Read of the counts and not of the label, since a label is free
     * to declare a value the rows never take and a sample cannot be spread over a class that is not
     * there
     */
    protected static int populatedClasses(int[] counts) {
        return (int) Arrays.stream(counts).filter(count -> count > 0).count();
    }

    /**
     * Checks that the rows are there and say which of their attributes is the label.
     *
     * @param data the rows to balance
     * @throws MlException if they are missing, name no label, or name one that is a number rather than a
     *                     class
     */
    private static void checkLabelled(Instances data) throws MlException {
        if (data == null) {
            throw new MlException("There are no rows to balance");
        }
        if (data.classIndex() < 0) {
            throw new MlException("The " + data.numInstances() + " rows to balance do not say which of "
                    + "their attributes is the label, so the classes to bring to the same size cannot "
                    + "be told apart");
        }
        if (!data.classAttribute().isNominal()) {
            throw new MlException("The label '" + data.classAttribute().name() + "' of the rows to "
                    + "balance is a number and not a class: there is no pair of classes to bring to the "
                    + "same size");
        }
    }

}
