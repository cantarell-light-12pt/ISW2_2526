package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.SourceScanner;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassHistory;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The leaf of the Composite pattern measuring the evolution metrics of the dataset through
 * <a href="https://www.eclipse.org/jgit/">JGit</a>, which reads the release history of the project
 * out of the repository it was cloned from.
 * <p>
 * These metrics are the only ones of the catalogue a snapshot of the sources cannot answer: how much
 * a class changed, how many people wrote it and how long it has been around are questions about the
 * releases that came before the one being measured. The reading itself is left to
 * {@link ReleaseHistoryAnalyzer}, which walks the whole history once, the first time this extractor
 * is asked for a version; this extractor is only about turning what it found into the metrics of a
 * row.
 * <p>
 * Unlike its siblings, this extractor never parses a source file, so it cannot tell which package a
 * class belongs to and names it after its file alone. That name is a fallback rather than the one the
 * dataset carries: {@link MetricsReport#forClass(String, String)} keeps the first name it is given,
 * and this extractor joins the composite last, so the fully qualified name CK or JavaParser derived
 * always wins. It only surfaces on a file neither of them could parse, where a row measured on its
 * history alone is still worth more than no row at all.
 */
@Slf4j
public class JGitHistoryExtractor implements MetricsExtractor {

    private static final Set<Metric> EXTRACTED_METRICS = Collections.unmodifiableSet(EnumSet.of(
            Metric.CH, Metric.MCH, Metric.CIS, Metric.NR, Metric.NDA, Metric.AGE, Metric.NLBF, Metric.WNBF));

    private final Path repoPath;

    private final List<Version> versions;

    private final SourceFilter filter;

    private final ReleaseHistoryAnalyzer analyzer;

    /**
     * The history of every class at every release, read on the first measurement and kept for the
     * ones that follow: walking the repository once per version would read the same commits over and
     * over, and the metrics of a release are about the releases before it anyway.
     */
    private Map<Integer, Map<String, ClassHistory>> history;

    /**
     * Builds an extractor measuring the evolution of the classes of a project over its releases.
     *
     * @param repoPath      the root directory of the repository holding the history to read
     * @param versions      the released versions of the project, already associated with their Git
     *                      tags and numbered
     * @param bugFixCommits the identifiers of the commits that fixed a bug, as the association between
     *                      the Jira issues and the Git commits reports them
     * @param filter        the rule telling which sources are rows of the dataset, shared with the
     *                      other extractors of the composite so that they all describe the same classes
     */
    public JGitHistoryExtractor(Path repoPath, List<Version> versions, Set<String> bugFixCommits,
                                SourceFilter filter) {
        this.repoPath = repoPath;
        this.versions = List.copyOf(versions);
        this.filter = filter;
        this.analyzer = new ReleaseHistoryAnalyzer(filter, Set.copyOf(bugFixCommits));
    }

    @Override
    public MetricsReport extract(Snapshot snapshot) throws MetricsException {
        Path sourcePath = snapshot.sourcePath();
        if (sourcePath == null || !Files.isDirectory(sourcePath)) {
            throw new MetricsException("Cannot extract the evolution metrics of '" + sourcePath
                    + "': it is not an existing directory");
        }
        if (snapshot.version() == null) {
            throw new MetricsException("Cannot extract the evolution metrics of '" + sourcePath
                    + "': it is not the snapshot of a released version");
        }
        Path root = sourcePath.toAbsolutePath().normalize();
        Version version = snapshot.version();
        log.info("Extracting the evolution metrics of version {} out of the sources under {}...",
                version.getName(), root);

        Map<String, ClassHistory> released = releaseHistory(version);
        MetricsReport report = new MetricsReport();
        for (Path file : SourceScanner.scan(root, filter)) {
            addClass(report, root, file, released);
        }
        log.info("Extracted the evolution metrics of the {} classes found under {}", report.size(), root);
        return report;
    }

    @Override
    public Set<Metric> extractedMetrics() {
        return EXTRACTED_METRICS;
    }

    /**
     * Returns what the history says about the classes of a release, reading the whole repository the
     * first time it is asked.
     *
     * @param version the release being measured
     * @return the history of each of its classes, empty if the release was never walked
     * @throws MetricsException if the repository cannot be read
     */
    private Map<String, ClassHistory> releaseHistory(Version version) throws MetricsException {
        if (history == null) {
            history = analyzer.analyse(repoPath, versions);
        }
        Map<String, ClassHistory> released = history.get(version.getIndex());
        if (released == null) {
            log.warn("The history holds no release numbered {}, which version {} is: " +
                            "its classes will be measured as if they had just been written",
                    version.getIndex(), version.getName());
            return Map.of();
        }
        return released;
    }

    /**
     * Records in the report the evolution metrics of the class a source file declares.
     * <p>
     * A file the snapshot holds and the history says nothing about — one only ever brought in by a
     * merge, which the walk leaves out — is measured as a class written for this very release rather
     * than dropped: the row is evidence of what the snapshot held, and leaving it out here would hide
     * from the composite that the extractors disagreed on it.
     *
     * @param report   the report to fill in
     * @param root     the root directory the sources are measured under
     * @param file     the path of the source file
     * @param released the history of the classes of the release being measured
     */
    private static void addClass(MetricsReport report, Path root, Path file, Map<String, ClassHistory> released) {
        String path = ClassNameResolver.relativePath(root, file);
        ClassHistory classHistory = released.get(path);
        if (classHistory == null) {
            log.debug("The history says nothing about {}: measuring it as a class of this release alone", path);
            classHistory = ClassHistory.UNTOUCHED;
        }
        ClassMetrics metrics = report.forClass(path, ClassNameResolver.qualifiedName("", path));
        metrics.set(Metric.CH, classHistory.churn());
        metrics.set(Metric.MCH, classHistory.maxChurn());
        metrics.set(Metric.CIS, classHistory.sizeChange());
        metrics.set(Metric.NR, classHistory.revisions());
        metrics.set(Metric.NDA, classHistory.authors());
        metrics.set(Metric.AGE, classHistory.age());
        metrics.set(Metric.NLBF, classHistory.latestBugFixes());
        metrics.set(Metric.WNBF, classHistory.weightedBugFixes());
        log.debug("Measured the evolution metrics of {}: {}", path, metrics.getValues());
    }
}
