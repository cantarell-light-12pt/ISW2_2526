package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks the shape of the file the dataset is written to: every row describes one class of one
 * released version, is named by those two alone, carries a field per metric whether it was measured
 * or not, and reaches the disk as soon as the version it belongs to has been measured.
 */
public class CsvDatasetWriterTest {

    /**
     * The two columns naming a row, before the ones holding the measures.
     */
    private static final int IDENTITY_COLUMNS = 2;

    private static final int COLUMNS = IDENTITY_COLUMNS + Metric.values().length;

    private static final String FIRST_PATH = "src/main/java/sample/First.java";
    private static final String SECOND_PATH = "src/main/java/sample/Second.java";

    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    private Path file;
    private Locale defaultLocale;

    @Before
    public void setUp() {
        file = output.getRoot().toPath().resolve("PROJECT.csv");
        defaultLocale = Locale.getDefault();
    }

    /**
     * Restores the locale one of the tests changes, so that it cannot leak into the rest of the suite.
     */
    @After
    public void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void testTheHeaderNamesTheIdentityColumnsAndEveryMetric() throws DatasetException, IOException {
        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            assertEquals(0, dataset.getRows());
        }

        List<String> lines = lines();
        assertEquals(1, lines.size());
        String[] header = lines.getFirst().split(",", -1);
        assertEquals(COLUMNS, header.length);
        assertEquals(List.of("Version", "ClassName"), List.of(header).subList(0, IDENTITY_COLUMNS));
        for (int i = 0; i < Metric.values().length; i++) {
            assertEquals(Metric.values()[i].name(), header[IDENTITY_COLUMNS + i]);
        }
    }

    /**
     * A row is named by the released version and the qualified name of the class, and by nothing
     * else: neither the ordinal index of the version nor the path of the source file is a column, the
     * first being an artefact of the numbering and the second naming the same class differently
     * across the releases that moved it.
     */
    @Test
    public void testAClassBecomesOneRowNamedByItsVersionAndItsQualifiedName() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);

        write(version("3.5.0", 2), report);

        List<String> lines = lines();
        assertEquals(2, lines.size());
        String[] row = lines.get(1).split(",", -1);
        assertEquals(COLUMNS, row.length);
        assertEquals("3.5.0", row[0]);
        assertEquals("sample.First", row[1]);
        assertEquals("42", cell(row, Metric.LOC));
        assertFalse("the path of the source file is no column of the dataset", lines.get(1).contains(FIRST_PATH));
        assertFalse("the index of the version is no column of the dataset", lines.get(1).startsWith("2,"));
    }

    /**
     * The two columns naming a row have to tell every row of the dataset apart, which is what lets a
     * label, or the measures of another release, be joined onto it.
     */
    @Test
    public void testTheVersionAndTheClassNameIdentifyEveryRow() throws DatasetException, IOException {
        MetricsReport first = new MetricsReport();
        first.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);
        first.forClass(SECOND_PATH, "sample.Second").set(Metric.LOC, 13);
        MetricsReport second = new MetricsReport();
        second.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 50);

        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            dataset.write(version("3.5.0", 1), first);
            dataset.write(version("3.6.0", 2), second);
        }

        List<String> keys = lines().subList(1, 4).stream()
                .map(line -> line.split(",", -1))
                .map(row -> row[0] + " " + row[1])
                .toList();
        assertEquals(List.of("3.5.0 sample.First", "3.5.0 sample.Second", "3.6.0 sample.First"), keys);
        assertEquals(keys.size(), Set.copyOf(keys).size());
    }

    /**
     * The reason the rows of a version are written as soon as it has been measured, rather than when
     * the whole history has: a run that never reaches its last release still leaves behind the
     * releases it did measure. Reading the file while the writer is still open is what pins it down.
     */
    @Test
    public void testRowsAreOnDiskBeforeTheWriterIsClosed() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);

        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            dataset.write(version("3.5.0", 1), report);

            assertEquals(2, lines().size());
            assertEquals(1, dataset.getRows());
        }
    }

    @Test
    public void testVersionsAreAppendedInTheOrderTheyAreWritten() throws DatasetException, IOException {
        MetricsReport first = new MetricsReport();
        first.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);
        MetricsReport second = new MetricsReport();
        second.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 50);
        second.forClass(SECOND_PATH, "sample.Second").set(Metric.LOC, 13);

        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            dataset.write(version("3.5.0", 1), first);
            dataset.write(version("3.6.0", 2), second);
            assertEquals(3, dataset.getRows());
        }

        List<String> lines = lines();
        assertEquals(4, lines.size());
        assertEquals(List.of("3.5.0", "3.6.0", "3.6.0"),
                lines.subList(1, lines.size()).stream().map(line -> line.split(",", -1)[0]).toList());
    }

    /**
     * Most of the metrics are counts, and a dataset reading {@code 42.0} where the class has 42 lines
     * is noise; the few that are averages, on the other hand, do have to keep their decimals.
     */
    @Test
    public void testCountsHaveNoDecimalPartAndAveragesDo() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);
        report.forPath(FIRST_PATH).set(Metric.NM, 0);
        report.forPath(FIRST_PATH).set(Metric.WCYC, 2.5);
        report.forPath(FIRST_PATH).set(Metric.WNBF, 1.0 / 3);

        write(version("3.5.0", 1), report);

        String[] row = lines().get(1).split(",", -1);
        assertEquals("42", cell(row, Metric.LOC));
        assertEquals("0", cell(row, Metric.NM));
        assertEquals("2.5", cell(row, Metric.WCYC));
        assertEquals("0.3333", cell(row, Metric.WNBF));
    }

    /**
     * A run performed where no container could be started measures no code smell, and the columns
     * holding them are then empty rather than absent: the file has the same shape whether PMD could
     * run or not, so that two runs stay comparable.
     */
    @Test
    public void testUnmeasuredMetricsAreLeftEmpty() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);

        write(version("3.5.0", 1), report);

        String[] row = lines().get(1).split(",", -1);
        assertEquals(COLUMNS, row.length);
        assertEquals("", cell(row, Metric.BS));
        assertEquals("", cell(row, Metric.IS));
    }

    /**
     * No version name and no class name holds a separator today, but a field that did would split its
     * row in two and corrupt the dataset without failing anything.
     */
    @Test
    public void testFieldsHoldingTheSeparatorAreQuoted() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.Odd,\"Name").set(Metric.LOC, 42);

        write(version("3.5.0", 1), report);

        String row = lines().get(1);
        assertTrue(row.contains("\"sample.Odd,\"\"Name\""));
        assertEquals(COLUMNS, row.replace("\"sample.Odd,\"\"Name\"", "name").split(",", -1).length);
    }

    @Test
    public void testTheOutputDirectoryIsCreatedIfMissing() throws DatasetException, IOException {
        file = output.getRoot().toPath().resolve("nested").resolve("deeper").resolve("PROJECT.csv");

        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            assertEquals(0, dataset.getRows());
        }

        assertEquals(1, lines().size());
    }

    /**
     * The failure this guards against is silent and only happens on someone else's machine: a locale
     * whose decimal separator is a comma would render every average as a second field, shifting the
     * measures of every metric after it by one column.
     */
    @Test
    public void testMeasuresDoNotDependOnTheDefaultLocale() throws DatasetException, IOException {
        Locale.setDefault(Locale.ITALY);
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.WCYC, 2.5);

        write(version("3.5.0", 1), report);

        String[] row = lines().get(1).split(",", -1);
        assertEquals(COLUMNS, row.length);
        assertEquals("2.5", cell(row, Metric.WCYC));
    }

    /**
     * A snapshot can be measured outside the release history, and its version is then null. Those
     * measures are no rows of a per-release dataset, and saying so beats writing a row nothing
     * identifies.
     */
    @Test
    public void testTheMeasuresOfNoVersionAreRejected() throws DatasetException, IOException {
        MetricsReport report = new MetricsReport();
        report.forClass(FIRST_PATH, "sample.First").set(Metric.LOC, 42);

        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            assertThrows(DatasetException.class, () -> dataset.write(null, report));
        }

        assertEquals(1, lines().size());
    }

    @Test
    public void testAFileThatCannotBeOpenedFails() throws IOException {
        Path regularFile = output.newFile("occupied").toPath();

        assertThrows(DatasetException.class, () -> CsvDatasetWriter.open(regularFile.resolve("PROJECT.csv")));
    }

    /**
     * Writes a single version to the dataset and closes it, which is what most of the tests need
     * before reading the file back.
     */
    private void write(Version version, MetricsReport report) throws DatasetException {
        try (CsvDatasetWriter dataset = CsvDatasetWriter.open(file)) {
            dataset.write(version, report);
        }
    }

    private List<String> lines() throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    /**
     * @param row    the fields of a row of the dataset
     * @param metric the metric whose column is wanted
     * @return the value that row holds for it
     */
    private static String cell(String[] row, Metric metric) {
        return row[IDENTITY_COLUMNS + metric.ordinal()];
    }

    private static Version version(String name, int index) {
        Version version = new Version("id-" + name, name, true, false);
        version.setIndex(index);
        return version;
    }
}
