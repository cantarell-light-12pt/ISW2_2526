package it.uniroma2.dicii.isw2.metrics.model;

import it.uniroma2.dicii.isw2.metrics.Metric;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The metrics measured on a single class of a single snapshot of the project, i.e. one row of the
 * dataset before it gets labelled.
 * <p>
 * The measures are held in a map rather than in one field per metric because each extractor of the
 * composite fills in only the metrics it is able to compute: a class is measured by several extractors
 * in turn, and every one of them adds its own contribution to the same object.
 */
@RequiredArgsConstructor
@ToString
public class ClassMetrics {

    /**
     * The path of the source file declaring the class, relative to the root of the repository and
     * always separated by {@code /}. It is what ties these measures to the Git history of the class.
     */
    @Getter
    private final String path;

    /**
     * The fully qualified name of the class.
     */
    @Getter
    private final String className;

    private final Map<Metric, Double> values = new EnumMap<>(Metric.class);

    /**
     * Records the value of a metric, replacing the one previously recorded, if any.
     *
     * @param metric the measured metric
     * @param value  its value on this class
     */
    public void set(Metric metric, double value) {
        values.put(metric, value);
    }

    /**
     * Returns the value of a metric on this class.
     *
     * @param metric the wanted metric
     * @return its value, or an empty optional if no extractor measured it on this class
     */
    public OptionalDouble get(Metric metric) {
        Double value = values.get(metric);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /**
     * @return every metric measured on this class so far, ordered as declared in {@link Metric}
     */
    public Map<Metric, Double> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * Records all the given values at once, replacing the ones previously recorded for the same
     * metrics. Used to merge into this class the measures another extractor took on it.
     *
     * @param measures the values to record
     */
    public void setAll(Map<Metric, Double> measures) {
        values.putAll(measures);
    }
}
