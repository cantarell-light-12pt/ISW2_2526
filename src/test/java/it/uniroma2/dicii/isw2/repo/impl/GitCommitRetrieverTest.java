package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import org.eclipse.jgit.api.Git;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads the history of a repository shaped the way a project maintaining several released lines is:
 * a main line, and a branch off it carrying the fixes that never reach the main line. The commits of
 * that branch are the ones the metrics counting the bug fixes of a release are about, so a retriever
 * that cannot see them makes every patch release look as though it fixed nothing.
 */
public class GitCommitRetrieverTest {

    private static final String MAIN_SOURCE = "src/main/java/app/Main.java";
    private static final String FIXED_SOURCE = "src/main/java/app/Fixed.java";

    private static final String FIX_MESSAGE = "APP-1 Fixed the leak on close";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private GitCommitRetriever retriever;
    private Path repoPath;
    private String first;
    private String maintenanceFix;
    private String second;

    @Before
    public void setUp() throws Exception {
        retriever = new GitCommitRetriever();
        repoPath = folder.getRoot().toPath();

        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(MAIN_SOURCE, "class Main { }");
            first = commit(git, "First");

            // The branch the repository is created on depends on how Git is configured on the machine
            // running the tests, and so cannot be assumed to be named "master"
            String main = git.getRepository().getFullBranch();

            git.checkout().setCreateBranch(true).setName("maintenance").call();
            write(FIXED_SOURCE, "class Fixed { }");
            maintenanceFix = commit(git, FIX_MESSAGE);

            git.checkout().setName(main).call();
            write(MAIN_SOURCE, "class Main { void added() { } }");
            second = commit(git, "Second");
        }
    }

    /**
     * The regression this test guards: a log read from HEAD alone stops at the branch that happens to
     * be checked out, leaving out precisely the commits a maintained line is made of.
     */
    @Test
    public void testACommitOnAMaintenanceBranchIsRetrieved() throws CommitException {
        Set<String> retrieved = idsOf(retriever.getCommits(repoPath));

        assertTrue("The commit of the maintenance branch should have been retrieved",
                retrieved.contains(maintenanceFix));
        assertEquals(Set.of(first, maintenanceFix, second), retrieved);
    }

    /**
     * The extraction of the metrics leaves HEAD detached at the last release it could measure, and the
     * clone is reused across runs, so a log read from HEAD would report the commits of whichever
     * release the previous run stopped at rather than the ones of the project.
     */
    @Test
    public void testADetachedHeadDoesNotHideTheHistory() throws CommitException {
        new GitRepoManager().checkoutAtCommit(repoPath, first);

        assertEquals(Set.of(first, maintenanceFix, second), idsOf(retriever.getCommits(repoPath)));
    }

    /**
     * Every ref seeds the walk, and a commit is reachable from more than one of them as soon as a
     * release is tagged: it has to be reported once all the same, or it would be counted twice by
     * everything reading the commits.
     */
    @Test
    public void testACommitReachedBySeveralRefsIsRetrievedOnce() throws Exception {
        try (Git git = Git.open(folder.getRoot())) {
            git.tag().setName("release-1.0").setObjectId(git.getRepository().parseCommit(
                    git.getRepository().resolve(maintenanceFix))).call();
        }

        List<Commit> commits = retriever.getCommits(repoPath);

        assertEquals("A commit reached by both a branch and a tag should be retrieved once",
                commits.size(), idsOf(commits).size());
        assertEquals(3, commits.size());
    }

    /**
     * The association with the bug tickets matches on the full message of a commit, and the release
     * history is walked from its identifier: a conversion dropping either of them would leave every
     * commit unusable without failing.
     */
    @Test
    public void testTheConvertedCommitCarriesWhatTheAssociationNeeds() throws CommitException {
        Commit fix = retriever.getCommits(repoPath).stream()
                .filter(commit -> commit.id().equals(maintenanceFix))
                .findFirst()
                .orElse(null);

        assertNotNull("The commit of the maintenance branch should have been retrieved", fix);
        assertTrue("The full message is what the issue key is matched against",
                fix.fullMessage().contains("APP-1"));
        assertEquals(FIX_MESSAGE, fix.shortMessage());
        assertEquals("Test", fix.authorName());
        assertEquals("test@example.com", fix.authorEmail());
        assertEquals(List.of(first), fix.parentIds());
        assertNotNull(fix.date());
    }

    private static Set<String> idsOf(List<Commit> commits) {
        return commits.stream().map(Commit::id).collect(Collectors.toSet());
    }

    private void write(String path, String content) throws IOException {
        Path source = repoPath.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static String commit(Git git, String message) throws Exception {
        git.add().addFilepattern(".").call();
        return git.commit()
                .setMessage(message)
                .setAuthor("Test", "test@example.com")
                .setCommitter("Test", "test@example.com")
                .setSign(false)
                .call()
                .getName();
    }
}
