package it.uniroma2.dicii.isw2.metrics;

import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The single enumeration of the sources of a snapshot every {@link MetricsExtractor} measures.
 * <p>
 * Each extractor could list the sources itself — the tools they wrap are happy to be pointed at a
 * directory and walk it on their own — but then nothing would tie the two walks together: a file one
 * tool considers a source and the other does not becomes a row of the dataset holding only half of
 * its metrics. Enumerating the snapshot once, here, and handing the resulting list to every extractor
 * is what makes the two of them describe the same classes, and what lets the {@link SourceFilter} be
 * applied in a single place.
 */
public final class SourceScanner {

    private static final String JAVA_EXTENSION = ".java";

    private SourceScanner() {
        // Utility class
    }

    /**
     * Lists the sources of a snapshot that are rows of the dataset, sorted so that two runs over the
     * same snapshot process them in the same order.
     *
     * @param root   the root directory of the sources to measure, absolute and normalised
     * @param filter the rule telling which of those sources are functional code
     * @return the paths of the source files to measure
     * @throws MetricsException if the directory cannot be walked
     */
    public static List<Path> scan(Path root, SourceFilter filter) throws MetricsException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(JAVA_EXTENSION))
                    .filter(file -> filter.accepts(ClassNameResolver.relativePath(root, file)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new MetricsException("Unable to list the sources under '" + root + "'", e);
        }
    }
}
