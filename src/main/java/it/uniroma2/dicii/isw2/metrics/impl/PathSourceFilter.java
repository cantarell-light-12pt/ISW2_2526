package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link SourceFilter} leaving out the sources whose path says they are not functional code,
 * configured through the {@code project.metrics.excluded*} properties so that mining a project laid
 * out differently is a matter of configuration rather than of code.
 * <p>
 * Two rules are applied, and a source file is a row of the dataset only if it survives both:
 * <ul>
 * <li>the <em>directories</em> rule drops a file having any of the named directories among the
 * segments of its path. Comparing whole segments, rather than matching a pattern against the path as
 * a whole, is what lets a single {@code test} rule cover the {@code src/test/java} of a project built
 * with Maven, the {@code src/java/test} of the same project back when it was built with Ant, and the
 * test tree of every one of its modules;</li>
 * <li>the <em>files</em> rule drops a file whose name matches one of the given glob patterns. The
 * patterns are matched against the name alone and not against the whole path, so that a
 * {@code *Test.java} rule catches the test classes that live outside a test directory without also
 * catching the production classes a test-related name starts with, such as
 * {@code TestableZooKeeper.java}.</li>
 * </ul>
 */
@Slf4j
public class PathSourceFilter implements SourceFilter {

    private static final String SEPARATOR = ",";

    private static final String PATH_SEPARATOR = "/";

    private static final String GLOB_SYNTAX = "glob:";

    private final Set<String> excludedDirectories;

    private final List<PathMatcher> excludedFiles;

    /**
     * Builds a filter out of the two rules as they are configured, each of them a comma-separated
     * list whose entries are trimmed and whose blanks are ignored. A rule left empty, or not
     * configured at all, excludes nothing.
     *
     * @param excludedDirectories the names of the directories whose sources are not functional code
     * @param excludedFiles       the glob patterns the name of a source that is not functional code
     *                            matches
     * @throws java.util.regex.PatternSyntaxException if one of the file patterns is not a valid glob,
     *                                                since a misspelt rule would silently let into the
     *                                                dataset the very classes it was meant to keep out
     */
    public PathSourceFilter(String excludedDirectories, String excludedFiles) {
        this.excludedDirectories = entriesOf(excludedDirectories).stream()
                .collect(Collectors.toUnmodifiableSet());
        this.excludedFiles = entriesOf(excludedFiles).stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher(GLOB_SYNTAX + pattern))
                .toList();
        log.debug("Leaving out of the dataset the sources under {} and the ones named as {}",
                this.excludedDirectories, entriesOf(excludedFiles));
    }

    @Override
    public boolean accepts(String relativePath) {
        return !isUnderAnExcludedDirectory(relativePath) && !hasAnExcludedName(relativePath);
    }

    /**
     * Splits a configured rule into its entries, dropping the blank ones so that a trailing separator
     * or a rule left empty does not turn into a pattern matching everything.
     *
     * @param property the value of the property carrying the rule, possibly null
     * @return its entries, trimmed, empty if the property is not configured
     */
    private static List<String> entriesOf(String property) {
        if (property == null || property.isBlank()) {
            return List.of();
        }
        return Arrays.stream(property.split(SEPARATOR))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    /**
     * @param relativePath the path of a source file, relative to the root of the repository
     * @return whether any of the directories it is nested in is an excluded one
     */
    private boolean isUnderAnExcludedDirectory(String relativePath) {
        String[] segments = relativePath.split(PATH_SEPARATOR);
        // The last segment is the file itself, which the other rule is about
        for (int i = 0; i < segments.length - 1; i++) {
            if (excludedDirectories.contains(segments[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param relativePath the path of a source file, relative to the root of the repository
     * @return whether its name matches one of the excluded patterns
     */
    private boolean hasAnExcludedName(String relativePath) {
        Path fileName = Path.of(relativePath).getFileName();
        return excludedFiles.stream().anyMatch(pattern -> pattern.matches(fileName));
    }
}
