package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The composite of the Composite pattern: an extractor made of other extractors, which measures a
 * snapshot by running each of its children on it in turn and merging what they report into a single
 * view of the classes.
 * <p>
 * Children are added to and removed from the composite rather than from the {@link MetricsExtractor}
 * interface, so that the leaves are not given operations they have no way of honouring.
 */
@Slf4j
public class CompositeMetricsExtractor implements MetricsExtractor {

    private final List<MetricsExtractor> extractors = new ArrayList<>();

    /**
     * Adds an extractor to this composite. The extractors run in the order they are added, and the
     * ones added later overwrite the metrics the earlier ones measured on the same class.
     *
     * @param extractor the extractor to add, ignored if null
     * @return this composite, so that several extractors can be chained in a single expression
     */
    public CompositeMetricsExtractor add(MetricsExtractor extractor) {
        if (extractor == null) {
            log.warn("Ignoring an attempt to add a null extractor to the composite");
            return this;
        }
        extractors.add(extractor);
        return this;
    }

    /**
     * Removes an extractor from this composite.
     *
     * @param extractor the extractor to remove
     * @return whether the extractor was part of this composite
     */
    public boolean remove(MetricsExtractor extractor) {
        return extractors.remove(extractor);
    }

    /**
     * @return the extractors this composite is made of, in the order they run
     */
    public List<MetricsExtractor> getExtractors() {
        return Collections.unmodifiableList(extractors);
    }

    @Override
    public MetricsReport extract(Path sourcePath) throws MetricsException {
        MetricsReport report = new MetricsReport();
        for (MetricsExtractor extractor : extractors) {
            log.info("Running {} on the sources under {}...", extractor.getClass().getSimpleName(), sourcePath);
            report.merge(extractor.extract(sourcePath));
        }
        checkpoint(report, sourcePath);
        log.info("Measured {} metrics on the {} classes found under {}",
                extractedMetrics().size(), report.size(), sourcePath);
        return report;
    }

    @Override
    public Set<Metric> extractedMetrics() {
        Set<Metric> metrics = EnumSet.noneOf(Metric.class);
        extractors.forEach(extractor -> metrics.addAll(extractor.extractedMetrics()));
        return metrics;
    }

    /**
     * Closes the measurement of a snapshot by checking that every child reported on the same classes,
     * which the composite has no other way of finding out: a class one of them left out is merged into
     * the report all the same, and shows up as a row missing the metrics that child was responsible
     * for rather than as a failure.
     * <p>
     * The incomplete rows are reported and kept: a partially measured class is still evidence of what
     * was found in the snapshot, and dropping it here would hide the disagreement instead of exposing
     * it.
     *
     * @param report     the merged measures of the snapshot
     * @param sourcePath the root directory the snapshot was measured under
     */
    private void checkpoint(MetricsReport report, Path sourcePath) {
        Set<Metric> expected = extractedMetrics();
        Collection<ClassMetrics> incomplete = report.incompleteClasses(expected);
        if (incomplete.isEmpty()) {
            log.info("Checkpoint on {}: the {} extractors agree on the {} classes measured",
                    sourcePath, extractors.size(), report.size());
            return;
        }
        log.warn("Checkpoint on {}: {} of the {} classes measured were not reported on by every extractor",
                sourcePath, incomplete.size(), report.size());
        incomplete.forEach(metrics -> log.warn("No extractor measured {} on {}",
                missingMetrics(expected, metrics), metrics.getPath()));
    }

    /**
     * @param expected the metrics every class of the snapshot should carry
     * @param metrics  the measures taken on one of its classes
     * @return the expected metrics that class carries none of
     */
    private static Set<Metric> missingMetrics(Set<Metric> expected, ClassMetrics metrics) {
        Set<Metric> missing = EnumSet.copyOf(expected);
        missing.removeAll(metrics.getValues().keySet());
        return missing;
    }
}
