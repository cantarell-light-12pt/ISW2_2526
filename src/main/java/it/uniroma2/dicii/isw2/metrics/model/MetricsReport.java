package it.uniroma2.dicii.isw2.metrics.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The metrics measured on every class of a single snapshot of the project, i.e. the slice of the
 * dataset describing one released version.
 * <p>
 * Classes are keyed by the path of the source file declaring them, since that is the identity the Git
 * history speaks in, and the one the evolution metrics and the buggy/not-buggy label will be joined
 * on. The paths are kept sorted, so that two runs over the same snapshot produce the same ordering.
 */
public class MetricsReport {

    private final Map<String, ClassMetrics> classes = new TreeMap<>();

    /**
     * Returns the measures of the class declared by the given source file, creating an empty set of
     * measures if this is the first extractor to report on it. Extractors call this method to obtain
     * the object they have to fill in.
     *
     * @param path      the path of the source file declaring the class, relative to the root of the
     *                  repository
     * @param className the fully qualified name of the class
     * @return the measures of that class, possibly already holding the contributions of other extractors
     */
    public ClassMetrics forClass(String path, String className) {
        return classes.computeIfAbsent(path, key -> new ClassMetrics(key, className));
    }

    /**
     * @param path the path of the source file declaring the wanted class
     * @return the measures of that class, or {@code null} if no extractor reported on it
     */
    public ClassMetrics forPath(String path) {
        return classes.get(path);
    }

    /**
     * @return the measures of every class of the snapshot, ordered by path
     */
    public Collection<ClassMetrics> getClasses() {
        return Collections.unmodifiableCollection(classes.values());
    }

    /**
     * @return the number of classes this report covers
     */
    public int size() {
        return classes.size();
    }

    /**
     * Merges into this report the measures of another one taken on the same snapshot, as the composite
     * extractor does with the reports of its children. Classes only present in the other report are
     * added, while the ones present in both have their measures combined; whenever both reports carry
     * the same metric for the same class, the value of the other one wins.
     *
     * @param other the report to merge into this one, left untouched
     */
    public void merge(MetricsReport other) {
        for (ClassMetrics metrics : other.classes.values()) {
            forClass(metrics.getPath(), metrics.getClassName()).setAll(metrics.getValues());
        }
    }
}
