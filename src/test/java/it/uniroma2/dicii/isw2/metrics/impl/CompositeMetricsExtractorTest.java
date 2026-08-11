package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import lombok.RequiredArgsConstructor;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the composite behaves as a single extractor: it runs its children on the same snapshot
 * and merges what they measure, whether they report on the same classes or on different ones.
 */
public class CompositeMetricsExtractorTest {

    private static final double DELTA = 0.0001;

    private static final Path SOURCE_PATH = Path.of("/tmp/repos/PROJECT");

    private static final String FIRST_CLASS = "sample/First.java";
    private static final String SECOND_CLASS = "sample/Second.java";

    private CompositeMetricsExtractor composite;

    @Before
    public void setUp() {
        composite = new CompositeMetricsExtractor();
    }

    @Test
    public void testEmptyCompositeMeasuresNothing() throws MetricsException {
        assertEquals(0, composite.extract(SOURCE_PATH).size());
        assertTrue(composite.extractedMetrics().isEmpty());
        assertTrue(composite.getExtractors().isEmpty());
    }

    @Test
    public void testMetricsOfTheSameClassAreMerged() throws MetricsException {
        composite.add(new StubExtractor(FIRST_CLASS, Metric.LOC, 42))
                .add(new StubExtractor(FIRST_CLASS, Metric.CBO, 7));

        MetricsReport report = composite.extract(SOURCE_PATH);

        assertEquals(1, report.size());
        assertEquals(42, report.forPath(FIRST_CLASS).get(Metric.LOC).orElseThrow(), DELTA);
        assertEquals(7, report.forPath(FIRST_CLASS).get(Metric.CBO).orElseThrow(), DELTA);
    }

    @Test
    public void testClassesOfDifferentChildrenAreGathered() throws MetricsException {
        composite.add(new StubExtractor(FIRST_CLASS, Metric.LOC, 42))
                .add(new StubExtractor(SECOND_CLASS, Metric.LOC, 13));

        MetricsReport report = composite.extract(SOURCE_PATH);

        assertEquals(2, report.size());
        assertEquals(42, report.forPath(FIRST_CLASS).get(Metric.LOC).orElseThrow(), DELTA);
        assertEquals(13, report.forPath(SECOND_CLASS).get(Metric.LOC).orElseThrow(), DELTA);
    }

    @Test
    public void testTheLastChildWinsOnTheSameMetric() throws MetricsException {
        composite.add(new StubExtractor(FIRST_CLASS, Metric.LOC, 42))
                .add(new StubExtractor(FIRST_CLASS, Metric.LOC, 13));

        assertEquals(13, composite.extract(SOURCE_PATH).forPath(FIRST_CLASS).get(Metric.LOC).orElseThrow(), DELTA);
    }

    @Test
    public void testExtractedMetricsAreTheUnionOfTheChildrenOnes() {
        composite.add(new StubExtractor(FIRST_CLASS, Metric.LOC, 42))
                .add(new StubExtractor(SECOND_CLASS, Metric.CBO, 7));

        assertEquals(EnumSet.of(Metric.LOC, Metric.CBO), composite.extractedMetrics());
    }

    @Test
    public void testFailingChildAbortsTheWholeExtraction() {
        composite.add(new StubExtractor(FIRST_CLASS, Metric.LOC, 42)).add(new FailingExtractor());

        assertThrows(MetricsException.class, () -> composite.extract(SOURCE_PATH));
    }

    @Test
    public void testChildrenCanBeAddedAndRemoved() throws MetricsException {
        MetricsExtractor child = new StubExtractor(FIRST_CLASS, Metric.LOC, 42);
        composite.add(child).add(null);

        assertEquals(1, composite.getExtractors().size());
        assertTrue(composite.remove(child));
        assertFalse(composite.remove(child));
        assertNull(composite.extract(SOURCE_PATH).forPath(FIRST_CLASS));
    }

    /**
     * Reports a single, fixed measure on a single class, so that the merging performed by the composite
     * can be observed without running any real analysis.
     */
    @RequiredArgsConstructor
    private static class StubExtractor implements MetricsExtractor {

        private final String path;
        private final Metric metric;
        private final double value;

        @Override
        public MetricsReport extract(Path sourcePath) {
            MetricsReport report = new MetricsReport();
            report.forClass(path, "sample." + path).set(metric, value);
            return report;
        }

        @Override
        public Set<Metric> extractedMetrics() {
            return EnumSet.of(metric);
        }
    }

    /**
     * Stands for an extractor whose tool is unable to measure the snapshot.
     */
    private static class FailingExtractor implements MetricsExtractor {

        @Override
        public MetricsReport extract(Path sourcePath) throws MetricsException {
            throw new MetricsException("Unable to measure " + sourcePath);
        }

        @Override
        public Set<Metric> extractedMetrics() {
            return EnumSet.noneOf(Metric.class);
        }
    }
}
