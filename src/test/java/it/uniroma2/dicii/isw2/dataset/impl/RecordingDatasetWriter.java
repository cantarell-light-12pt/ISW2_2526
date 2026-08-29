package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetWriter;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A dataset that writes nowhere and remembers what it was handed, which is what the writers built out
 * of other writers are told apart by: what reaches a delegate, and whether it was closed.
 * <p>
 * It can be told to fail on close, so that a composite holding several of them can be checked to close
 * the ones after the failure all the same.
 */
class RecordingDatasetWriter implements DatasetWriter {

    @Getter
    private final List<Version> written = new ArrayList<>();

    @Getter
    private boolean closed;

    /**
     * Whether closing this writer fails, as closing a dataset whose file cannot be flushed does.
     */
    @Setter
    private boolean failingOnClose;

    @Override
    public void write(Version version, MetricsReport report) {
        written.add(version);
    }

    @Override
    public void close() throws DatasetException {
        closed = true;
        if (failingOnClose) {
            throw new DatasetException("Unable to close the dataset");
        }
    }
}
