package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.PmdRunner;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.SmellSeverity;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Counts the smells of a snapshot whose report is written by hand, so that what the extractor does with
 * a report is tested without PMD, and therefore without a container, having to run at all.
 */
public class PMDExtractorTest {

    private static final double DELTA = 0.0001;

    private static final String SOURCE = """
            package sample;

            public class Sample {
            }
            """;

    /**
     * A report naming both classes of the snapshot: one violation of every severity on the first, a
     * single medium one on the second.
     */
    private static final String REPORT = """
            {
              "formatVersion": 0,
              "pmdVersion": "7.26.0",
              "files": [
                {
                  "filename": "sample/First.java",
                  "violations": [
                    {"rule": "OneBlocker", "priority": 1},
                    {"rule": "OneHigh", "priority": 2},
                    {"rule": "OneMedium", "priority": 3},
                    {"rule": "AnotherMedium", "priority": 3},
                    {"rule": "OneMinor", "priority": 4},
                    {"rule": "OneInfo", "priority": 5}
                  ]
                },
                {
                  "filename": "sample/Second.java",
                  "violations": [
                    {"rule": "OneMedium", "priority": 3}
                  ]
                }
              ],
              "suppressedViolations": [],
              "processingErrors": [],
              "configurationErrors": []
            }
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path root;

    @Before
    public void setUp() throws IOException {
        root = folder.getRoot().toPath();
        Files.createDirectories(root.resolve("sample"));
        Files.writeString(root.resolve("sample/First.java"), SOURCE);
        Files.writeString(root.resolve("sample/Second.java"), SOURCE);
    }

    @Test
    public void testViolationsAreCountedUnderTheMetricOfTheirSeverity() throws MetricsException {
        MetricsReport report = extract(REPORT);

        ClassMetrics first = report.forPath("sample/First.java");
        assertNotNull(first);
        assertEquals(1, first.get(Metric.BS).orElseThrow(), DELTA);
        assertEquals(1, first.get(Metric.HS).orElseThrow(), DELTA);
        assertEquals(2, first.get(Metric.MS).orElseThrow(), DELTA);
        assertEquals(1, first.get(Metric.LS).orElseThrow(), DELTA);
        assertEquals(1, first.get(Metric.IS).orElseThrow(), DELTA);
    }

    /**
     * PMD only names the sources it found something in, so a class it says nothing about is a clean
     * class and has to be a row of zeros rather than a row the composite would report as unmeasured.
     */
    @Test
    public void testAClassNoRuleWasBrokenByIsCountedAsClean() throws MetricsException {
        MetricsReport report = extract("""
                {"formatVersion": 0, "pmdVersion": "7.26.0", "files": [], "processingErrors": []}
                """);

        assertEquals(2, report.size());
        for (ClassMetrics metrics : report.getClasses()) {
            SmellSeverity.metrics().forEach(metric ->
                    assertEquals(0, metrics.get(metric).orElseThrow(), DELTA));
        }
    }

    @Test
    public void testEveryClassIsMeasuredOnEverySmellMetric() throws MetricsException {
        MetricsReport report = extract(REPORT);

        assertTrue(report.incompleteClasses(SmellSeverity.metrics()).isEmpty());
    }

    /**
     * The row of the dataset is the source file, named the way every other extractor names it.
     */
    @Test
    public void testClassesAreKeyedByTheirPathRelativeToTheRoot() throws MetricsException {
        MetricsReport report = extract(REPORT);

        ClassMetrics first = report.forPath("sample/First.java");
        assertNotNull(first);
        assertEquals("sample/First.java", first.getPath());
        assertEquals("First", first.getClassName());
    }

    @Test
    public void testOnlyTheSourcesTheFilterAcceptsAreMeasured() throws MetricsException {
        SourceFilter onlyFirst = relativePath -> relativePath.endsWith("First.java");
        MetricsReport report = extract(REPORT, onlyFirst);

        assertEquals(1, report.size());
        assertNotNull(report.forPath("sample/First.java"));
        assertNull(report.forPath("sample/Second.java"));
    }

    /**
     * PMD is handed the very sources the filter accepted, but a report naming one it was not asked
     * about must not become a row the other extractors know nothing of.
     */
    @Test
    public void testSmellsReportedOnASourceThatIsNoRowAreDropped() throws MetricsException {
        MetricsReport report = extract("""
                {"formatVersion": 0, "pmdVersion": "7.26.0", "files": [
                  {"filename": "sample/Unknown.java", "violations": [{"rule": "R", "priority": 1}]}
                ], "processingErrors": []}
                """);

        assertEquals(2, report.size());
        assertNull(report.forPath("sample/Unknown.java"));
        assertEquals(0, report.forPath("sample/First.java").get(Metric.BS).orElseThrow(), DELTA);
    }

    /**
     * A priority outside the five PMD defines is no severity the catalogue knows, and counting it under
     * one of them would make the row say something the report did not.
     */
    @Test
    public void testAViolationOfAnUnknownPriorityIsNotCounted() throws MetricsException {
        MetricsReport report = extract("""
                {"formatVersion": 0, "pmdVersion": "7.26.0", "files": [
                  {"filename": "sample/First.java", "violations": [
                    {"rule": "R", "priority": 0}, {"rule": "R", "priority": 9},
                    {"rule": "R", "priority": 2}
                  ]}
                ], "processingErrors": []}
                """);

        ClassMetrics first = report.forPath("sample/First.java");
        assertEquals(1, first.get(Metric.HS).orElseThrow(), DELTA);
        assertEquals(0, first.get(Metric.BS).orElseThrow(), DELTA);
        assertEquals(0, first.get(Metric.IS).orElseThrow(), DELTA);
    }

    /**
     * A source PMD could not parse costs the run nothing but the smells of that one class, which is
     * counted as clean rather than dropped: the row is still evidence of what the snapshot held.
     */
    @Test
    public void testASourcePmdCouldNotParseIsStillARowOfTheDataset() throws MetricsException {
        MetricsReport report = extract("""
                {"formatVersion": 0, "pmdVersion": "7.26.0", "files": [], "processingErrors": [
                  {"filename": "sample/Second.java", "message": "ParseException"}
                ]}
                """);

        assertEquals(2, report.size());
        assertEquals(0, report.forPath("sample/Second.java").get(Metric.MS).orElseThrow(), DELTA);
    }

    @Test
    public void testTheSourcesHandedToPmdAreTheOnesTheFilterAccepted() throws MetricsException {
        RecordingRunner runner = new RecordingRunner(REPORT);
        new PMDExtractor(relativePath -> relativePath.endsWith("First.java"), runner,
                new JavaVersionDetector("8")).extract(new Snapshot(root));

        assertEquals(List.of(root.resolve("sample/First.java")), runner.analysed);
    }

    /**
     * The version the sources are parsed as is read from the release itself, not fixed for the run.
     */
    @Test
    public void testTheDetectedJavaVersionIsHandedToPmd() throws MetricsException, IOException {
        Files.writeString(root.resolve("pom.xml"), "<project><properties>"
                + "<maven.compiler.source>1.6</maven.compiler.source></properties></project>");
        RecordingRunner runner = new RecordingRunner(REPORT);
        new PMDExtractor(SourceFilter.everything(), runner, new JavaVersionDetector("8"))
                .extract(new Snapshot(root));

        assertEquals("java-6", runner.languageVersion);
    }

    @Test
    public void testAReportThatCannotBeReadFailsTheSnapshot() {
        assertThrows(MetricsException.class, () -> extract("this is no report"));
    }

    @Test
    public void testASnapshotThatIsNoDirectoryFailsTheExtraction() {
        PMDExtractor extractor = new PMDExtractor(SourceFilter.everything(), new RecordingRunner(REPORT),
                new JavaVersionDetector("8"));

        assertThrows(MetricsException.class, () -> extractor.extract(new Snapshot(root.resolve("missing"))));
    }

    @Test
    public void testItMeasuresTheSmellMetricsOfTheCatalogue() {
        PMDExtractor extractor = new PMDExtractor(SourceFilter.everything(), new RecordingRunner(REPORT),
                new JavaVersionDetector("8"));

        assertEquals(SmellSeverity.metrics(), extractor.extractedMetrics());
    }

    private MetricsReport extract(String report) throws MetricsException {
        return extract(report, SourceFilter.everything());
    }

    private MetricsReport extract(String report, SourceFilter filter) throws MetricsException {
        return new PMDExtractor(filter, new RecordingRunner(report), new JavaVersionDetector("8"))
                .extract(new Snapshot(root));
    }

    /**
     * Stands in for PMD, handing back a report written by hand and remembering what it was asked to
     * analyse.
     */
    private static class RecordingRunner implements PmdRunner {

        private final String report;

        private final List<Path> analysed = new ArrayList<>();

        private String languageVersion;

        private RecordingRunner(String report) {
            this.report = report;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String run(Path root, List<Path> sources, String languageVersion) {
            this.analysed.addAll(sources);
            this.languageVersion = languageVersion;
            return report;
        }
    }
}
