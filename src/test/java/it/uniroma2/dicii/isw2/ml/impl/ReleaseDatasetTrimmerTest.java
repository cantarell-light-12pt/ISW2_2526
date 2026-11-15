package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.DatasetTrimmer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import org.junit.Test;
import weka.core.Instances;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the dataset is trimmed to its earliest releases, as the file orders them, that the count
 * rounds to a whole release, and that what is kept is still the dataset it came from: same header, the
 * columns naming each row still on, and some buggy row to learn from.
 */
public class ReleaseDatasetTrimmerTest {

    private static final List<String> RELEASES =
            List.of("1.0.0", "1.0.1", "1.1.0", "1.1.1", "1.2.0", "1.2.1");

    private static final int ROWS_PER_RELEASE = 10;

    private static final Path FILE = Path.of("PROJECT.csv");

    /**
     * A third of six releases is two, and they are the first two the file holds.
     */
    @Test
    public void testTheEarliestReleasesAreKept() throws MlException {
        Instances trimmed = trimmer(0.33).trim(releases(true));

        assertEquals(List.of("1.0.0", "1.0.1"), ReleaseRows.releasesInOrder(trimmed, FILE));
        assertEquals(2 * ROWS_PER_RELEASE, trimmed.numInstances());
    }

    /**
     * What is kept is still the dataset it came from: the label the nominal class it was read as, and
     * the columns naming each row still on, since the split reads the release off them.
     */
    @Test
    public void testTheHeaderOfTheWholeDatasetIsKept() throws MlException {
        Instances data = releases(true);
        Instances trimmed = trimmer(0.5).trim(data);

        assertTrue("The trimmed rows describe different columns", data.equalHeaders(trimmed));
        assertEquals("Version", trimmed.attribute(0).name());
        assertEquals("ClassName", trimmed.attribute(1).name());
        assertEquals("Buggy", trimmed.classAttribute().name());
        assertTrue(trimmed.classAttribute().isNominal());
    }

    @Test
    public void testAWholeFractionKeepsEveryRelease() throws MlException {
        Instances data = releases(true);
        assertEquals(data.numInstances(), trimmer(1.0).trim(data).numInstances());
    }

    /**
     * A release is kept or it is not, so a fraction of the history has to come to a whole number of
     * them. 33% of the 61 releases ZooKeeper had tagged at the last run is 20.13, which is 20 releases.
     */
    @Test
    public void testTheCountRoundsToTheNearestWholeRelease() {
        assertEquals(20, ReleaseDatasetTrimmer.fractionOf(61, 0.33));
        assertEquals(3, ReleaseDatasetTrimmer.fractionOf(10, 0.33));
        assertEquals(4, ReleaseDatasetTrimmer.fractionOf(10, 0.35));
    }

    /**
     * A trimmed dataset with no release in it is no dataset.
     */
    @Test
    public void testTheCountIsBoundedToTheReleasesThereAre() {
        assertEquals(1, ReleaseDatasetTrimmer.fractionOf(1, 0.33));
        assertEquals(1, ReleaseDatasetTrimmer.fractionOf(2, 0.1));
        assertEquals(10, ReleaseDatasetTrimmer.fractionOf(10, 1.0));
    }

    @Test
    public void testAFractionOutsideTheUnitIntervalIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> trimmer(0));
        assertThrows(IllegalArgumentException.class, () -> trimmer(1.5));
        assertThrows(IllegalArgumentException.class, () -> trimmer(Double.NaN));
    }

    /**
     * The releases are ordered by where their rows sit, which only means anything if the file was
     * written release by release.
     */
    @Test
    public void testADatasetHoldingAReleaseInTwoPlacesIsRefused() {
        MlException thrown = assertThrows(MlException.class, () -> trimmer(0.33).trim(releases(false)));
        assertTrue(thrown.getMessage().contains("two places"));
    }

    /**
     * The header declares the buggy label whatever rows are kept, so a trim leaving no buggy row would
     * go unnoticed until every per-class figure came out empty.
     */
    @Test
    public void testATrimLeavingNoBuggyRowIsRefused() {
        Instances data = releases(true);
        for (int i = 0; i < ROWS_PER_RELEASE; i++) {
            data.instance(i).setClassValue(SyntheticInstances.NOT_BUGGY);
        }
        DatasetTrimmer trimmer = trimmer(0.1);
        MlException thrown = assertThrows(MlException.class, () -> trimmer.trim(data));
        assertTrue(thrown.getMessage().contains("no buggy class"));
    }

    /**
     * @param fraction the share of the releases to keep
     * @return a trimmer keeping it
     */
    private static DatasetTrimmer trimmer(double fraction) {
        return new ReleaseDatasetTrimmer(fraction, SyntheticInstances.BUGGY, FILE);
    }

    /**
     * @param contiguous whether each release holds its rows in one piece
     * @return the dataset as it comes off the file
     */
    private static Instances releases(boolean contiguous) {
        return SyntheticInstances.withReleases(RELEASES, ROWS_PER_RELEASE, contiguous);
    }

}
