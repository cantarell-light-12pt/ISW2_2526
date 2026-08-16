package it.uniroma2.dicii.isw2.dataset;

import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.versions.model.Version;

/**
 * Where the dataset the workflow builds is written to.
 * <p>
 * A writer is handed one released version at a time, rather than the whole set of measures at the
 * end, because measuring a version is expensive: a full run checks out some fifty releases and lets
 * four tools loose on each of them, so the rows of the releases already analysed have to reach their
 * destination before the run has a chance of failing on a later one. Implementations are therefore
 * expected to make the rows of a version durable by the time {@link #write(Version, MetricsReport)}
 * returns, and not merely by the time the writer is closed.
 * <p>
 * A writer holds a resource for as long as the extraction lasts, and is meant to be used as the
 * resource of a try-with-resources wrapping the loop over the versions.
 */
public interface DatasetWriter extends AutoCloseable {

    /**
     * Appends to the dataset one row per class of a released version.
     *
     * @param version the released version the measures were taken on
     * @param report  the measures taken on its classes
     * @throws DatasetException if the rows cannot be written
     */
    void write(Version version, MetricsReport report) throws DatasetException;

    /**
     * Releases the resources held by this writer. The dataset is complete only once this method has
     * returned, although the rows written so far are readable before it.
     *
     * @throws DatasetException if the dataset cannot be closed
     */
    @Override
    void close() throws DatasetException;

}
