package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassHistory;
import it.uniroma2.dicii.isw2.repo.exception.RepoException;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.errors.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads the history of small repositories whose shape is the one the walk has to be careful about:
 * the branches a project merges back into its main line, and the sources that are no rows of the
 * dataset.
 */
public class ReleaseHistoryAnalyzerTest {

    private static final String MERGED = "src/main/java/app/Merged.java";
    private static final String MOVED = "module/src/main/java/app/Merged.java";
    private static final String TEST_CLASS = "src/test/java/app/MergedTest.java";

    private static final String THREE_LINES = "package app;\nclass Merged {\n}\n";
    private static final String FIVE_LINES = THREE_LINES + "// one\n// two\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * A merge brings in work that was already counted on the branch it came from. Comparing it with
     * its first parent, as a commit with a single parent is compared, would count that work a second
     * time: the two lines the branch added would be worth four, and the branch would be worth two
     * revisions instead of one.
     */
    @Test
    public void testAMergeIsNotCountedTwice() throws RepoException, MetricsException {
        Path repoPath = folder.getRoot().toPath();
        String release;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, MERGED, THREE_LINES);
            commit(git, "First");
            String main = git.getRepository().getFullBranch();

            git.checkout().setCreateBranch(true).setName("feature").call();
            write(repoPath, MERGED, FIVE_LINES);
            commit(git, "On the branch");

            git.checkout().setName(main).call();
            release = git.merge()
                    .include(git.getRepository().resolve("feature"))
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .setMessage("Merge the branch")
                    .call()
                    .getNewHead()
                    .getName();
        } catch (GitAPIException | IOException e) {
            throw new RepoException("Error during test", e);
        }

        ClassHistory history = analyse(repoPath, release).get(MERGED);
        assertEquals("The three lines of the first commit plus the two of the branch, counted once",
                5, history.churn());
        assertEquals("The merge itself is no revision of the class", 2, history.revisions());
        assertEquals(5, history.sizeChange());
    }

    /**
     * The analyzer is given the very same filter the other extractors of the composite are, so a
     * source that is no row of the dataset must not even reach the history.
     */
    @Test
    public void testTheExcludedSourcesAreLeftOut() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String release;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, MERGED, THREE_LINES);
            write(repoPath, TEST_CLASS, "package app;\nclass MergedTest {\n}\n");
            release = commit(git, "First");
        }

        Map<String, ClassHistory> history = analyse(repoPath, release);
        assertTrue("The class under test belongs in the dataset", history.containsKey(MERGED));
        assertNull("The class exercising it does not", history.get(TEST_CLASS));
    }

    /**
     * A class that moved is the same class: a project reorganising its sources — as ZooKeeper did on
     * becoming a Maven project — would otherwise restart the history of every one of its classes,
     * reading the move as the whole project having been written from scratch in a single release.
     */
    @Test
    public void testAMovedClassKeepsTheHistoryItHadBeforeTheMove() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String release;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, MERGED, THREE_LINES);
            commit(git, "First");
            write(repoPath, MERGED, FIVE_LINES);
            commit(git, "Two lines more");
            move(repoPath, MERGED, MOVED);
            release = commit(git, "Move the sources under the module they belong to");
        }

        ClassHistory history = analyse(repoPath, release).get(MOVED);
        assertEquals("The two commits before the move and the move itself", 3, history.revisions());
        assertEquals("A move rewrites no line", 5, history.churn());
        assertEquals(5, history.sizeChange());
    }

    /**
     * The paths a class has been known by are aliases of one another rather than one replacing the
     * next, so that a release still holding its sources where they used to be — a maintenance release
     * of an older line, published after the move — finds them there.
     */
    @Test
    public void testAMovedClassIsFoundUnderBothOfItsPaths() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String moved;
        String notYetMoved;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, MERGED, THREE_LINES);
            String common = commit(git, "First");
            move(repoPath, MERGED, MOVED);
            moved = commit(git, "Move the sources");

            // The line that has not been reorganised yet, branched off before the move
            git.checkout().setStartPoint(common).setCreateBranch(true).setName("maintenance").call();
            write(repoPath, MERGED, FIVE_LINES);
            notYetMoved = commit(git, "Two lines more, where the sources have always been");
        }

        Map<Integer, Map<String, ClassHistory>> history = analyse(repoPath, moved, notYetMoved);
        assertEquals("The release that moved the class knows it under its new path",
                2, history.get(1).get(MOVED).revisions());
        ClassHistory maintained = history.get(2).get(MERGED);
        assertNotNull("The release that has not moved it still knows it under its old one", maintained);
        assertEquals("Its history is the one of the class, not of the path", 3, maintained.revisions());
        assertEquals("A class is as old as the oldest of its paths", 2, maintained.age());
    }

    /**
     * A source promoted out of the excluded directories — a test turned into functional code — is a
     * class the dataset has never held a row of, so it brings no history along with it.
     */
    @Test
    public void testASourceMovedOutOfTheExcludedDirectoriesBringsNoHistory() throws Exception {
        Path repoPath = folder.getRoot().toPath();
        String release;
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, TEST_CLASS, THREE_LINES);
            commit(git, "A class exercising something");
            move(repoPath, TEST_CLASS, MERGED);
            release = commit(git, "Promote it to functional code");
        }

        Map<String, ClassHistory> history = analyse(repoPath, release);
        assertNull("What it was is no row of the dataset", history.get(TEST_CLASS));
        assertEquals("What it became starts a history of its own", 1, history.get(MERGED).revisions());
    }

    /**
     * Reads the history of a repository holding a single release, tagged on the given commit.
     *
     * @param repoPath the root directory of the repository
     * @param commitId the commit the only release is tagged on
     * @return the history of each of its classes
     */
    private static Map<String, ClassHistory> analyse(Path repoPath, String commitId) throws MetricsException {
        return analyse(repoPath, commitId, null).get(1);
    }

    /**
     * Reads the history of a repository holding one or two releases, tagged on the given commits and
     * numbered in the order they are given in.
     *
     * @param repoPath the root directory of the repository
     * @param commitIds the commits the releases are tagged on, the second one optional
     * @return the history of the classes of each release, keyed by its ordinal index
     */
    private static Map<Integer, Map<String, ClassHistory>> analyse(Path repoPath, String... commitIds)
            throws MetricsException {
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        List<Version> versions = new ArrayList<>();
        for (String commitId : commitIds) {
            if (commitId != null) {
                Version version = new Version(String.valueOf(versions.size() + 1),
                        "1." + versions.size() + ".0", true, false);
                version.setCommitId(commitId);
                version.setIndex(versions.size() + 1);
                versions.add(version);
            }
        }
        return new ReleaseHistoryAnalyzer(filter, Set.of()).analyse(repoPath, versions);
    }

    private static void write(Path repoPath, String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * Moves a source file, leaving the commit to whoever called: the move and the edits committed
     * along with it are what tells a pure rename from one the walk has to weigh.
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
        return git.commit()
                .setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com")
                .setSign(false)
                .call()
                .getName();
    }
}
