package it.uniroma2.dicii.isw2.metrics;

import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs PMD over a set of sources and hands back the report it produced.
 * <p>
 * Unlike the other tools the metrics are read through, PMD is not a library this project depends on:
 * it is a program, run outside the JVM measuring the dataset. This interface is where that boundary
 * is drawn, so that whoever turns a report into metrics does not have to know how the report was
 * obtained — and can be tested without the program being installed at all.
 *
 * @see it.uniroma2.dicii.isw2.metrics.impl.DockerPmdRunner
 */
public interface PmdRunner {

    /**
     * Tells whether PMD can actually be run on this machine, so that the caller can leave the smell
     * metrics out of the dataset instead of failing every version over a tool that is not there.
     *
     * @return true if a run would have something to run on
     */
    boolean isAvailable();

    /**
     * Analyses the given sources and returns what PMD found.
     *
     * @param root            the root directory the sources are measured under, absolute and normalised
     * @param sources         the source files to analyse, as the scanner enumerated them
     * @param languageVersion the Java version the sources are to be parsed as, in the {@code java-8}
     *                        form PMD names its language versions with
     * @return the report PMD produced, in its JSON format, holding paths relative to the root
     * @throws MetricsException if PMD could not be run or did not run to completion
     */
    String run(Path root, List<Path> sources, String languageVersion) throws MetricsException;
}
