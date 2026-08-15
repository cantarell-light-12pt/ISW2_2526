package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.PmdRunner;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.SmellSeverity;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs PMD for real, over a snapshot holding one class that breaks a rule and one that breaks none, to
 * check that the container is invoked the way the report is then read.
 * <p>
 * Every test here is skipped where no Docker daemon can be reached, which is the same condition under
 * which the workflow leaves the smell metrics out of the dataset: a machine that cannot run PMD is not
 * a machine these tests can fail on.
 */
public class DockerPmdRunnerTest {

    /**
     * {@code UnusedPrivateField} and {@code ImmutableField} are both medium-priority rules of the
     * ruleset, so this class smells of at least two medium smells and of no blocker.
     */
    private static final String SMELLY_SOURCE = """
            package sample;

            public class Smelly {

                private String unused = "never read";

                public String greet() {
                    return "hello";
                }
            }
            """;

    private static final String CLEAN_SOURCE = """
            package sample;

            public interface Marker {
            }
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private PmdRunner runner;

    private Path root;

    @Before
    public void setUp() throws IOException {
        runner = new DockerPmdRunner(
                PropertiesManager.getInstance().getProperty("project.metrics.pmd.image"),
                PropertiesManager.getInstance().getProperty("project.metrics.pmd.ruleset"),
                Long.parseLong(PropertiesManager.getInstance().getProperty("project.metrics.pmd.timeoutSeconds")));
        Assume.assumeTrue("No Docker daemon could be reached", runner.isAvailable());
        root = folder.getRoot().toPath();
        Files.createDirectories(root.resolve("sample"));
        Files.writeString(root.resolve("sample/Smelly.java"), SMELLY_SOURCE);
        Files.writeString(root.resolve("sample/Marker.java"), CLEAN_SOURCE);
    }

    /**
     * The paths of the report are the ones the dataset keys its rows by, which is what lets the extractor
     * join what PMD found with what its siblings measured.
     */
    @Test
    public void testTheReportNamesTheSourcesRelativeToTheRoot() throws MetricsException {
        String report = runner.run(root, List.of(root.resolve("sample/Smelly.java")), "java-8");

        assertTrue(report, report.contains("\"filename\": \"sample/Smelly.java\""));
        assertTrue(report, report.contains("\"priority\""));
    }

    /**
     * The whole chain, from the container down to the metrics of a row.
     */
    @Test
    public void testTheSmellsOfASnapshotAreCounted() throws MetricsException {
        MetricsReport report = new PMDExtractor(SourceFilter.everything(), runner,
                new JavaVersionDetector("8")).extract(new Snapshot(root));

        assertEquals(2, report.size());
        ClassMetrics smelly = report.forPath("sample/Smelly.java");
        assertNotNull(smelly);
        assertTrue("The smelly class should smell of something", smelly.get(Metric.MS).orElseThrow() > 0);
        assertTrue(report.incompleteClasses(SmellSeverity.metrics()).isEmpty());
    }

    /**
     * A release whose sources cannot all be parsed still has to be measured: PMD reports the ones it
     * failed on and analyses the rest.
     */
    @Test
    public void testASourceThatCannotBeParsedDoesNotFailTheRun() throws MetricsException, IOException {
        Files.writeString(root.resolve("sample/Broken.java"), "package sample; class Broken { not java }");

        MetricsReport report = new PMDExtractor(SourceFilter.everything(), runner,
                new JavaVersionDetector("8")).extract(new Snapshot(root));

        assertEquals(3, report.size());
        assertEquals(0, report.forPath("sample/Broken.java").get(Metric.MS).orElseThrow(), 0.0001);
    }

    /**
     * The oldest releases of a long-lived project were written against a version of the language PMD
     * still has to be told about by name.
     */
    @Test
    public void testAnOldLanguageVersionIsAccepted() throws MetricsException {
        String report = runner.run(root, List.of(root.resolve("sample/Smelly.java")), "java-1.5");

        assertTrue(report, report.contains("\"pmdVersion\""));
    }
}
