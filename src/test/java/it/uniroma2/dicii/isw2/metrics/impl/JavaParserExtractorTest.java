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

/**
 * Measures a snapshot whose cognitive complexity can be counted by hand, to check that the values end
 * up under the expected metric and against the expected class.
 */
public class JavaParserExtractorTest {

    private static final double DELTA = 0.0001;

    /**
     * {@code classify} is worth 3: one for the {@code if}, one for the {@code &&} of its condition, and
     * one for the {@code for}. {@code getLabel} is worth nothing, so the class has a maximum cognitive
     * complexity of 3 and a weighted one of 1.5.
     */
    private static final String CHILD_SOURCE = """
            package sample;

            public class Child {

                private int counter;

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
                    return "child";
                }
            }
            """;

    /**
     * The methods declared by the class nested in this one belong to the nested class, which the dataset
     * holds no row for: only {@code run}, worth 1 for its {@code while}, is measured here.
     */
    private static final String OUTER_SOURCE = """
            package sample;

            public class Outer {

                public void run(int times) {
                    while (times > 0) {
                        times--;
                    }
                }

                static class Nested {

                    void loop(int[] values) {
                        for (int value : values) {
                            if (value == 0) {
                                return;
                            }
                        }
                    }
                }
            }
            """;

    /**
     * An abstract method is worth nothing and still counts as a method: the class is worth 2 over the
     * two methods it declares, i.e. a weighted complexity of 1.
     */
    private static final String ABSTRACT_SOURCE = """
            package sample;

            public abstract class Shape {

                public abstract double area();

                public boolean isEmpty() {
                    if (area() == 0) {
                        return true;
                    }
                    return area() < 0 || Double.isNaN(area());
                }
            }
            """;

    @Rule
    public TemporaryFolder sources = new TemporaryFolder();

    private JavaParserExtractor extractor;
    private MetricsReport report;

    @Before
    public void setUp() throws IOException, MetricsException {
        extractor = new JavaParserExtractor();
        Path packageDirectory = sources.newFolder("sample").toPath();
        Files.writeString(packageDirectory.resolve("Child.java"), CHILD_SOURCE);
        Files.writeString(packageDirectory.resolve("Outer.java"), OUTER_SOURCE);
        Files.writeString(packageDirectory.resolve("Shape.java"), ABSTRACT_SOURCE);
        report = extractor.extract(sources.getRoot().toPath());
    }

    @Test
    public void testExtractedMetricsAreTheDeclaredOnes() {
        assertEquals(3, report.size());
        for (ClassMetrics metrics : report.getClasses()) {
            assertEquals(extractor.extractedMetrics(), metrics.getValues().keySet());
        }
    }

    @Test
    public void testClassesAreKeyedByPathRelativeToTheRootAndNamedAfterTheirFile() {
        assertNotNull(report.forPath("sample/Child.java"));
        assertEquals("sample.Child", report.forPath("sample/Child.java").getClassName());
        assertEquals("sample.Outer", report.forPath("sample/Outer.java").getClassName());
    }

    @Test
    public void testCognitiveComplexityMetrics() {
        assertMetric(3, "sample/Child.java", Metric.MCOC);
        assertMetric(1.5, "sample/Child.java", Metric.WCOC);
    }

    @Test
    public void testMethodsOfNestedTypesAreNotAttributedToTheOuterOne() {
        assertMetric(1, "sample/Outer.java", Metric.MCOC);
        assertMetric(1, "sample/Outer.java", Metric.WCOC);
    }

    @Test
    public void testMethodsWithoutABodyStillCountAsMethods() {
        assertMetric(2, "sample/Shape.java", Metric.MCOC);
        assertMetric(1, "sample/Shape.java", Metric.WCOC);
    }

    @Test
    public void testFileDeclaringNoTypeIsNotARowOfTheDataset() throws IOException, MetricsException {
        Path packageDirectory = sources.getRoot().toPath().resolve("sample");
        Files.writeString(packageDirectory.resolve("package-info.java"), "package sample;\n");

        report = extractor.extract(sources.getRoot().toPath());

        assertEquals(3, report.size());
        assertNull(report.forPath("sample/package-info.java"));
    }

    /**
     * A source the filter leaves out is no row of the dataset, whether it is left out for the directory
     * it sits in or for the name it carries.
     */
    @Test
    public void testExcludedSourcesAreNotRowsOfTheDataset() throws IOException, MetricsException {
        Path testDirectory = sources.newFolder("test", "sample").toPath();
        Files.writeString(testDirectory.resolve("ChildTest.java"), CHILD_SOURCE.replace("Child", "ChildTest"));
        Files.writeString(sources.getRoot().toPath().resolve("sample").resolve("OuterTest.java"),
                CHILD_SOURCE.replace("Child", "OuterTest"));

        MetricsReport filtered = new JavaParserExtractor(new PathSourceFilter("test", "*Test.java"))
                .extract(sources.getRoot().toPath());

        assertEquals(3, filtered.size());
        assertNull(filtered.forPath("test/sample/ChildTest.java"));
        assertNull(filtered.forPath("sample/OuterTest.java"));
        assertNotNull(filtered.forPath("sample/Child.java"));
    }

    @Test
    public void testUnparsableFileIsSkippedRatherThanFatal() throws IOException, MetricsException {
        Path packageDirectory = sources.getRoot().toPath().resolve("sample");
        Files.writeString(packageDirectory.resolve("Broken.java"), "package sample; class Broken {");

        report = extractor.extract(sources.getRoot().toPath());

        assertEquals(3, report.size());
        assertNull(report.forPath("sample/Broken.java"));
    }

    @Test
    public void testClassWithoutMethodsIsWorthNothing() throws IOException, MetricsException {
        Path packageDirectory = sources.getRoot().toPath().resolve("sample");
        Files.writeString(packageDirectory.resolve("Empty.java"), "package sample;\n\npublic class Empty {\n}\n");

        report = extractor.extract(sources.getRoot().toPath());

        assertMetric(0, "sample/Empty.java", Metric.WCOC);
        assertMetric(0, "sample/Empty.java", Metric.MCOC);
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

    private void assertMetric(double expected, String path, Metric metric) {
        ClassMetrics metrics = report.forPath(path);
        assertNotNull("No metrics were measured on " + path, metrics);
        assertEquals(metric.name() + " of " + path, expected, metrics.get(metric).orElseThrow(), DELTA);
    }
}
