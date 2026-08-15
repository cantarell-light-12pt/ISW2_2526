package it.uniroma2.dicii.isw2.metrics.impl;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads out of a snapshot which Java version the release it holds was compiled against.
 * <p>
 * PMD parses a source against a language version, and a project long-lived enough to be worth mining
 * does not keep the same one: the releases of the project this dataset is built on span the Ant era,
 * compiled against Java 5 and 6, and the Maven one, compiled against 8 and later. Parsing an old
 * release as a new one would mistake for syntax errors the identifiers whose names later became
 * keywords, and parsing a new release as an old one would fail on the syntax it was written in;
 * either way the classes lost are classes the dataset would count as smelling of nothing.
 * <p>
 * The version is therefore read from the release itself, out of the build files it ships, rather than
 * configured once for the whole run or tabulated per release: nothing here is specific to the project
 * being mined. The reading is deliberately a tolerant one — these are hand-written build files, read
 * for a single number, and a snapshot that does not say has a configured version to fall back on.
 */
@Slf4j
public class JavaVersionDetector {

    /**
     * How PMD names the versions of the language the sources are written in.
     */
    private static final String LANGUAGE = "java-";

    private static final String MAVEN_BUILD_FILE = "pom.xml";
    private static final List<String> ANT_BUILD_FILES = List.of("build.xml", "build.properties");

    /**
     * Where a Maven build says which version it compiles against, in the order the compiler plugin
     * honours them: the release it targets first, then the source level, then the bytecode level, and
     * last the source level configured on the plugin itself rather than through a property.
     */
    private static final List<Pattern> MAVEN_PATTERNS = List.of(
            mavenPropertyPattern("maven.compiler.release"),
            mavenPropertyPattern("maven.compiler.source"),
            mavenPropertyPattern("maven.compiler.target"),
            Pattern.compile("<source>\\s*([\\d.]+?)\\s*</source>"),
            Pattern.compile("<target>\\s*([\\d.]+?)\\s*</target>"));

    /**
     * Where an Ant build says the same thing: a property the javac tasks read, whether declared as one
     * or assigned in a properties file, or the attribute of a javac task written out in full.
     * <p>
     * The bytecode level is looked at only once the source level has been looked for everywhere, and
     * for the same reason the Maven reading falls back on it: the oldest builds of a project are apt to
     * pin the one and leave the other to whichever compiler happened to run.
     */
    private static final List<Pattern> ANT_PATTERNS = List.of(
            antPropertyPattern("javac.source"),
            antAssignmentPattern("javac.source"),
            antAttributePattern("source"),
            antPropertyPattern("javac.target"),
            antAssignmentPattern("javac.target"),
            antAttributePattern("target"));

    /**
     * The {@code 1.x} spelling of a version, which PMD only knows the oldest two releases by.
     */
    private static final Pattern LEGACY_VERSION = Pattern.compile("1\\.(\\d+)");

    /**
     * The releases PMD still names {@code 1.x}: from Java 5 on, the leading {@code 1.} is dropped.
     */
    private static final int OLDEST_MODERN_RELEASE = 5;

    private final String defaultVersion;

    /**
     * Builds a detector reading the version of each snapshot it is given.
     *
     * @param defaultVersion the Java version to assume for a snapshot whose build files do not say
     *                       which one it was compiled against, as a bare number such as {@code 8}
     */
    public JavaVersionDetector(String defaultVersion) {
        this.defaultVersion = normalise(defaultVersion).orElse("8");
    }

    /**
     * Detects the Java version the sources of a snapshot were compiled against.
     *
     * @param root the root directory of the snapshot
     * @return the version, named the way PMD names its language versions, e.g. {@code java-8}
     */
    public String detect(Path root) {
        Optional<String> detected = read(root.resolve(MAVEN_BUILD_FILE), MAVEN_PATTERNS);
        for (String antBuildFile : ANT_BUILD_FILES) {
            if (detected.isPresent()) {
                break;
            }
            detected = read(root.resolve(antBuildFile), ANT_PATTERNS);
        }
        if (detected.isEmpty()) {
            log.warn("The build files under {} do not say which Java version the release was compiled "
                    + "against: parsing its sources as Java {}", root, defaultVersion);
            return LANGUAGE + defaultVersion;
        }
        log.info("The release under {} was compiled against Java {}", root, detected.get());
        return LANGUAGE + detected.get();
    }

    /**
     * Looks a build file up for the first of the given patterns that matches it.
     *
     * @param buildFile the file to read, which need not exist
     * @param patterns  the ways that kind of build file states its Java version, most telling first
     * @return the version it states, normalised, empty if it states none
     */
    private static Optional<String> read(Path buildFile, List<Pattern> patterns) {
        if (!Files.isRegularFile(buildFile)) {
            return Optional.empty();
        }
        String contents;
        try {
            contents = Files.readString(buildFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Unable to read the build file {}: {}", buildFile, e.getMessage());
            return Optional.empty();
        }
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(contents);
            if (matcher.find()) {
                Optional<String> version = normalise(matcher.group(1));
                if (version.isPresent()) {
                    return version;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Spells a Java version the way PMD names it: {@code 1.8} and {@code 8} are the same version, but
     * only one of the two is a name PMD answers to for every release.
     *
     * @param version the version as a build file states it
     * @return {@code 1.3} and {@code 1.4} unchanged, every later release without its leading
     * {@code 1.}, empty if it is no version at all
     */
    private static Optional<String> normalise(String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        String trimmed = version.strip();
        Matcher legacy = LEGACY_VERSION.matcher(trimmed);
        if (legacy.matches()) {
            int release = Integer.parseInt(legacy.group(1));
            return Optional.of(release < OLDEST_MODERN_RELEASE ? trimmed : String.valueOf(release));
        }
        return trimmed.chars().allMatch(Character::isDigit) ? Optional.of(trimmed) : Optional.empty();
    }

    /**
     * @param name the name of a build property
     * @return the pattern matching the value a Maven build file gives it
     */
    private static Pattern mavenPropertyPattern(String name) {
        return Pattern.compile("<" + Pattern.quote(name) + ">\\s*([\\d.]+?)\\s*</" + Pattern.quote(name) + ">");
    }

    /**
     * @param name the name of a build property, which a module is free to prefix with its own
     * @return the pattern matching the value an Ant build file declares it with
     */
    private static Pattern antPropertyPattern(String name) {
        return Pattern.compile("<property\\s+name\\s*=\\s*\"[\\w.]*" + Pattern.quote(name)
                + "\"\\s+value\\s*=\\s*\"([\\d.]+?)\"");
    }

    /**
     * @param name the name of a build property, which a module is free to prefix with its own
     * @return the pattern matching the value a properties file assigns it
     */
    private static Pattern antAssignmentPattern(String name) {
        return Pattern.compile("^\\s*[\\w.]*" + Pattern.quote(name) + "\\s*=\\s*([\\d.]+?)\\s*$",
                Pattern.MULTILINE);
    }

    /**
     * @param name the name of an attribute of the javac task
     * @return the pattern matching the value a javac task gives it, when it is written out rather than
     * read from a property
     */
    private static Pattern antAttributePattern(String name) {
        return Pattern.compile("<javac\\b[^>]*?\\b" + Pattern.quote(name) + "\\s*=\\s*\"([\\d.]+?)\"");
    }
}
