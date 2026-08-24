package it.uniroma2.dicii.isw2.buggyness.impl;

import it.uniroma2.dicii.isw2.buggyness.exception.BuggynessException;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.impl.PathSourceFilter;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Reads the fixes of small repositories whose shape is the one the reading has to be careful about:
 * the layouts a project moves its sources between, the commits that move rather than change them, and
 * the sources that are no rows of the dataset.
 */
public class FixedClassesAnalyzerTest {

    /**
     * Where the class lives while the project is built with Ant, and where it lives once it is built
     * with Maven. The two paths name one class, which is the reason a fix reports the class by name.
     */
    private static final String ANT_PATH = "src/java/main/app/Broken.java";
    private static final String MAVEN_PATH = "module/src/main/java/app/Broken.java";

    private static final String TEST_PATH = "src/test/java/app/BrokenTest.java";

    private static final String BROKEN = "app.Broken";

    private static final String SOURCE = "package app;\nclass Broken {\n}\n";
    private static final String FIXED_SOURCE = "package app;\nclass Broken {\n// fixed\n}\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Reads the classes fixed by a repository holding a single defect, closed by a single commit.
     *
     * @param repoPath the root directory of the repository
     * @param commitId the commit that closed it
     * @return the classes it fixed
     */
    private static Set<String> analyse(Path repoPath, String commitId) throws BuggynessException {
        Map<Issue, List<Commit>> fixes = new HashMap<>();
        fixes.put(defect("PROJ-1"), List.of(commitOf(commitId)));
        return analyse(repoPath, fixes).getOrDefault("PROJ-1", Set.of());
    }

    private static Map<String, Set<String>> analyse(Path repoPath, Map<Issue, List<Commit>> fixes) throws BuggynessException {
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        return new FixedClassesAnalyzer(filter).analyse(repoPath, fixes);
    }

    private static Issue defect(String key) {
        return new Issue(key, LocalDateTime.now(), LocalDateTime.now(), key, IssueType.BUG, "assignee", ResolutionType.FIXED, key, IssueStatus.CLOSED);
    }

    private static Commit commitOf(String id) {
        return new Commit(id, "message", "message", "Alice", "alice@example.com", ZonedDateTime.now(), new ArrayList<>());
    }

    private static void write(Path repoPath, String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * Writes a source file whose bytes are not valid UTF-8, which encoding the content as Latin-1 is
     * enough to produce: an accented letter becomes a single byte no UTF-8 decoder accepts on its own.
     */
    private static void writeBytes(Path repoPath, String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * Moves a source file, leaving the commit to whoever called: the move and the edits committed
     * along with it are what tells a pure rename from one that also fixed the class.
     */
    private static void move(Path repoPath, String from, String to) throws IOException {
        Path target = repoPath.resolve(to);
        Files.createDirectories(target.getParent());
        Files.move(repoPath.resolve(from), target);
    }

    private static String commit(Git git, String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        // Staging the tracked files as well is what records the source a move left behind as deleted,
        // without which the move would read as the file having been written anew
        git.add().setUpdate(true).addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor("Alice", "alice@example.com").setCommitter("Alice", "alice@example.com").setSign(false).call().getName();
    }

    /**
     * The reason the classes are reported by name and not by path: a fix committed while the project
     * kept its sources where Ant wanted them has to be joined onto the rows of the releases that keep
     * them where Maven wants them, and only the name is the same in both.
     */
    @Test
    public void testAClassIsNamedByThePackageItDeclaresAndNotByItsPath() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            write(repoPath, ANT_PATH, FIXED_SOURCE);
            fix = commit(git, "Fix it");
        }

        assertEquals(Set.of(BROKEN), analyse(repoPath, fix));
    }

    @Test
    public void testAClassDeclaringNoPackageIsNamedAfterItsFileAlone() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, "class Broken {\n}\n");
            commit(git, "Write it");
            write(repoPath, ANT_PATH, "class Broken {\n// fixed\n}\n");
            fix = commit(git, "Fix it");
        }

        assertEquals(Set.of("Broken"), analyse(repoPath, fix));
    }

    /**
     * The package has to be read as the compiler reads it, and not looked for line by line: a licence
     * header is a comment, {@code package} is an ordinary English word, and ZooKeeper's own headers do
     * talk about packages.
     */
    @Test
    public void testThePackageIsNotTakenFromAComment() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String source = """
                /*
                 * Licensed to the ASF under one or more contributor licence agreements.
                package wrong;
                 */
                // package alsowrong;
                package app;
                class Broken {
                }
                """;
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, source);
            commit(git, "Write it");
            write(repoPath, ANT_PATH, source + "// fixed\n");
            fix = commit(git, "Fix it");
        }

        assertEquals(Set.of(BROKEN), analyse(repoPath, fix));
    }

    /**
     * A history spanning twenty years holds sources whose comments are not valid UTF-8. The package
     * declaration is ASCII in every one of them, so the reading has to replace what it cannot decode
     * rather than give up on the file.
     */
    @Test
    public void testASourceThatIsNotValidUtf8IsStillRead() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            writeBytes(repoPath, ANT_PATH, "// café\n" + SOURCE);
            commit(git, "Write it");
            writeBytes(repoPath, ANT_PATH, "// café\n" + FIXED_SOURCE);
            fix = commit(git, "Fix it");
        }

        assertEquals(Set.of(BROKEN), analyse(repoPath, fix));
    }

    /**
     * A class that only moved was not fixed, whatever the ticket the commit that moved it cites. It
     * matters because a project reorganising its sources moves hundreds of them in a single commit —
     * the one that made ZooKeeper a Maven project moved 355 — so a fix bundled with a reorganisation
     * would otherwise label the whole project.
     */
    @Test
    public void testAPureMoveFixesNothing() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            move(repoPath, ANT_PATH, MAVEN_PATH);
            fix = commit(git, "Become a Maven project");
        }

        assertTrue(analyse(repoPath, fix).isEmpty());
    }

    @Test
    public void testAMoveThatAlsoChangesTheClassFixesIt() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            move(repoPath, ANT_PATH, MAVEN_PATH);
            write(repoPath, MAVEN_PATH, FIXED_SOURCE);
            fix = commit(git, "Move it and fix it");
        }

        assertEquals(Set.of(BROKEN), analyse(repoPath, fix));
    }

    @Test
    public void testADeletedClassIsFixedNowhere() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            Files.delete(repoPath.resolve(ANT_PATH));
            fix = commit(git, "Drop it");
        }

        assertTrue(analyse(repoPath, fix).isEmpty());
    }

    /**
     * The analyzer is given the very same filter the extractors of the composite are, so a source that
     * is no row of the dataset must not be labelled: a fix in a test class says nothing about the
     * class it exercises.
     */
    @Test
    public void testTheExcludedSourcesAreLeftOut() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, TEST_PATH, "package app;\nclass BrokenTest {\n}\n");
            commit(git, "Write it");
            write(repoPath, TEST_PATH, "package app;\nclass BrokenTest {\n// fixed\n}\n");
            fix = commit(git, "Fix it");
        }

        assertTrue(analyse(repoPath, fix).isEmpty());
    }

    /**
     * Diffing a merge against its first parent would credit the fix with every class the branch it
     * merged happened to touch, which on a release branch is most of the project.
     */
    @Test
    public void testAMergeFixesNothing() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            String main = git.getRepository().getFullBranch();

            git.checkout().setCreateBranch(true).setName("feature").call();
            write(repoPath, ANT_PATH, FIXED_SOURCE);
            commit(git, "Fix it on the branch");

            git.checkout().setName(main).call();
            fix = git.merge().include(git.getRepository().resolve("feature")).setFastForward(MergeCommand.FastForwardMode.NO_FF).setMessage("Merge the branch").call().getNewHead().getName();
        }

        assertTrue(analyse(repoPath, fix).isEmpty());
    }

    /**
     * A commit citing two tickets fixed both of them, and is read once.
     */
    @Test
    public void testACommitClosingTwoDefectsFixesTheClassForBothOfThem() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            write(repoPath, ANT_PATH, FIXED_SOURCE);
            fix = commit(git, "Fix PROJ-1 and PROJ-2");
        }

        Map<Issue, List<Commit>> fixes = new LinkedHashMap<>();
        fixes.put(defect("PROJ-1"), List.of(commitOf(fix)));
        fixes.put(defect("PROJ-2"), List.of(commitOf(fix)));

        Map<String, Set<String>> fixed = analyse(repoPath, fixes);
        assertEquals(Set.of(BROKEN), fixed.get("PROJ-1"));
        assertEquals(Set.of(BROKEN), fixed.get("PROJ-2"));
    }

    /**
     * A defect whose fix changed no class the dataset holds a row of is no entry of the result, rather
     * than an entry holding nothing.
     */
    @Test
    public void testADefectFixedInNoClassIsLeftOut() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, "README.md", "Nothing to see here\n");
            fix = commit(git, "Write the readme");
        }

        Map<Issue, List<Commit>> fixes = new HashMap<>();
        fixes.put(defect("PROJ-1"), List.of(commitOf(fix)));

        assertTrue(analyse(repoPath, fixes).isEmpty());
    }

    /**
     * A commit the clone does not hold — a fix on a branch that was never fetched, say — costs the
     * labels of that fix, and must not cost the labels of every other one.
     */
    @Test
    public void testACommitTheCloneDoesNotHoldIsSkipped() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String fix;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
            write(repoPath, ANT_PATH, FIXED_SOURCE);
            fix = commit(git, "Fix it");
        }

        Map<Issue, List<Commit>> fixes = new LinkedHashMap<>();
        fixes.put(defect("PROJ-1"), List.of(commitOf("0123456789012345678901234567890123456789")));
        fixes.put(defect("PROJ-2"), List.of(commitOf(fix)));

        Map<String, Set<String>> fixed = analyse(repoPath, fixes);
        assertEquals(Set.of("PROJ-2"), fixed.keySet());
    }

    @Test
    public void testARepositoryWithoutDefectsYieldsNothing() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, ANT_PATH, SOURCE);
            commit(git, "Write it");
        }

        assertTrue(analyse(repoPath, new HashMap<>()).isEmpty());
    }
}
