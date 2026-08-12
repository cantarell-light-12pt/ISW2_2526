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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads the history of small repositories whose shape is the one the walk has to be careful about:
 * the branches a project merges back into its main line, and the sources that are no rows of the
 * dataset.
 */
public class ReleaseHistoryAnalyzerTest {

    private static final String MERGED = "src/main/java/app/Merged.java";
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
     * Reads the history of a repository holding a single release, tagged on the given commit.
     *
     * @param repoPath the root directory of the repository
     * @param commitId the commit the only release is tagged on
     * @return the history of each of its classes
     */
    private static Map<String, ClassHistory> analyse(Path repoPath, String commitId) throws MetricsException {
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        Version version = new Version("1", "1.0.0", true, false);
        version.setCommitId(commitId);
        version.setIndex(1);
        return new ReleaseHistoryAnalyzer(filter, Set.of())
                .analyse(repoPath, List.of(version))
                .get(1);
    }

    private static void write(Path repoPath, String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String commit(Git git, String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        return git.commit()
                .setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com")
                .setSign(false)
                .call()
                .getName();
    }
}
