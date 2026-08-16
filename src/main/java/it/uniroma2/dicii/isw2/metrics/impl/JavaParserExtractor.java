package it.uniroma2.dicii.isw2.metrics.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.SourceScanner;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The leaf of the Composite pattern measuring the cognitive complexity and the depth of the
 * inheritance tree of the classes of the dataset through the <a href="https://javaparser.org">JavaParser</a>
 * library, which builds the syntax tree of a source file without needing it to compile.
 * <p>
 * The measures themselves are left to {@link CognitiveComplexityCalculator} and to
 * {@link InheritanceDepthCalculator}: this extractor is only about finding the classes of a snapshot,
 * the methods each of them declares and the class each of them extends. As CK does, it reports one row
 * per source file, describing the top-level type the file is named after.
 * <p>
 * Unlike its siblings, it reads the snapshot twice over one parse of it: a hierarchy runs across
 * source files, so no class can be measured before every class of the release has been declared.
 */
@Slf4j
public class JavaParserExtractor implements MetricsExtractor {

    private static final Set<Metric> EXTRACTED_METRICS =
            Collections.unmodifiableSet(EnumSet.of(Metric.WCOC, Metric.MCOC, Metric.DIT));

    private final CognitiveComplexityCalculator calculator = new CognitiveComplexityCalculator();

    private final SourceFilter filter;

    /**
     * The sources being mined are those of a project whose releases span several years, so they are
     * parsed at the newest language level the library supports rather than at the one this project is
     * built with: an older snapshot is a subset of a newer grammar, while the opposite does not hold.
     * Comments are not attributed to the nodes they precede, since no metric measured here is about them.
     */
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
            .setAttributeComments(false));

    /**
     * Builds an extractor measuring every source it finds, which is what a directory holding nothing
     * but the classes to measure calls for.
     */
    public JavaParserExtractor() {
        this(SourceFilter.everything());
    }

    /**
     * Builds an extractor measuring the sources of a snapshot that are functional code.
     *
     * @param filter the rule telling which of them are, shared with the other extractors of the
     *               composite so that they all describe the same classes
     */
    public JavaParserExtractor(SourceFilter filter) {
        this.filter = filter;
    }

    @Override
    public MetricsReport extract(Snapshot snapshot) throws MetricsException {
        Path sourcePath = snapshot.sourcePath();
        if (sourcePath == null || !Files.isDirectory(sourcePath)) {
            throw new MetricsException("Cannot extract the cognitive complexity metrics of '" + sourcePath
                    + "': it is not an existing directory");
        }
        Path root = sourcePath.toAbsolutePath().normalize();
        log.info("Extracting the JavaParser metrics of the sources under {}...", root);

        // A syntax tree is built once and kept until the whole snapshot has been measured: parsing every
        // source twice would cost more than holding the trees of the one release being measured. The
        // calculator of the depths is a new one per snapshot, so that the hierarchies of a release are
        // resolved through its own classes alone, as CK resets the registry counting the children
        Map<Path, CompilationUnit> units = new LinkedHashMap<>();
        for (Path file : SourceScanner.scan(root, filter)) {
            parse(file).ifPresent(unit -> units.put(file, unit));
        }
        InheritanceDepthCalculator depths = new InheritanceDepthCalculator();
        units.values().forEach(depths::declare);

        MetricsReport report = new MetricsReport();
        units.forEach((file, unit) -> addClass(report, root, file, unit, depths));
        log.info("Extracted the JavaParser metrics of the {} classes found under {}", report.size(), root);
        return report;
    }

    @Override
    public Set<Metric> extractedMetrics() {
        return EXTRACTED_METRICS;
    }

    /**
     * Builds the syntax tree of a source file. A file that cannot be parsed is left out of the dataset,
     * rather than aborting the extraction of the whole snapshot.
     *
     * @param file the path of the source file
     * @return its syntax tree, or an empty optional if it cannot be parsed
     */
    private Optional<CompilationUnit> parse(Path file) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful()) {
                return result.getResult();
            }
            logFailure(file, result);
        } catch (IOException e) {
            log.warn("Unable to read {}: {}. Skipping it...", file, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Reports a source file no syntax tree could be built of. Only where the file stops being valid
     * Java is logged: the text of a {@link Problem} carries the whole stack trace of the parser, which
     * says nothing about the file and buries the rest of the run, so it is left to the debug level.
     *
     * @param file   the path of the source file
     * @param result the outcome of parsing it
     */
    private static void logFailure(Path file, ParseResult<CompilationUnit> result) {
        log.warn("JavaParser was unable to parse {}{}. Skipping it...", file, firstProblemPosition(result));
        log.debug("Problems found while parsing {}: {}", file, result.getProblems());
    }

    /**
     * Locates the first problem the parser ran into, so that a source file left out of the dataset can
     * be looked at without turning the debug level on.
     *
     * @param result the outcome of parsing a source file
     * @return where the first problem was found, or an empty string if the parser located none
     */
    private static String firstProblemPosition(ParseResult<CompilationUnit> result) {
        return result.getProblems().stream()
                .findFirst()
                .flatMap(Problem::getLocation)
                .flatMap(TokenRange::toRange)
                .map(range -> " at line " + range.begin.line + ", column " + range.begin.column)
                .orElse("");
    }

    /**
     * Records in the report the metrics measured on the class a source file declares. A file declaring
     * no type at all, as {@code package-info.java} does, is not a row of the dataset and is skipped.
     *
     * @param report the report to fill in
     * @param root   the root directory the sources are measured under
     * @param file   the path of the source file
     * @param unit   its syntax tree
     * @param depths the depths measured over the whole snapshot
     */
    private void addClass(MetricsReport report, Path root, Path file, CompilationUnit unit,
                          InheritanceDepthCalculator depths) {
        String path = ClassNameResolver.relativePath(root, file);
        Optional<TypeDeclaration<?>> declaration = topLevelType(path, unit);
        if (declaration.isEmpty()) {
            log.debug("{} declares no type: leaving it out of the dataset", path);
            return;
        }
        String packageName = unit.getPackageDeclaration()
                .map(PackageDeclaration::getNameAsString)
                .orElse("");
        ClassMetrics metrics = report.forClass(path, ClassNameResolver.qualifiedName(packageName, path));
        List<Integer> complexities = complexities(declaration.get());
        metrics.set(Metric.WCOC, weightedComplexity(complexities));
        metrics.set(Metric.MCOC, maximumComplexity(complexities));
        // The name the row carries is the one the file is named after, while the hierarchy was declared
        // under the name of the type actually found in it: the two differ on a file declaring a type
        // named otherwise, and the depth has to be asked for under the name it was declared with
        metrics.set(Metric.DIT, depths.depthOf(qualifiedName(packageName, declaration.get())));
        log.debug("Measured the JavaParser metrics of {}: {}", metrics.getClassName(), metrics.getValues());
    }

    /**
     * @param packageName the package the source file declares, empty if it declares none
     * @param declaration the type the row of the dataset is about
     * @return the fully qualified name of that type
     */
    private static String qualifiedName(String packageName, TypeDeclaration<?> declaration) {
        String simpleName = declaration.getNameAsString();
        return packageName.isEmpty() ? simpleName : packageName + '.' + simpleName;
    }

    /**
     * Chooses which of the types a source file declares the row of the dataset is about, i.e. the one
     * named after the file. A file declaring no such type — a package-private class named differently
     * from its file, for instance — falls back to the first type declared in it. It is the same choice
     * {@code CKExtractor} makes, so that the two extractors describe the same class.
     *
     * @param path the path of the source file, relative to the root of the repository
     * @param unit its syntax tree
     * @return the type the file is named after, the first one declared in it, or an empty optional if
     * it declares none
     */
    private static Optional<TypeDeclaration<?>> topLevelType(String path, CompilationUnit unit) {
        String expected = ClassNameResolver.fileName(path);
        return unit.getTypes().stream()
                .filter(type -> type.getNameAsString().equals(expected))
                .findFirst()
                .or(() -> unit.getTypes().stream().findFirst());
    }

    /**
     * Measures the cognitive complexity of every method a type declares. Only the methods declared
     * directly by the type are measured: those of the types nested in it belong to the nested types,
     * which CK reports on separately and the dataset does not hold a row for. Constructors count as
     * methods, so that these measures are taken over the same set the cyclomatic ones are.
     * <p>
     * The body of an <em>anonymous</em> class, on the other hand, is part of the method declaring it,
     * as the reference algorithm prescribes — the reader of that method has to read through it — so a
     * method hiding a long anonymous class can be worth far more here than the cyclomatic complexity
     * CK attributes to it, which counts that class as a type of its own.
     *
     * @param declaration the type the row of the dataset is about
     * @return the cognitive complexity of each of its methods, empty if it declares none
     */
    private List<Integer> complexities(TypeDeclaration<?> declaration) {
        List<CallableDeclaration<?>> callables = new ArrayList<>(declaration.getMethods());
        callables.addAll(declaration.getConstructors());
        return callables.stream().map(calculator::complexityOf).toList();
    }

    /**
     * Computes the weighted cognitive complexity of a class as the mean cognitive complexity of its
     * methods. A method with no body of its own, as an abstract or an interface one, is worth nothing
     * and still counts as a method, since the metric is defined over every method the class declares.
     *
     * @param complexities the cognitive complexity of each method of the class
     * @return their mean, or 0 if the class declares no method
     */
    private static double weightedComplexity(List<Integer> complexities) {
        if (complexities.isEmpty()) {
            return 0;
        }
        return complexities.stream().mapToInt(Integer::intValue).sum() / (double) complexities.size();
    }

    /**
     * Computes the maximum cognitive complexity of a class as the highest cognitive complexity among
     * its methods.
     *
     * @param complexities the cognitive complexity of each method of the class
     * @return the complexity of its most complex method, or 0 if it declares none
     */
    private static double maximumComplexity(List<Integer> complexities) {
        return complexities.stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
