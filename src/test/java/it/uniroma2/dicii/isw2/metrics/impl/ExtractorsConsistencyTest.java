package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
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
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Runs the two extractors of the composite over the same snapshot, to check that they describe its
 * classes the same way: were they to key or name a class differently, the dataset would hold two
 * half-filled rows for it instead of a single complete one.
 */
public class ExtractorsConsistencyTest {

    private static final String BASE_SOURCE = """
            package sample;

            public class Base {

                public void doIt() {
                    // nothing to do
                }
            }
            """;

    /**
     * A file declaring no type named after it, which is the case the two extractors are most likely to
     * disagree on: CK reports on the type it parsed, {@code sample.Different}, while JavaParser starts
     * from the file. Both must name the class after the file, since that is the identity the rest of
     * the dataset knows it by.
     */
    private static final String HELPER_SOURCE = """
            package sample;

            class Different {

                int compute(int value) {
                    return value > 0 ? value : -value;
                }
            }
            """;

    @Rule
    public TemporaryFolder sources = new TemporaryFolder();

    private Path root;

    @Before
    public void setUp() throws IOException {
        Path packageDirectory = sources.newFolder("sample").toPath();
        Files.writeString(packageDirectory.resolve("Base.java"), BASE_SOURCE);
        Files.writeString(packageDirectory.resolve("Helper.java"), HELPER_SOURCE);
        root = sources.getRoot().toPath();
    }

    @Test
    public void testBothExtractorsKeyAndNameTheClassesAlike() throws MetricsException {
        MetricsReport ck = new CKExtractor().extract(root);
        MetricsReport javaParser = new JavaParserExtractor().extract(root);

        assertEquals(ck.getClasses().size(), javaParser.getClasses().size());
        for (ClassMetrics measured : ck.getClasses()) {
            ClassMetrics counterpart = javaParser.forPath(measured.getPath());
            assertNotNull("JavaParser reported on no class at " + measured.getPath(), counterpart);
            assertEquals("The two extractors disagree on the name of " + measured.getPath(),
                    measured.getClassName(), counterpart.getClassName());
        }
    }

    @Test
    public void testClassIsNamedAfterItsFileRatherThanAfterTheTypeItDeclares() throws MetricsException {
        MetricsReport report = new CKExtractor().extract(root);

        assertEquals("sample.Helper", report.forPath("sample/Helper.java").getClassName());
    }

    @Test
    public void testCompositeFillsEveryMetricOfEveryClass() throws MetricsException {
        MetricsExtractor composite = new CompositeMetricsExtractor()
                .add(new CKExtractor())
                .add(new JavaParserExtractor());

        MetricsReport report = composite.extract(root);

        Set<Metric> expected = EnumSet.copyOf(composite.extractedMetrics());
        assertEquals(2, report.size());
        for (ClassMetrics metrics : report.getClasses()) {
            assertEquals("Incomplete row for " + metrics.getPath(), expected, metrics.getValues().keySet());
        }
    }
}
