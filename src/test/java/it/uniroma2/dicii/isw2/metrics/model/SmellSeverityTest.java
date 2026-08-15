package it.uniroma2.dicii.isw2.metrics.model;

import it.uniroma2.dicii.isw2.metrics.Metric;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the priorities PMD reports its violations with are read as the severities the catalogue
 * of the dataset counts them under.
 */
public class SmellSeverityTest {

    @Test
    public void testEveryPriorityIsCountedUnderItsMetric() {
        assertEquals(Optional.of(Metric.BS), SmellSeverity.ofPriority(1).map(SmellSeverity::getMetric));
        assertEquals(Optional.of(Metric.HS), SmellSeverity.ofPriority(2).map(SmellSeverity::getMetric));
        assertEquals(Optional.of(Metric.MS), SmellSeverity.ofPriority(3).map(SmellSeverity::getMetric));
        assertEquals(Optional.of(Metric.LS), SmellSeverity.ofPriority(4).map(SmellSeverity::getMetric));
        assertEquals(Optional.of(Metric.IS), SmellSeverity.ofPriority(5).map(SmellSeverity::getMetric));
    }

    @Test
    public void testAPriorityPmdDoesNotDefineIsNoSeverity() {
        assertTrue(SmellSeverity.ofPriority(0).isEmpty());
        assertTrue(SmellSeverity.ofPriority(6).isEmpty());
        assertTrue(SmellSeverity.ofPriority(-1).isEmpty());
    }

    @Test
    public void testTheMetricsAreTheSmellMetricsOfTheCatalogue() {
        assertEquals(EnumSet.of(Metric.BS, Metric.HS, Metric.MS, Metric.LS, Metric.IS),
                SmellSeverity.metrics());
    }

    /**
     * Every severity has to be counted somewhere of its own, or two of them would be told apart by the
     * enumeration and not by the dataset.
     */
    @Test
    public void testNoTwoSeveritiesShareAMetric() {
        assertEquals(SmellSeverity.values().length, SmellSeverity.metrics().size());
    }
}
