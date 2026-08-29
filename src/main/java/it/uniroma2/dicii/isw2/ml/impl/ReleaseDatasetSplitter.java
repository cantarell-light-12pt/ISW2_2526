package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.dataset.impl.CsvDatasetWriter;
import it.uniroma2.dicii.isw2.dataset.impl.WekaCsvDatasetReader;
import it.uniroma2.dicii.isw2.ml.DatasetSplitter;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import lombok.extern.slf4j.Slf4j;
import weka.core.Attribute;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cuts the dataset at a release: the earliest {@code project.ml.trainingFraction} of them are what a
 * model is trained on, and the later ones are what it is finally asked about.
 * <p>
 * At a release rather than at a row, and the difference is one of <b>direction in time</b>, not of how
 * much overlap is left. A long-lived class is in nearly every release, so it is in both halves either
 * way, and that is not a fault: when a predictor is asked about the next release, the earlier releases
 * of that same class are exactly the history it legitimately has. What a random row split does instead
 * is let the model read a class's <i>future</i> — release 8 of a class in the training set, release 7
 * of it in the test set — and a near-identical row carrying the answer, from a release that had not
 * happened yet, is information no predictor could ever have. Cutting here at a point in time is what
 * makes every training row older than every test row, which is the only arrangement under which the
 * figure means "how well would this have predicted what came next".
 * <p>
 * "Older" is by release <i>date</i>, which is not the same as by name: ZooKeeper maintains several
 * lines at once and published 3.4.0 before 3.3.4, so a cut at the thirteenth release ends the training
 * set at 3.4.0 and opens the test set at 3.3.4. That reads oddly and is right — the ordering below is
 * the one the file was written in, which is the one the versions were numbered in, which is by date.
 * <p>
 * The releases are ordered as the file holds them, which is the order they were published in — {@code
 * Workflow} measures the versions in the order {@code Version.getIndex()} gives them, which is by
 * release date, and appends the rows of each as it goes. That is an assumption about a file written
 * elsewhere, so it is checked rather than trusted: a release whose rows are not contiguous means the
 * file was not written release by release, and everything below it would be cutting the history at a
 * point that is not a point in time.
 */
@Slf4j
public class ReleaseDatasetSplitter implements DatasetSplitter {

    /**
     * Where the column naming the release a row belongs to sits, as {@link CsvDatasetWriter} writes it.
     */
    private static final int VERSION_COLUMN = 0;

    private final MlSettings settings;

    /**
     * The dataset the rows came from, named in the report of a failure.
     */
    private final Path file;

    public ReleaseDatasetSplitter(MlSettings settings, Path file) {
        this.settings = settings;
        this.file = file;
    }

    @Override
    public DatasetSplit split(Instances data) throws MlException {
        List<String> releases = releasesInOrder(data);
        int trainingReleases = trainingReleaseCount(releases.size());
        List<String> training = releases.subList(0, trainingReleases);
        List<String> test = releases.subList(trainingReleases, releases.size());
        log.info("Splitting {} releases into {} to train on, {} to {}, and {} to test on, {} to {}",
                releases.size(), trainingReleases, training.getFirst(), training.getLast(),
                test.size(), test.getFirst(), test.getLast());
        return new DatasetSplit(rowsOf(data, training), rowsOf(data, test), training, test);
    }

    /**
     * Reads which releases the dataset holds, and checks that it holds each of them in one piece.
     *
     * @param data the whole dataset, with its identity columns
     * @return the releases, in the order the file introduces them
     * @throws MlException if the dataset does not name a release per row, or holds one in two pieces
     */
    private List<String> releasesInOrder(Instances data) throws MlException {
        Attribute version = data.attribute(VERSION_COLUMN);
        if (version == null || !CsvDatasetWriter.IDENTITY_COLUMNS.getFirst().equals(version.name())) {
            throw new MlException("The dataset '" + file + "' does not open with the column naming the "
                    + "release of a row, which is what a release-aware split is cut on");
        }
        List<String> releases = new ArrayList<>();
        String current = null;
        for (int i = 0; i < data.numInstances(); i++) {
            String release = data.instance(i).stringValue(VERSION_COLUMN);
            if (release.equals(current)) {
                continue;
            }
            if (releases.contains(release)) {
                throw new MlException("Release '" + release + "' holds rows in two places in the "
                        + "dataset '" + file + "', which is written release by release. Its releases "
                        + "can no longer be ordered by where its rows are, so the history cannot be cut "
                        + "in two at a point in time");
            }
            releases.add(release);
            current = release;
        }
        if (releases.size() < 2) {
            throw new MlException("The dataset '" + file + "' holds " + releases.size()
                    + " release(s): there is no later release to test on");
        }
        return releases;
    }

    /**
     * @param releases how many releases the dataset holds
     * @return how many of them to train on, rounded to the nearest whole release — a release is trained
     * on or it is not — and always leaving at least one on each side
     */
    private int trainingReleaseCount(int releases) {
        int count = (int) Math.round(releases * settings.trainingFraction());
        int bounded = Math.clamp(count, 1, releases - 1);
        if (bounded != count) {
            log.warn("A training fraction of {} over {} releases leaves {} of them to train on, which "
                    + "would leave one of the two sets empty: training on {} instead",
                    settings.trainingFraction(), releases, count, bounded);
        }
        return bounded;
    }

    /**
     * Collects the rows of the given releases, and strips the columns naming them.
     *
     * @param data     the whole dataset, with its identity columns
     * @param releases the releases whose rows to keep
     * @return those rows, described by their metrics alone and labelled
     * @throws MlException if the identity columns cannot be dropped
     */
    private Instances rowsOf(Instances data, List<String> releases) throws MlException {
        Set<String> kept = new HashSet<>(releases);
        Instances rows = new Instances(data, 0);
        for (int i = 0; i < data.numInstances(); i++) {
            if (kept.contains(data.instance(i).stringValue(VERSION_COLUMN))) {
                rows.add(data.instance(i));
            }
        }
        try {
            return WekaCsvDatasetReader.withoutIdentityColumns(file, rows);
        } catch (DatasetException e) {
            throw new MlException("Unable to drop the columns naming the rows of one half of the "
                    + "dataset '" + file + "'", e);
        }
    }

}
