package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks what the dataset trimmed to the earliest releases holds: the releases within the cutoff and
 * no other, named by the ordinal the versions were numbered with rather than by the order they were
 * handed over in, and the cutoff itself rounded to a whole release.
 */
public class FirstReleasesDatasetWriterTest {

    private static final String FIRST_PATH = "src/main/java/sample/First.java";

    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    private Path file;

    @Before
    public void setUp() {
        file = output.getRoot().toPath().resolve("PROJECT-trimmed.csv");
    }

    /**
     * A release is measured or it is not, so a fraction of the history has to come to a whole number
     * of them. 33% of the 61 releases ZooKeeper had tagged at the last run is 20.13, which is 20
     * releases.
     */
    @Test
    public void testTheCutoffRoundsToTheNearestWholeRelease() {
        assertEquals(20, FirstReleasesDatasetWriter.fractionOf(61, 0.33));
        assertEquals(3, FirstReleasesDatasetWriter.fractionOf(10, 0.33));
        assertEquals(4, FirstReleasesDatasetWriter.fractionOf(10, 0.35));
    }

    /**
     * A trimmed dataset with no release in it is no dataset, and one asked for more releases than the
     * project has is simply the whole history.
     */
    @Test
    public void testTheCutoffIsBoundedToTheReleasesThereAre() {
        assertEquals(1, FirstReleasesDatasetWriter.fractionOf(1, 0.33));
        assertEquals(1, FirstReleasesDatasetWriter.fractionOf(2, 0.1));
        assertEquals(10, FirstReleasesDatasetWriter.fractionOf(10, 1.0));
        assertEquals(10, FirstReleasesDatasetWriter.fractionOf(10, 5.0));
    }

    @Test
    public void testTheReleasesWithinTheCutoffAreWrittenAndTheOnesPastItAreNot() throws DatasetException, IOException {
        try (FirstReleasesDatasetWriter dataset = new FirstReleasesDatasetWriter(
                CsvDatasetWriter.open(file), 2)) {
            dataset.write(version("3.0.0", 1), report());
            dataset.write(version("3.1.0", 2), report());
            dataset.write(version("3.2.0", 3), report());
        }

        assertEquals(List.of("3.0.0", "3.1.0"), releases());
    }

    /**
     * Which releases are the earliest is read off the ordinal the versions were numbered by release
     * date with, and not off the order they reach the writer in: the extraction walks the numbered
     * list today, but only the index says what the first releases of a project are.
     */
    @Test
    public void testTheCutoffIsReadOffTheIndexAndNotOffTheOrderOfArrival() throws DatasetException, IOException {
        try (FirstReleasesDatasetWriter dataset = new FirstReleasesDatasetWriter(
                CsvDatasetWriter.open(file), 2)) {
            dataset.write(version("3.6.0", 5), report());
            dataset.write(version("3.5.10", 2), report());
        }

        assertEquals(List.of("3.5.10"), releases());
    }

    /**
     * An unnumbered version is one nothing can place. Keeping it is a mistake a reader of the file can
     * see, whereas a release silently missing from the trimmed dataset is not.
     */
    @Test
    public void testAnUnnumberedVersionIsKeptRatherThanDropped() throws DatasetException, IOException {
        try (FirstReleasesDatasetWriter dataset = new FirstReleasesDatasetWriter(
                CsvDatasetWriter.open(file), 1)) {
            dataset.write(version("3.0.0", 0), report());
        }

        assertEquals(List.of("3.0.0"), releases());
    }

    /**
     * The delegate is left to decide what the measures of a snapshot taken outside the release history
     * are worth, rather than having them dropped as a release past the cutoff.
     */
    @Test
    public void testTheMeasuresOfNoVersionReachTheDelegate() throws DatasetException {
        RecordingDatasetWriter delegate = new RecordingDatasetWriter();

        try (FirstReleasesDatasetWriter dataset = new FirstReleasesDatasetWriter(delegate, 1)) {
            dataset.write(null, report());
        }

        assertEquals(1, delegate.getWritten().size());
    }

    /**
     * The composition the workflow builds, and the property the trimmed dataset is read by: the same
     * measures reach both files, so the trimmed one is a prefix of the whole one, line for line. Two
     * files written out of two separate passes could differ in a way nothing in either of them says.
     */
    @Test
    public void testTheTrimmedDatasetIsAPrefixOfTheWholeOne() throws DatasetException, IOException {
        Path whole = output.getRoot().toPath().resolve("PROJECT.csv");
        CompositeDatasetWriter dataset = new CompositeDatasetWriter();
        try (dataset) {
            dataset.add(CsvDatasetWriter.open(whole));
            dataset.add(new FirstReleasesDatasetWriter(CsvDatasetWriter.open(file), 2));
            for (int index = 1; index <= 4; index++) {
                dataset.write(version("3." + index + ".0", index), report());
            }
        }

        List<String> trimmed = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(3, trimmed.size());
        assertEquals(5, Files.readAllLines(whole, StandardCharsets.UTF_8).size());
        assertEquals(Files.readAllLines(whole, StandardCharsets.UTF_8).subList(0, trimmed.size()), trimmed);
    }

    @Test
    public void testClosingTheWriterClosesTheDatasetItTrims() throws DatasetException {
        RecordingDatasetWriter delegate = new RecordingDatasetWriter();

        new FirstReleasesDatasetWriter(delegate, 1).close();

        assertTrue(delegate.isClosed());
    }

    /**
     * @return the released version each row of the trimmed dataset describes, in the order they were
     * written, the header left out
     */
    private List<String> releases() throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return lines.subList(1, lines.size()).stream()
                .map(line -> line.split(",", -1)[0])
                .toList();
    }

    private static MetricsReport report() {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);
        return report;
    }

    private static Version version(String name, int index) {
        Version version = new Version("id-" + name, name, true, false);
        version.setIndex(index);
        return version;
    }
}
