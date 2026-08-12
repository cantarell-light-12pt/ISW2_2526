package it.uniroma2.dicii.isw2.metrics;

import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;

import java.util.Set;

/**
 * The component of the Composite pattern used to measure the class-level metrics of a snapshot of the
 * project.
 * <p>
 * The dataset draws its metrics from several unrelated tools — {@code CKExtractor} wraps the CK
 * library, {@code JGitHistoryExtractor} reads the release history through JGit, and the code-smell
 * metrics will come from a further leaf — but the workflow has no reason to know how many of them
 * there are: {@code CompositeMetricsExtractor} implements this same interface by delegating to the
 * extractors it holds and merging their reports, so a single extractor and a whole set of them are
 * used interchangeably.
 */
public interface MetricsExtractor {

    /**
     * Measures the metrics this extractor is responsible for on every class of the given snapshot.
     * The directory the snapshot points at is expected to hold the sources of the project as they
     * were at the point to measure, so extracting the metrics of a released version means checking
     * the repository out at the corresponding commit first.
     *
     * @param snapshot the sources to measure, and the released version they are the ones of
     * @return the metrics measured on the classes of that snapshot
     * @throws MetricsException if the snapshot cannot be measured
     */
    MetricsReport extract(Snapshot snapshot) throws MetricsException;

    /**
     * Declares which metrics this extractor contributes to the dataset, so that the metrics missing
     * from a report can be told apart from the ones no extractor is able to measure.
     *
     * @return the metrics filled in by {@link #extract(Snapshot)}
     */
    Set<Metric> extractedMetrics();

}
