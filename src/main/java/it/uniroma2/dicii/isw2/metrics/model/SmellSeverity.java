package it.uniroma2.dicii.isw2.metrics.model;

import it.uniroma2.dicii.isw2.metrics.Metric;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * How severe a code smell is, and the metric counting the smells of that severity.
 * <p>
 * This is the one place where the severities the catalogue in the README is written in meet the
 * priorities PMD writes its reports in: a rule carries a priority from 1 to 5, and the dataset counts
 * the violations of each of them under a metric of its own. Naming the severities rather than working
 * on the bare numbers is what keeps the rest of the extraction from having to know that a blocker is
 * a 1.
 */
@Getter
@RequiredArgsConstructor
public enum SmellSeverity {

    /**
     * The priority PMD gives the rules whose violation has to be changed absolutely.
     */
    BLOCKER(1, Metric.BS),

    /**
     * The priority PMD gives the rules whose violation has to be changed.
     */
    HIGH(2, Metric.HS),

    /**
     * The priority PMD gives the rules whose violation is worth changing.
     */
    MEDIUM(3, Metric.MS),

    /**
     * The priority PMD gives the rules whose violation could be changed.
     */
    MINOR(4, Metric.LS),

    /**
     * The priority PMD gives the rules whose violation is only worth reporting.
     */
    INFO(5, Metric.IS);

    private static final Set<Metric> SMELL_METRICS = Collections.unmodifiableSet(
            EnumSet.of(Metric.BS, Metric.HS, Metric.MS, Metric.LS, Metric.IS));

    /**
     * The priority PMD reports a violation of this severity with.
     */
    private final int priority;

    /**
     * The metric counting the smells of this severity found in a class.
     */
    private final Metric metric;

    /**
     * Reads the severity of a violation out of the priority PMD reported it with.
     *
     * @param priority the priority of the rule that was violated
     * @return the severity it stands for, empty if it is none of the five PMD defines
     */
    public static Optional<SmellSeverity> ofPriority(int priority) {
        for (SmellSeverity severity : values()) {
            if (severity.priority == priority) {
                return Optional.of(severity);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the metrics the severities are counted under, i.e. the smell metrics of the dataset
     */
    public static Set<Metric> metrics() {
        return SMELL_METRICS;
    }
}
