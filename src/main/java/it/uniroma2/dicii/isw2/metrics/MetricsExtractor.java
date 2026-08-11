package it.uniroma2.dicii.isw2.metrics;

import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;

import java.nio.file.Path;
import java.util.Set;

/**
 * The component of the Composite pattern used to measure the class-level metrics of a snapshot of the
 * project.
 * <p>
 * The dataset draws its metrics from several unrelated tools — {@code CKExtractor} wraps the CK
 * library, and the evolution and code-smell metrics will come from further leaves — but the workflow
 * has no reason to know how many of them there are: {@code CompositeMetricsExtractor} implements this
 * same interface by delegating to the extractors it holds and merging their reports, so a single
 * extractor and a whole set of them are used interchangeably.
 */
public interface MetricsExtractor {

    /**
     * Measures the metrics this extractor is responsible for on every class found under the given
     * directory. The directory is expected to hold the sources of the project as they were at the
     * snapshot to measure, so extracting the metrics of a released version means checking the
     * repository out at the corresponding commit first.
     *
     * @param sourcePath the root directory of the sources to measure
     * @return the metrics measured on the classes found under that directory
     * @throws MetricsException if the sources cannot be measured
     */
    MetricsReport extract(Path sourcePath) throws MetricsException;

    /**
     * Declares which metrics this extractor contributes to the dataset, so that the metrics missing
     * from a report can be told apart from the ones no extractor is able to measure.
     *
     * @return the metrics filled in by {@link #extract(Path)}
     */
    Set<Metric> extractedMetrics();

}
