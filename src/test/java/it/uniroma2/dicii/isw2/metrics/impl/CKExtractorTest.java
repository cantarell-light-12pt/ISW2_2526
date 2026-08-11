package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Measures a two-class snapshot whose metrics can be counted by hand, to check that the values CK
 * produces end up under the expected metric and against the expected class.
 */
public class CKExtractorTest {

    private static final double DELTA = 0.0001;

    private static final String BASE_SOURCE = """
            package sample;

            public class Base {

                protected int field;

                public void doIt() {
                    // nothing to do
                }
            }
            """;

    /**
     * {@code classify} is worth 4: one for the method itself, two for the condition of the {@code if}
     * and its {@code &&}, and one for the {@code for}. {@code getLabel} is worth 1, so the class has a
     * maximum complexity of 4 and a weighted one of 2.5.
     */
    private static final String CHILD_SOURCE = """
            package sample;

            public class Child extends Base {

                private int counter;

                private String label;

                public int classify(int value) {
                    if (value > 0 && value < 10) {
                        return 1;
                    }
                    for (int i = 0; i < value; i++) {
                        counter++;
                    }
                    return 0;
                }

                public String getLabel() {
                    return label;
                }
            }
            """;

    @Rule
    public TemporaryFolder sources = new TemporaryFolder();

    private CKExtractor extractor;
    private MetricsReport report;

    @Before
    public void setUp() throws IOException, MetricsException {
        extractor = new CKExtractor();
        Path packageDirectory = sources.newFolder("sample").toPath();
        Files.writeString(packageDirectory.resolve("Base.java"), BASE_SOURCE);
        Files.writeString(packageDirectory.resolve("Child.java"), CHILD_SOURCE);
        report = extractor.extract(sources.getRoot().toPath());
    }

    @Test
    public void testExtractedMetricsAreTheDeclaredOnes() {
        for (ClassMetrics metrics : report.getClasses()) {
            assertEquals(extractor.extractedMetrics(), metrics.getValues().keySet());
        }
    }

    @Test
    public void testClassesAreKeyedByPathRelativeToTheRoot() {
        assertEquals(2, report.size());
        assertNotNull(report.forPath("sample/Base.java"));
        assertNotNull(report.forPath("sample/Child.java"));
        assertEquals("sample.Child", report.forPath("sample/Child.java").getClassName());
    }

    @Test
    public void testInheritanceMetrics() {
        assertMetric(1, "sample/Base.java", Metric.NOC);
        assertMetric(0, "sample/Child.java", Metric.NOC);
        assertMetric(2, "sample/Child.java", Metric.DIT);
    }

    @Test
    public void testSizeMetrics() {
        assertMetric(2, "sample/Child.java", Metric.NM);
        assertMetric(2, "sample/Child.java", Metric.NA);
        assertMetric(1, "sample/Base.java", Metric.NM);
        assertMetric(1, "sample/Base.java", Metric.NA);
        assertTrue(report.forPath("sample/Child.java").get(Metric.LOC).orElseThrow() > 0);
    }

    @Test
    public void testComplexityMetrics() {
        assertMetric(4, "sample/Child.java", Metric.MCC);
        assertMetric(2.5, "sample/Child.java", Metric.WCC);
        assertMetric(1, "sample/Base.java", Metric.MCC);
        assertMetric(1, "sample/Base.java", Metric.WCC);
    }

    @Test
    public void testEmptyDirectoryProducesAnEmptyReport() throws MetricsException, IOException {
        assertEquals(0, extractor.extract(sources.newFolder("empty").toPath()).size());
    }

    @Test
    public void testMissingDirectoryIsRejected() {
        assertThrows(MetricsException.class, () -> extractor.extract(sources.getRoot().toPath().resolve("absent")));
        assertThrows(MetricsException.class, () -> extractor.extract(null));
    }

    @Test
    public void testUnmeasuredMetricIsEmpty() {
        assertNull(report.forPath("sample/Missing.java"));
        assertTrue(report.forPath("sample/Base.java").get(Metric.LOC).isPresent());
    }

    private void assertMetric(double expected, String path, Metric metric) {
        ClassMetrics metrics = report.forPath(path);
        assertNotNull("No metrics were measured on " + path, metrics);
        assertEquals(metric.name() + " of " + path, expected, metrics.get(metric).orElseThrow(), DELTA);
    }
}
