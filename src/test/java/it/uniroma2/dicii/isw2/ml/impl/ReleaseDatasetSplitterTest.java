package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.DatasetSplitter;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.DatasetSplit;
import org.junit.Test;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the history is cut at a release, that the cut falls where the configured share puts it,
 * and that neither half carries the columns naming its rows into a classifier.
 */
public class ReleaseDatasetSplitterTest {

    private static final List<String> RELEASES =
            List.of("1.0.0", "1.0.1", "1.1.0", "1.1.1", "1.2.0", "1.2.1");

    private static final int ROWS_PER_RELEASE = 10;

    private static final Path FILE = Path.of("PROJECT-trimmed.csv");

    /**
     * Two thirds of six releases is four, leaving two to test on.
     */
    @Test
    public void testTheEarliestReleasesAreTrainedOnAndTheLatestTestedOn() throws MlException {
        DatasetSplit split = splitter(0.67).split(releases(true));

        assertEquals(List.of("1.0.0", "1.0.1", "1.1.0", "1.1.1"), split.trainingReleases());
        assertEquals(List.of("1.2.0", "1.2.1"), split.testReleases());
        assertEquals(4 * ROWS_PER_RELEASE, split.training().numInstances());
        assertEquals(2 * ROWS_PER_RELEASE, split.test().numInstances());
    }

    /**
     * Every row goes to exactly one side. A long-lived class is still on both, which is no fault — its
     * earlier releases are the history a predictor legitimately has — but no single row may be counted
     * twice or dropped.
     */
    @Test
    public void testEveryRowGoesToExactlyOneSide() throws MlException {
        DatasetSplit split = splitter(0.67).split(releases(true));
        assertEquals(releases(true).numInstances(),
                split.training().numInstances() + split.test().numInstances());
    }

    /**
     * What cutting at a release actually buys: a direction in time. Every training row has to come from
     * a release older than every test row's, which is the only arrangement under which the figures mean
     * "how well would this have predicted what came next".
     */
    @Test
    public void testEveryTrainingReleaseIsOlderThanEveryTestRelease() throws MlException {
        DatasetSplit split = splitter(0.67).split(releases(true));
        int lastTrained = RELEASES.indexOf(split.trainingReleases().getLast());
        int firstTested = RELEASES.indexOf(split.testReleases().getFirst());
        assertTrue("The cut does not fall at a point in time", lastTrained < firstTested);
        for (String release : split.testReleases()) {
            assertFalse("A release is on both sides of the cut",
                    split.trainingReleases().contains(release));
        }
    }

    /**
     * The columns naming a row identify it, they do not describe it: kept as attributes, the name of
     * the class alone would let a model memorise which classes of this one project were found faulty.
     */
    @Test
    public void testNeitherHalfKeepsTheColumnsNamingItsRows() throws MlException {
        DatasetSplit split = splitter(0.67).split(releases(true));
        for (Instances half : List.of(split.training(), split.test())) {
            assertEquals("Buggy", half.classAttribute().name());
            assertEquals(SyntheticInstances.NOISE.size() + 2, half.numAttributes());
            assertEquals(SyntheticInstances.SIGNAL, half.attribute(0).name());
        }
        assertTrue("The two halves have to describe the same columns to be comparable",
                split.training().equalHeaders(split.test()));
    }

    @Test
    public void testTheCutFollowsTheConfiguredShare() throws MlException {
        assertEquals(3, splitter(0.5).split(releases(true)).trainingReleases().size());
        assertEquals(5, splitter(0.8).split(releases(true)).trainingReleases().size());
    }

    /**
     * A share rounding to every release or to none would leave one of the two sets empty, and there
     * would be nothing to train on or nothing to report.
     */
    @Test
    public void testNeitherSideIsEverLeftEmpty() throws MlException {
        assertEquals(RELEASES.size() - 1, splitter(0.99).split(releases(true)).trainingReleases().size());
        assertEquals(1, splitter(0.01).split(releases(true)).trainingReleases().size());
    }

    /**
     * The releases are ordered by where their rows sit, which only means anything if the file was
     * written release by release. A file that was not has to say so, rather than have the history cut
     * at a point that is not a point in time.
     */
    @Test
    public void testADatasetHoldingAReleaseInTwoPlacesIsRefused() {
        MlException thrown =
                assertThrows(MlException.class, () -> splitter(0.67).split(releases(false)));
        assertTrue(thrown.getMessage().contains("two places"));
    }

    @Test
    public void testADatasetOfASingleReleaseHasNothingToTestOn() {
        Instances single = SyntheticInstances.withReleases(List.of("1.0.0"), ROWS_PER_RELEASE, true);
        MlException thrown = assertThrows(MlException.class, () -> splitter(0.67).split(single));
        assertTrue(thrown.getMessage().contains("no later release"));
    }

    /**
     * @param trainingFraction the share of the releases to train on
     * @return a splitter cutting there
     */
    private static DatasetSplitter splitter(double trainingFraction) {
        MlSettings settings = new MlSettings(List.of(ClassifierKind.NAIVE_BAYES), trainingFraction, 2, 1,
                SyntheticInstances.BUGGY, 1, 10, 1);
        return new ReleaseDatasetSplitter(settings, FILE);
    }

    /**
     * @param contiguous whether each release holds its rows in one piece
     * @return the dataset as it comes off the file
     */
    private static Instances releases(boolean contiguous) {
        return SyntheticInstances.withReleases(RELEASES, ROWS_PER_RELEASE, contiguous);
    }

}
