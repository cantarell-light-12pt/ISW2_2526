package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetWriter;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A writer made of other writers, which hands every released version it is given to each of them in
 * turn. It is what lets one run produce several datasets out of the same measures — the whole release
 * history and the trimmed prefix of it — without measuring anything twice, since measuring a version
 * is what the hours of a run are spent on.
 * <p>
 * The composite <b>owns</b> the writers added to it: closing it closes all of them. That is what makes
 * it safe to build inside the try-with-resources that holds it, adding one writer at a time, since a
 * file that cannot be opened then finds the ones already opened being held by a resource.
 */
@Slf4j
public class CompositeDatasetWriter implements DatasetWriter {

    private final List<DatasetWriter> writers = new ArrayList<>();

    /**
     * Adds a writer to this composite. The writers are handed each version in the order they are
     * added.
     *
     * @param writer the writer to add, ignored if null
     * @return this composite, so that several writers can be chained in a single expression
     */
    public CompositeDatasetWriter add(DatasetWriter writer) {
        if (writer == null) {
            log.warn("Ignoring an attempt to add a null writer to the composite");
            return this;
        }
        writers.add(writer);
        return this;
    }

    /**
     * @return the writers this composite is made of, in the order they are written to
     */
    public List<DatasetWriter> getWriters() {
        return Collections.unmodifiableList(writers);
    }

    @Override
    public void write(Version version, MetricsReport report) throws DatasetException {
        for (DatasetWriter writer : writers) {
            writer.write(version, report);
        }
    }

    /**
     * Closes every writer, whether the ones before it could be closed or not: a dataset left open
     * because another one failed to close would be the second file lost to the first one's failure.
     * The failures are reported together, the first of them carrying the others.
     *
     * @throws DatasetException if any of the writers cannot be closed
     */
    @Override
    public void close() throws DatasetException {
        DatasetException failure = null;
        for (DatasetWriter writer : writers) {
            try {
                writer.close();
            } catch (DatasetException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
