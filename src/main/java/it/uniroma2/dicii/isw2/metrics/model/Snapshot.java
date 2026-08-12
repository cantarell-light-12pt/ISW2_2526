package it.uniroma2.dicii.isw2.metrics.model;

import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.nio.file.Path;

/**
 * What a {@link MetricsExtractor} is asked to measure: the sources of the project as they were at one
 * point of its history, and which released version that point is.
 * <p>
 * The directory alone was enough as long as every metric of the dataset could be read off the sources
 * themselves. The evolution metrics cannot: how much a class changed, and how many releases it has
 * been part of, are questions about the release the snapshot belongs to rather than about the files
 * it holds. Carrying both in a single object keeps the signature of the interface stable as further
 * leaves join the composite asking for further context.
 *
 * @param sourcePath the root directory of the sources to measure
 * @param version    the released version those sources were the ones of, {@code null} when the
 *                   directory is measured outside the release history
 */
public record Snapshot(Path sourcePath, Version version) {

    /**
     * Builds the snapshot of a directory that is not tied to a released version, which is what the
     * extractors reading their metrics off the sources alone are happy with.
     *
     * @param sourcePath the root directory of the sources to measure
     */
    public Snapshot(Path sourcePath) {
        this(sourcePath, null);
    }
}
