package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that a released version measured once reaches every dataset it is meant to be written to, and
 * that closing the composite closes all of them: two files come out of the one set of measures, and
 * measuring a version is what the hours of a run are spent on.
 */
public class CompositeDatasetWriterTest {

    private static final String FIRST_PATH = "src/main/java/sample/First.java";

    @Test
    public void testAVersionMeasuredOnceIsWrittenToEveryDataset() throws DatasetException {
        RecordingDatasetWriter first = new RecordingDatasetWriter();
        RecordingDatasetWriter second = new RecordingDatasetWriter();
        Version version = version("3.5.0", 1);

        try (CompositeDatasetWriter dataset = new CompositeDatasetWriter().add(first).add(second)) {
            dataset.write(version, report());
        }

        assertEquals(List.of(version), first.getWritten());
        assertEquals(List.of(version), second.getWritten());
    }

    @Test
    public void testTheDatasetsAreWrittenToInTheOrderTheyWereAdded() {
        RecordingDatasetWriter first = new RecordingDatasetWriter();
        RecordingDatasetWriter second = new RecordingDatasetWriter();

        CompositeDatasetWriter dataset = new CompositeDatasetWriter().add(first).add(second);

        assertEquals(List.of(first, second), dataset.getWriters());
    }

    @Test
    public void testANullWriterIsIgnoredRatherThanAdded() {
        CompositeDatasetWriter dataset = new CompositeDatasetWriter().add(null);

        assertTrue(dataset.getWriters().isEmpty());
    }

    @Test
    public void testClosingTheCompositeClosesEveryDataset() throws DatasetException {
        RecordingDatasetWriter first = new RecordingDatasetWriter();
        RecordingDatasetWriter second = new RecordingDatasetWriter();

        new CompositeDatasetWriter().add(first).add(second).close();

        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
    }

    /**
     * A dataset left open because another one failed to close would be the second file lost to the
     * first one's failure. The failures are reported together, the first of them carrying the others.
     */
    @Test
    public void testADatasetThatCannotBeClosedDoesNotLeaveTheOthersOpen() {
        RecordingDatasetWriter failing = new RecordingDatasetWriter();
        failing.setFailingOnClose(true);
        RecordingDatasetWriter alsoFailing = new RecordingDatasetWriter();
        alsoFailing.setFailingOnClose(true);
        RecordingDatasetWriter last = new RecordingDatasetWriter();
        CompositeDatasetWriter dataset = new CompositeDatasetWriter()
                .add(failing).add(alsoFailing).add(last);

        DatasetException failure = assertThrows(DatasetException.class, dataset::close);

        assertTrue(last.isClosed());
        assertEquals(1, failure.getSuppressed().length);
    }

    /**
     * A composite of nothing writes nowhere rather than failing, which is what it is before the first
     * dataset has been added to it.
     */
    @Test
    public void testACompositeOfNoDatasetWritesNowhere() throws DatasetException {
        CompositeDatasetWriter dataset = new CompositeDatasetWriter();

        dataset.write(version("3.5.0", 1), report());
        dataset.close();

        assertTrue(dataset.getWriters().isEmpty());
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
