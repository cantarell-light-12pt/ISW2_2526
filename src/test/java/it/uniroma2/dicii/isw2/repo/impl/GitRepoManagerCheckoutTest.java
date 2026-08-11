package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.exception.RepoException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Walks a small repository back and forth across its history, the way the extraction of the metrics
 * walks the releases of the mined project.
 */
public class GitRepoManagerCheckoutTest {

    private static final String TRACKED = "Tracked.java";
    private static final String ADDED_LATER = "AddedLater.java";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private GitRepoManager manager;
    private Path repoPath;
    private String first;
    private String second;

    /**
     * The branch the repository is created on, which depends on how Git is configured on the machine
     * running the tests and so cannot be assumed to be named "master".
     */
    private String initialBranch;

    @Before
    public void setUp() throws Exception {
        manager = new GitRepoManager();
        repoPath = folder.getRoot().toPath();

        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            Files.writeString(repoPath.resolve(TRACKED), "class Tracked { }");
            first = commit(git, "First");

            Files.writeString(repoPath.resolve(TRACKED), "class Tracked { void added() { } }");
            Files.writeString(repoPath.resolve(ADDED_LATER), "class AddedLater { }");
            second = commit(git, "Second");

            initialBranch = git.getRepository().getFullBranch();
        }
    }

    @Test
    public void testCheckoutBringsTheWorkingTreeToTheCommit() throws IOException {
        manager.checkoutAtCommit(repoPath, first);
        assertEquals("class Tracked { }", Files.readString(repoPath.resolve(TRACKED)));
        assertFalse("The file added by the later commit should be gone",
                Files.exists(repoPath.resolve(ADDED_LATER)));

        manager.checkoutAtCommit(repoPath, second);
        assertEquals("class Tracked { void added() { } }", Files.readString(repoPath.resolve(TRACKED)));
        assertTrue(Files.exists(repoPath.resolve(ADDED_LATER)));
    }

    /**
     * Walking the whole release history means checking one commit out after another without ever
     * cleaning up in between, which is precisely what a plain checkout is unable to do: the stat
     * information JGit leaves behind after checking a commit out makes it report untouched files as
     * conflicting on the next one.
     */
    @Test
    public void testHistoryCanBeWalkedRepeatedly() throws IOException {
        for (int round = 0; round < 3; round++) {
            manager.checkoutAtCommit(repoPath, first);
            assertEquals("class Tracked { }", Files.readString(repoPath.resolve(TRACKED)));
            manager.checkoutAtCommit(repoPath, second);
            assertEquals("class Tracked { void added() { } }", Files.readString(repoPath.resolve(TRACKED)));
        }
    }

    @Test
    public void testCheckoutDetachesHeadAndLeavesBranchesAlone() throws Exception {
        manager.checkoutAtCommit(repoPath, first);

        try (Git git = Git.open(folder.getRoot())) {
            Repository repository = git.getRepository();
            assertEquals(first, repository.resolve(Constants.HEAD).name());
            // A detached HEAD reports the commit it points at, rather than the branch it is on
            assertFalse("HEAD should be detached", repository.getFullBranch().startsWith(Constants.R_HEADS));
            assertEquals("Hard resetting must not drag the branch onto the checked out commit",
                    second, repository.resolve(initialBranch).name());
        }
    }

    @Test
    public void testUnknownCommitIsRejected() {
        assertThrows(RepoException.class, () -> manager.checkoutAtCommit(repoPath,
                "0000000000000000000000000000000000000000"));
    }

    private String commit(Git git, String message) throws Exception {
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
