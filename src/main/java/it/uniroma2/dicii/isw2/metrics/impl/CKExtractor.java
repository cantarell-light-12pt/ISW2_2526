package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import com.github.mauricioaniche.ck.metric.NOCExtras;
import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.SourceScanner;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The leaf of the Composite pattern measuring the complexity and object-oriented metrics of the
 * dataset through the <a href="https://github.com/mauricioaniche/ck">CK</a> library, which computes
 * them by parsing the sources without needing them to compile.
 * <p>
 * CK reports one result per declared type, including inner and anonymous classes; since the dataset is
 * one row per source file, only the top-level type of each file is kept.
 */
@Slf4j
public class CKExtractor implements MetricsExtractor {

    private static final Set<Metric> EXTRACTED_METRICS = Collections.unmodifiableSet(EnumSet.of(
            Metric.CBO, Metric.RFC, Metric.DIT, Metric.LCOM, Metric.NOC,
            Metric.LOC, Metric.NM, Metric.NA, Metric.WCYC, Metric.MCYC));

    /**
     * The kinds of type CK can declare a result to be about, minus the ones nested in another type
     * ("innerclass" and "anonymous"). Nested enumerations are reported as plain enumerations, so this
     * filter alone does not guarantee a single result per file: see {@link #topLevelType(String, List)}.
     */
    private static final Set<String> TOP_LEVEL_TYPES = Set.of("class", "interface", "enum");

    /**
     * Do not resolve the dependencies of the sources against the jars found in the repository, and do
     * not collect the usage of variables and fields: neither is needed by the metrics of the dataset,
     * and both make the analysis considerably slower on a project the size of the one being mined.
     */
    private static final boolean USE_JARS = false;
    private static final boolean COLLECT_VARIABLES_AND_FIELDS = false;

    /**
     * Let CK size its batches of files on the memory available to the JVM.
     */
    private static final int AUTOMATIC_BATCH_SIZE = 0;

    private final SourceFilter filter;

    /**
     * Builds an extractor measuring every source it finds, which is what a directory holding nothing
     * but the classes to measure calls for.
     */
    public CKExtractor() {
        this(SourceFilter.everything());
    }

    /**
     * Builds an extractor measuring the sources of a snapshot that are functional code.
     *
     * @param filter the rule telling which of them are, shared with the other extractors of the
     *               composite so that they all describe the same classes
     */
    public CKExtractor(SourceFilter filter) {
        this.filter = filter;
    }

    @Override
    public MetricsReport extract(Path sourcePath) throws MetricsException {
        if (sourcePath == null || !Files.isDirectory(sourcePath)) {
            throw new MetricsException("Cannot extract the CK metrics of '" + sourcePath
                    + "': it is not an existing directory");
        }
        Path root = sourcePath.toAbsolutePath().normalize();
        log.info("Extracting the CK metrics of the sources under {}...", root);

        // The children of a class are counted in a registry shared by every run of the library: clearing
        // it keeps the subclasses seen on a snapshot from being counted again on the following ones
        NOCExtras.resetInstance();

        // The sources are enumerated here rather than left to the library to walk on its own, so that
        // the excluded ones are not parsed at all: were they merely dropped from the report, the classes
        // of the dataset would still be credited with the children and the couplings they bring
        List<Path> sources = SourceScanner.scan(root, filter);
        CollectingNotifier notifier = new CollectingNotifier();
        new CK(USE_JARS, AUTOMATIC_BATCH_SIZE, COLLECT_VARIABLES_AND_FIELDS)
                .calculate(root, notifier, sources.toArray(Path[]::new));

        MetricsReport report = buildReport(root, notifier.getResults());
        log.info("Extracted the CK metrics of the {} classes found under {}", report.size(), root);
        return report;
    }

    @Override
    public Set<Metric> extractedMetrics() {
        return EXTRACTED_METRICS;
    }

    /**
     * Turns the results CK produced into a report holding one entry per source file, keeping the
     * top-level type of each of them.
     *
     * @param root    the root directory the sources were measured under
     * @param results every type CK reported on
     * @return the metrics of the classes of the snapshot
     */
    private static MetricsReport buildReport(Path root, List<CKClassResult> results) {
        MetricsReport report = new MetricsReport();
        // Grouping into a sorted map keeps the files processed in the same order on every run
        Map<String, List<CKClassResult>> byFile = results.stream()
                .filter(result -> TOP_LEVEL_TYPES.contains(result.getType()))
                .collect(Collectors.groupingBy(CKClassResult::getFile, TreeMap::new, Collectors.toList()));
        byFile.forEach((file, declared) -> addClass(report, root, file, topLevelType(file, declared)));
        return report;
    }

    /**
     * Chooses which of the types CK reported on a source file describes the class the row of the
     * dataset is about, i.e. the one named after the file. A file declaring no such type — a
     * package-private class named differently from its file, for instance — falls back to the first
     * type declared in it.
     *
     * @param file     the path of the source file
     * @param declared the top-level types CK reported on it, never empty
     * @return the type the file is named after, or the first one declared in it
     */
    private static CKClassResult topLevelType(String file, List<CKClassResult> declared) {
        String expected = ClassNameResolver.fileName(file);
        return declared.stream()
                .filter(result -> ClassNameResolver.simpleName(result.getClassName()).equals(expected))
                .findFirst()
                .orElseGet(declared::getFirst);
    }

    /**
     * Records in the report the metrics CK measured on a class.
     *
     * @param report the report to fill in
     * @param root   the root directory the sources were measured under
     * @param file   the path of the source file declaring the class
     * @param result the metrics CK measured on it
     */
    private static void addClass(MetricsReport report, Path root, String file, CKClassResult result) {
        // Only the package of the type CK reported on is kept: the name of the class is the one the file
        // is named after, so that it agrees with the one every other extractor measuring it derives
        String path = ClassNameResolver.relativePath(root, Path.of(file));
        String className = ClassNameResolver.qualifiedName(
                ClassNameResolver.packageOf(result.getClassName()), path);
        ClassMetrics metrics = report.forClass(path, className);
        metrics.set(Metric.CBO, result.getCbo());
        metrics.set(Metric.RFC, result.getRfc());
        metrics.set(Metric.DIT, result.getDit());
        metrics.set(Metric.LCOM, result.getLcom());
        // NOC is read only now that the whole snapshot has been parsed, since a class can be extended by
        // any other one, including those CK happened to parse after it
        metrics.set(Metric.NOC, result.getNoc());
        metrics.set(Metric.LOC, result.getLoc());
        metrics.set(Metric.NM, result.getNumberOfMethods());
        metrics.set(Metric.NA, result.getNumberOfFields());
        metrics.set(Metric.WCYC, weightedComplexity(result));
        metrics.set(Metric.MCYC, maximumComplexity(result));
        log.debug("Measured the CK metrics of {}: {}", className, metrics.getValues());
    }

    /**
     * Computes the weighted cyclomatic complexity of a class as the mean cyclomatic complexity of its
     * methods. It is derived from the same method-level measures the maximum is taken over, rather than
     * from the complexity CK attributes to the class as a whole, which also counts the branches of the
     * initialiser blocks.
     *
     * @param result the metrics CK measured on the class
     * @return the mean cyclomatic complexity of its methods, or 0 if it declares none
     */
    private static double weightedComplexity(CKClassResult result) {
        Set<CKMethodResult> methods = result.getMethods();
        if (methods.isEmpty()) {
            return 0;
        }
        return methods.stream().mapToInt(CKMethodResult::getWmc).sum() / (double) methods.size();
    }

    /**
     * Computes the maximum cyclomatic complexity of a class as the highest cyclomatic complexity among
     * its methods.
     *
     * @param result the metrics CK measured on the class
     * @return the cyclomatic complexity of its most complex method, or 0 if it declares none
     */
    private static double maximumComplexity(CKClassResult result) {
        return result.getMethods().stream().mapToInt(CKMethodResult::getWmc).max().orElse(0);
    }

    /**
     * Gathers the results CK hands over while it parses the sources. Files that cannot be parsed are
     * reported to this same notifier and are left out of the dataset, rather than aborting the
     * extraction of the whole snapshot.
     */
    private static class CollectingNotifier implements CKNotifier {

        private final List<CKClassResult> results = new ArrayList<>();

        @Override
        public void notify(CKClassResult result) {
            results.add(result);
        }

        @Override
        public void notifyError(String sourceFilePath, Exception e) {
            log.warn("CK was unable to parse {}: {}. Skipping it...", sourceFilePath, e.getMessage());
        }

        private List<CKClassResult> getResults() {
            return results;
        }
    }
}
