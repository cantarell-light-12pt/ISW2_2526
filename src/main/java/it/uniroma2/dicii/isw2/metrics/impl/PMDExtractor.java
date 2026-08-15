package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.PmdRunner;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.SourceScanner;
import it.uniroma2.dicii.isw2.metrics.dto.PmdFileDTO;
import it.uniroma2.dicii.isw2.metrics.dto.PmdReportDTO;
import it.uniroma2.dicii.isw2.metrics.dto.PmdViolationDTO;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.SmellSeverity;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The leaf of the Composite pattern measuring the code smell metrics of the dataset through
 * <a href="https://pmd.github.io/">PMD</a>, which reports every rule the sources of a release break.
 * <p>
 * A smell is a violation, and how severe it is is the priority PMD gives the rule that was broken:
 * this extractor does nothing but count the violations of a class under the metric its severity is
 * catalogued as, which {@link SmellSeverity} maps. Running PMD is left to a {@link PmdRunner}, since
 * PMD is a program rather than a library, and parsing what it reports is all this extractor is about.
 * <p>
 * A report only names the sources PMD found something in, so the classes it says nothing about are the
 * clean ones. Every source the scanner enumerated is therefore recorded as smelling of nothing before
 * a single violation is counted: a class with no smell is a row of five zeros, not a row the composite
 * would have to report as missing its smell metrics.
 * <p>
 * Like {@link JGitHistoryExtractor}, and unlike CK and JavaParser, this extractor never parses a
 * source itself — PMD reports a file, not the package it declares — so it names a class after its file
 * alone. That name is a fallback: {@link MetricsReport#forClass(String, String)} keeps the first name
 * it is given and this extractor joins the composite after the two that can derive a package, so the
 * fully qualified name they built always wins.
 */
@Slf4j
public class PMDExtractor implements MetricsExtractor {

    private static final Set<Metric> EXTRACTED_METRICS = SmellSeverity.metrics();

    private final SourceFilter filter;

    private final PmdRunner runner;

    private final JavaVersionDetector versionDetector;

    private final ObjectMapper objectMapper;

    /**
     * Builds an extractor counting the code smells of the sources of a snapshot.
     *
     * @param filter          the rule telling which of them are rows of the dataset, shared with the
     *                        other extractors of the composite so that they all describe the same
     *                        classes
     * @param runner          the way PMD is run over them
     * @param versionDetector the way the Java version their release was compiled against is read
     */
    public PMDExtractor(SourceFilter filter, PmdRunner runner, JavaVersionDetector versionDetector) {
        this.filter = filter;
        this.runner = runner;
        this.versionDetector = versionDetector;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    public MetricsReport extract(Snapshot snapshot) throws MetricsException {
        Path sourcePath = snapshot.sourcePath();
        if (sourcePath == null || !Files.isDirectory(sourcePath)) {
            throw new MetricsException("Cannot extract the code smells of '" + sourcePath
                    + "': it is not an existing directory");
        }
        Path root = sourcePath.toAbsolutePath().normalize();
        log.info("Extracting the code smells of the sources under {}...", root);

        List<Path> sources = SourceScanner.scan(root, filter);
        MetricsReport report = new MetricsReport();
        // Recorded before the report is read, so that the classes PMD found nothing wrong with are rows
        // counting no smell rather than rows missing the smell metrics altogether
        sources.forEach(source -> addCleanClass(report, root, source));
        if (sources.isEmpty()) {
            log.warn("There is no source to look for code smells in under {}", root);
            return report;
        }

        String languageVersion = versionDetector.detect(root);
        PmdReportDTO analysis = parse(runner.run(root, sources, languageVersion), root);
        countSmells(report, analysis, root);

        log.info("Extracted the code smells of the {} classes found under {}", report.size(), root);
        return report;
    }

    @Override
    public Set<Metric> extractedMetrics() {
        return EXTRACTED_METRICS;
    }

    /**
     * Records a class as smelling of nothing, which is what every class is until the report says
     * otherwise.
     *
     * @param report the report to fill in
     * @param root   the root directory the sources are measured under
     * @param source the path of the source file
     */
    private static void addCleanClass(MetricsReport report, Path root, Path source) {
        String path = ClassNameResolver.relativePath(root, source);
        ClassMetrics metrics = report.forClass(path, ClassNameResolver.qualifiedName("", path));
        SmellSeverity.metrics().forEach(metric -> metrics.set(metric, 0));
    }

    /**
     * Reads the report PMD produced.
     *
     * @param report the report, in the JSON format PMD writes it in
     * @param root   the root directory the sources were measured under
     * @return the report, mapped
     * @throws MetricsException if it is not a report that can be read
     */
    private PmdReportDTO parse(String report, Path root) throws MetricsException {
        try {
            return objectMapper.readValue(report, PmdReportDTO.class);
        } catch (JacksonException e) {
            throw new MetricsException("Unable to read the code smells PMD reported on the sources under '"
                    + root + "'", e);
        }
    }

    /**
     * Counts the violations of the report under the metrics their severity is catalogued as.
     *
     * @param report   the report to fill in, already holding every class of the snapshot
     * @param analysis what PMD found
     * @param root     the root directory the sources were measured under
     */
    private static void countSmells(MetricsReport report, PmdReportDTO analysis, Path root) {
        logProcessingErrors(analysis, root);
        if (analysis.getFiles() == null) {
            log.warn("PMD reported on no source at all under {}: every class is counted as clean", root);
            return;
        }
        int unknownFiles = 0;
        for (PmdFileDTO file : analysis.getFiles()) {
            ClassMetrics metrics = report.forPath(reportPath(file.getFilename()));
            if (metrics == null) {
                // PMD is handed the very sources the scanner enumerated, so this cannot happen unless the
                // report names them some other way than relative to the root, which would silently leave
                // every class clean: it is worth saying out loud rather than passing over
                unknownFiles++;
                continue;
            }
            countViolations(metrics, file);
        }
        if (unknownFiles > 0) {
            log.warn("PMD reported smells on {} sources under {} that are no class of the dataset: their "
                    + "smells are lost. Check that the report names the sources relative to the root",
                    unknownFiles, root);
        }
    }

    /**
     * Counts the violations found in one source file under the metrics of the class it declares.
     *
     * @param metrics the metrics of that class
     * @param file    the violations PMD found in it
     */
    private static void countViolations(ClassMetrics metrics, PmdFileDTO file) {
        if (file.getViolations() == null) {
            return;
        }
        for (PmdViolationDTO violation : file.getViolations()) {
            Optional<SmellSeverity> severity = SmellSeverity.ofPriority(violation.getPriority());
            if (severity.isEmpty()) {
                log.warn("PMD reported the rule {} with the unknown priority {}: not counting it as a smell",
                        violation.getRule(), violation.getPriority());
                continue;
            }
            Metric metric = severity.get().getMetric();
            metrics.set(metric, metrics.get(metric).orElse(0) + 1);
        }
        log.debug("Counted the code smells of {}: {}", file.getFilename(), metrics.getValues());
    }

    /**
     * Reports the sources PMD could not analyse, which are counted as clean for want of anything better
     * to count them as.
     *
     * @param analysis what PMD found
     * @param root     the root directory the sources were measured under
     */
    private static void logProcessingErrors(PmdReportDTO analysis, Path root) {
        if (analysis.getProcessingErrors() == null || analysis.getProcessingErrors().isEmpty()) {
            return;
        }
        log.warn("PMD could not analyse {} of the sources under {}: they are counted as smelling of nothing",
                analysis.getProcessingErrors().size(), root);
        analysis.getProcessingErrors().forEach(error ->
                log.debug("PMD could not analyse {}: {}", error.getFilename(), error.getMessage()));
    }

    /**
     * Turns the name a report gives a source into the path the dataset knows it by.
     * <p>
     * The runner is told to name the sources relative to the root it measured them under, which is the
     * very path the report of the composite keys a class by; all that is left is to spell it the way
     * every other extractor does.
     *
     * @param filename the name the report gives the source
     * @return the path of the source, relative to the root of the repository
     */
    private static String reportPath(String filename) {
        String path = filename.replace(File.separatorChar, '/');
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
