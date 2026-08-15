package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.ClassNameResolver;
import it.uniroma2.dicii.isw2.metrics.PmdRunner;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs PMD inside a container, so that measuring the code smells of the dataset asks for nothing to be
 * installed beyond Docker: neither PMD itself nor a Java runtime able to host it.
 * <p>
 * The image is pinned to a version rather than followed at {@code latest}, for the same reason the
 * ruleset is carried by this project: the smell counts of two releases are only comparable if the
 * rules that produced them, and the engine that applied them, were the same. It has to be a PMD 7
 * image, the only line able to parse every Java version the releases of a long-lived project were
 * written against.
 * <p>
 * The sources are mounted read-only and PMD is told to analyse a list of files rather than a
 * directory, so that it parses exactly the sources the scanner enumerated — the excluded ones are
 * never read, let alone reported on. Its report comes back on the standard output instead of being
 * written to a mounted file, which spares the run an output directory and the file a container would
 * leave behind owning.
 */
@Slf4j
public class DockerPmdRunner implements PmdRunner {

    /**
     * Where a Docker client is looked for, in order. Naming the program to run by its path rather than
     * letting the operating system search for it is what keeps a directory prepended to the {@code PATH}
     * from deciding which program every release of the project being mined is measured by.
     */
    private static final List<Path> DOCKER_PATHS = List.of(
            Path.of("/usr/bin/docker"),
            Path.of("/usr/local/bin/docker"),
            Path.of("/bin/docker"),
            Path.of("/snap/bin/docker"),
            Path.of("/opt/homebrew/bin/docker"));

    /**
     * The directory the runs stage what the container has to read under. The temporary directory of the
     * system would be the obvious place for it, but every user of the machine may write there: a
     * directory of this user alone is what keeps another process from slipping its own ruleset in
     * between the moment a run writes one and the moment PMD reads it.
     */
    private static final Path STAGING_ROOT = Path.of(System.getProperty("user.home"), ".isw2", "pmd");

    /**
     * The permissions the staging directories are kept behind, i.e. those of their owner alone.
     */
    private static final Set<PosixFilePermission> OWNER_ONLY =
            PosixFilePermissions.fromString("rwx------");

    private static final String POSIX_VIEW = "posix";

    /**
     * Where the two directories a run needs are mounted inside the container. The sources are mounted
     * read-only, since PMD is told not to keep an analysis cache and has nothing else to write there.
     */
    private static final String SOURCES_MOUNT = "/project";
    private static final String STAGING_MOUNT = "/pmd";

    private static final String FILE_LIST_NAME = "files.txt";
    private static final String RULESET_NAME = "ruleset.xml";
    private static final String ERROR_LOG_NAME = "pmd-stderr.log";

    /**
     * The outcomes of a run that produced a report: none at all, at least one violation, and at least
     * one source that could not be parsed. The last one is a normal event on a project whose oldest
     * releases predate the syntax of its newest ones, and it costs the run nothing but the classes it
     * happened on, so it is warned about rather than raised.
     */
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_VIOLATIONS_FOUND = 4;
    private static final int EXIT_RECOVERABLE_ERRORS = 5;
    private static final Set<Integer> REPORTING_EXIT_CODES =
            Set.of(EXIT_SUCCESS, EXIT_VIOLATIONS_FOUND, EXIT_RECOVERABLE_ERRORS);

    /**
     * How long the probe deciding whether Docker is there may take. It only asks the daemon its
     * version, so a wait this short is already generous, and an unreachable daemon is exactly the case
     * this class must not hang on.
     */
    private static final long AVAILABILITY_TIMEOUT_SECONDS = 20;

    /**
     * How much of what PMD wrote to its standard error is worth quoting when a run fails.
     */
    private static final int ERROR_LOG_TAIL_LINES = 20;

    private final String image;

    private final String rulesetResource;

    private final long timeoutSeconds;

    /**
     * Whether Docker answered the probe, remembered so that every version of the project being mined
     * does not pay for asking again.
     */
    private Boolean available;

    /**
     * Builds a runner analysing the sources with a containerised PMD.
     *
     * @param image           the PMD image to run, pinned to a version
     * @param rulesetResource the name of the classpath resource holding the ruleset to apply
     * @param timeoutSeconds  how long a single run may take before it is abandoned
     */
    public DockerPmdRunner(String image, String rulesetResource, long timeoutSeconds) {
        this.image = image;
        this.rulesetResource = rulesetResource;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean isAvailable() {
        if (available == null) {
            available = probeDocker();
        }
        return available;
    }

    @Override
    public String run(Path root, List<Path> sources, String languageVersion) throws MetricsException {
        Path staging = createStagingDirectory();
        try {
            writeFileList(staging.resolve(FILE_LIST_NAME), root, sources);
            copyRuleset(staging.resolve(RULESET_NAME));
            return analyse(root, staging, languageVersion);
        } finally {
            deleteRecursively(staging);
        }
    }

    /**
     * Asks the Docker daemon its version, which is the cheapest question that only a reachable daemon
     * can answer: the client alone being installed is not enough to run anything.
     *
     * @return true if the daemon answered
     */
    private boolean probeDocker() {
        Optional<Path> client = findDockerClient();
        if (client.isEmpty()) {
            log.warn("No Docker client was found in any of {}", DOCKER_PATHS);
            return false;
        }
        try {
            Process process = new ProcessBuilder(client.get().toString(), "version",
                    "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true)
                    .start();
            String output = consume(process, AVAILABILITY_TIMEOUT_SECONDS);
            if (output == null) {
                log.warn("The Docker daemon did not answer within {} seconds", AVAILABILITY_TIMEOUT_SECONDS);
                return false;
            }
            if (process.exitValue() != EXIT_SUCCESS) {
                log.warn("The Docker daemon could not be reached: {}", output.strip());
                return false;
            }
            log.info("Found a Docker daemon running version {}", output.strip());
            return true;
        } catch (IOException e) {
            log.warn("No Docker client could be run: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while looking for a Docker daemon");
            return false;
        }
    }

    /**
     * Runs the container and collects the report it writes to its standard output.
     *
     * @param root            the root directory the sources are measured under
     * @param staging         the directory holding the list of files and the ruleset
     * @param languageVersion the Java version the sources are to be parsed as
     * @return the JSON report PMD produced
     * @throws MetricsException if the container could not be run or did not run to completion
     */
    private String analyse(Path root, Path staging, String languageVersion) throws MetricsException {
        Path errorLog = staging.resolve(ERROR_LOG_NAME);
        Path client = findDockerClient().orElseThrow(() -> new MetricsException(
                "No Docker client to run PMD with was found in any of " + DOCKER_PATHS));
        List<String> command = command(client, root, staging, languageVersion);
        if (log.isDebugEnabled()) {
            log.debug("Running PMD as: {}", String.join(" ", command));
        }
        try {
            Process process = new ProcessBuilder(command)
                    .redirectError(errorLog.toFile())
                    .start();
            String report = consume(process, timeoutSeconds);
            if (report == null) {
                throw new MetricsException("PMD did not analyse the sources under '" + root + "' within "
                        + timeoutSeconds + " seconds");
            }
            checkExitCode(process.exitValue(), root, errorLog);
            return report;
        } catch (IOException e) {
            throw new MetricsException("Unable to run PMD on the sources under '" + root + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetricsException("Interrupted while running PMD on the sources under '" + root + "'", e);
        }
    }

    /**
     * Builds the command running the analysis.
     *
     * @param client          the Docker client to run the container with
     * @param root            the directory to mount the sources from
     * @param staging         the directory to mount the list of files and the ruleset from
     * @param languageVersion the Java version the sources are to be parsed as
     * @return the command and its arguments
     */
    private List<String> command(Path client, Path root, Path staging, String languageVersion) {
        List<String> command = new ArrayList<>(List.of(
                client.toString(), "run", "--rm",
                "-v", root + ":" + SOURCES_MOUNT + ":ro",
                "-v", staging + ":" + STAGING_MOUNT + ":ro",
                image,
                "check",
                "--file-list", STAGING_MOUNT + "/" + FILE_LIST_NAME,
                "-R", STAGING_MOUNT + "/" + RULESET_NAME,
                "-f", "json",
                "--use-version", languageVersion));
        // Report the sources by the path they are known by in the dataset rather than by the one they
        // happen to be mounted at, so that the report can be joined with what the other extractors
        // measured without any path being rewritten afterwards
        command.addAll(List.of("-z", SOURCES_MOUNT));
        // The report is the whole point of the run, so finding violations is not a failure; and an
        // incremental cache would be both useless, every release being a different set of sources, and
        // unwritable, the sources being mounted read-only
        command.addAll(List.of("--no-fail-on-violation", "--no-cache", "--no-progress"));
        return command;
    }

    /**
     * Decides whether a finished run produced a report worth reading.
     *
     * @param exitCode the code PMD exited with
     * @param root     the root directory the sources were measured under
     * @param errorLog the file holding what PMD wrote to its standard error
     * @throws MetricsException if PMD did not run to completion
     */
    private static void checkExitCode(int exitCode, Path root, Path errorLog) throws MetricsException {
        if (!REPORTING_EXIT_CODES.contains(exitCode)) {
            throw new MetricsException("PMD failed on the sources under '" + root + "' with exit code "
                    + exitCode + ": " + errorTail(errorLog));
        }
        if (exitCode == EXIT_RECOVERABLE_ERRORS) {
            log.warn("PMD could not analyse some of the sources under {}: the classes it failed on are "
                    + "measured as if they held no smell. See the report for the errors it recovered from", root);
        }
    }

    /**
     * Reads the sources of a snapshot into the list of files PMD is handed, expressed as the container
     * sees them.
     *
     * @param fileList the file to write the list to
     * @param root     the root directory the sources are measured under
     * @param sources  the source files to analyse
     * @throws MetricsException if the list cannot be written
     */
    private static void writeFileList(Path fileList, Path root, List<Path> sources) throws MetricsException {
        List<String> lines = sources.stream()
                .map(source -> SOURCES_MOUNT + "/" + ClassNameResolver.relativePath(root, source))
                .toList();
        try {
            Files.write(fileList, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MetricsException("Unable to list the sources to analyse in '" + fileList + "'", e);
        }
    }

    /**
     * Copies the ruleset out of the classpath and next to the list of files, since a container cannot
     * read a resource packaged in a jar.
     *
     * @param ruleset the file to copy the ruleset to
     * @throws MetricsException if the resource is missing or cannot be copied
     */
    private void copyRuleset(Path ruleset) throws MetricsException {
        try (InputStream resource = DockerPmdRunner.class.getClassLoader().getResourceAsStream(rulesetResource)) {
            if (resource == null) {
                throw new MetricsException("The ruleset '" + rulesetResource + "' is not on the classpath");
            }
            Files.copy(resource, ruleset, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MetricsException("Unable to stage the ruleset '" + rulesetResource + "'", e);
        }
    }

    /**
     * @return the first of the known locations of a Docker client holding one that can be run
     */
    private static Optional<Path> findDockerClient() {
        return DOCKER_PATHS.stream().filter(Files::isExecutable).findFirst();
    }

    /**
     * @return a directory of its own for the run to stage what the container has to read
     * @throws MetricsException if it cannot be created
     */
    private static Path createStagingDirectory() throws MetricsException {
        try {
            return Files.createTempDirectory(stagingRoot(), "run-");
        } catch (IOException e) {
            throw new MetricsException("Unable to create a directory to run PMD from", e);
        }
    }

    /**
     * Prepares the directory the staging directories of the runs live in, keeping it readable by its
     * owner alone wherever the file system knows how to say so.
     *
     * @return the directory
     * @throws IOException if it cannot be created or closed to the other users of the machine
     */
    private static Path stagingRoot() throws IOException {
        Files.createDirectories(STAGING_ROOT);
        if (STAGING_ROOT.getFileSystem().supportedFileAttributeViews().contains(POSIX_VIEW)) {
            Files.setPosixFilePermissions(STAGING_ROOT, OWNER_ONLY);
        }
        return STAGING_ROOT;
    }

    /**
     * Reads everything a process writes to its standard output and waits for it to finish. The reading
     * comes first on purpose: a process whose output is left unread stops as soon as the pipe fills up,
     * and a report the size of the one a release produces fills it long before the process is done.
     *
     * @param process the process to wait for
     * @param timeout how long it may take, in seconds
     * @return what it wrote, or null if it did not finish in time
     */
    private static String consume(Process process, long timeout) throws IOException, InterruptedException {
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        return output;
    }

    /**
     * @param errorLog the file holding what PMD wrote to its standard error
     * @return its last lines, to say why a run failed, or a note if it cannot be read
     */
    private static String errorTail(Path errorLog) {
        try (Stream<String> lines = Files.lines(errorLog, StandardCharsets.UTF_8)) {
            List<String> tail = lines.toList();
            return String.join(System.lineSeparator(),
                    tail.subList(Math.max(0, tail.size() - ERROR_LOG_TAIL_LINES), tail.size()));
        } catch (IOException e) {
            return "PMD reported nothing that could be read";
        }
    }

    /**
     * Removes the staging directory of a run, deepest entry first. A run that cannot clean up after
     * itself is not a run that failed, so this only warns.
     *
     * @param directory the directory to delete
     */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            entries.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.warn("Unable to delete the directory {} PMD was run from: {}", directory, e.getMessage());
        }
    }
}
