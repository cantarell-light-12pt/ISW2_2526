package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.DatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import lombok.extern.slf4j.Slf4j;
import weka.core.Attribute;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.List;

/**
 * Keeps the earliest {@code project.ml.trimFraction} of the releases the dataset holds, and drops the
 * rest.
 * <p>
 * The buggy label of a release is only as good as the defects reported against it, and the last
 * releases of a project have had the least time to have any reported. Learning from the tail would be
 * learning that the recent releases are clean, which is a fact about the calendar and not about the
 * code — the snoring effect.
 * <p>
 * "Earliest" is by release <i>date</i>, not by name: a project maintaining several lines at once
 * publishes 3.5.10 after 3.6.0. The releases are ordered as the file holds them, which is the order they
 * were published in, and {@link ReleaseRows} checks that the file was written release by release. The
 * releases counted are the ones the dataset holds, so a release that could not be measured is neither
 * kept nor counted.
 * <p>
 * The rows kept keep the header of the whole dataset, the columns naming each row included: the label
 * stays the nominal attribute it was read as, with its values in the order the whole file introduced
 * them, and the trimmed rows can be written out exactly as the whole would be.
 */
@Slf4j
public class ReleaseDatasetTrimmer implements DatasetTrimmer {

    private final double fraction;

    /**
     * The label of the buggy class, as it reads in the dataset.
     */
    private final String positiveClass;

    /**
     * The dataset the rows came from, named in the report of a failure.
     */
    private final Path file;

    /**
     * @param fraction      which share of the releases to keep, the earliest first
     * @param positiveClass the label of the buggy class, as it reads in the dataset
     * @param file          the dataset the rows come from, named in the report of a failure
     * @throws IllegalArgumentException if the share does not lie in (0, 1]
     */
    public ReleaseDatasetTrimmer(double fraction, String positiveClass, Path file) {
        // Written as what is rejected rather than as the negation of what is accepted, so that NaN,
        // which fails every comparison, is rejected as well
        if (!(fraction > 0 && fraction <= 1)) {
            throw new IllegalArgumentException("The trimmed dataset keeps a share of the releases, so the "
                    + "trim fraction lies in (0, 1], not " + fraction);
        }
        this.fraction = fraction;
        this.positiveClass = positiveClass;
        this.file = file;
    }

    /**
     * How many releases a fraction of the release history comes to, rounded to the nearest whole
     * release: a release is kept or it is not, and 33% of the 61 releases ZooKeeper had tagged at the
     * last run is 20.13 of them.
     * <p>
     * The count is never less than one release — a trimmed dataset with nothing in it is no dataset,
     * and rounding a small enough history down would leave one — and never more than the whole
     * history.
     *
     * @param releases how many releases the dataset holds
     * @param fraction which part of them to keep, between 0 and 1
     * @return how many of the earliest ones that comes to
     */
    public static int fractionOf(int releases, double fraction) {
        long kept = Math.round(releases * fraction);
        return Math.clamp(kept, 1, releases);
    }

    @Override
    public Instances trim(Instances data) throws MlException {
        List<String> releases = ReleaseRows.releasesInOrder(data, file);
        if (releases.isEmpty()) {
            throw new MlException("The dataset '" + file + "' holds no release: there is nothing to trim");
        }
        List<String> kept = releases.subList(0, fractionOf(releases.size(), fraction));
        Instances trimmed = ReleaseRows.of(data, kept);
        checkBuggyRows(trimmed);
        log.info("Trimmed the {} releases of the dataset to the {} earliest, {} to {}: {} rows of {}",
                releases.size(), kept.size(), kept.getFirst(), kept.getLast(),
                trimmed.numInstances(), data.numInstances());
        return trimmed;
    }

    /**
     * Checks that the releases kept hold a class to learn: the header still declares the buggy label
     * whatever rows are kept, so it is the rows that have to be asked.
     *
     * @param trimmed the rows kept, with the label set as the class
     * @throws MlException if none of them is labelled buggy
     */
    private void checkBuggyRows(Instances trimmed) throws MlException {
        Attribute label = trimmed.classAttribute();
        int buggy = label.indexOfValue(positiveClass);
        if (buggy < 0) {
            throw new MlException("The label '" + label.name() + "' of the dataset '" + file
                    + "' takes no value '" + positiveClass + "'");
        }
        for (int i = 0; i < trimmed.numInstances(); i++) {
            if (!trimmed.instance(i).classIsMissing() && (int) trimmed.instance(i).classValue() == buggy) {
                return;
            }
        }
        throw new MlException("No row of the releases the dataset '" + file + "' is trimmed to is "
                + "labelled '" + positiveClass + "': there is no buggy class to learn");
    }

}
