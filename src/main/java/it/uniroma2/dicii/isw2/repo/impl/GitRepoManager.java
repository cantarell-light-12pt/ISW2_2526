package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.RepoManager;
import it.uniroma2.dicii.isw2.repo.exception.RepoException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class GitRepoManager implements RepoManager {

    /**
     * The outcomes of moving HEAD that leave it pointing at the wanted commit: it either already was
     * there, or it was moved onto it.
     */
    private static final Set<RefUpdate.Result> SUCCESSFUL_HEAD_UPDATES = Collections.unmodifiableSet(
            EnumSet.of(RefUpdate.Result.NEW, RefUpdate.Result.FORCED,
                    RefUpdate.Result.NO_CHANGE, RefUpdate.Result.FAST_FORWARD));

    @Override
    public void cloneRepo(String repoUrl, String repoName, Path destinationPath, boolean forceOverwrite) throws RepoException {
        // Resolve the final directory path (e.g., /base/path/repoName)
        Path targetDirectory = destinationPath.resolve(repoName);

        // 1. Check if the directory already exists
        if (Files.exists(targetDirectory)) {
            if (forceOverwrite) {
                try {
                    log.warn("Directory '{}' already exists. Force overwrite enabled. Deleting existing contents...", targetDirectory);
                    deleteDirectoryRecursively(targetDirectory);
                } catch (IOException e) {
                    log.error("Failed to delete existing directory '{}': {}", targetDirectory, e.getMessage(), e);
                    throw new RepoException("Error while deleting existing repository", e);
                }
            } else {
                // If it exists, and we shouldn't overwrite, log and exit early
                log.info("Repository already exists at '{}'. Skipping clone operation.", targetDirectory);
                return;
            }
        }
        cloneRepo(repoUrl, repoName, targetDirectory);
    }

    @Override
    public void checkoutAtCommit(Path repoPath, String commitId) throws RepoException {
        log.info("Checking out repository at {} to commit {}...", repoPath, commitId);

        // Use try-with-resources to automatically close the Git instance
        try (Git git = Git.open(repoPath.toFile())) {
            Repository repository = git.getRepository();
            moveHeadTo(repository, commitId, resolveCommit(repository, repoPath, commitId));
            // Bringing the working tree in line with the detached HEAD, rather than checking the commit
            // out, is what makes walking the whole release history possible: a checkout compares the
            // working tree against the index, and the stat information JGit itself leaves behind after
            // one checkout makes the next one report untouched binary files as conflicting
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            log.info("Successfully checked out commit {}", commitId);
        } catch (GitAPIException | JGitInternalException e) {
            log.error("Failed to checkout commit '{}' in repository '{}': {}", commitId, repoPath, e.getMessage(), e);
            throw new RepoException("Error while checking out commit " + commitId, e);
        } catch (IOException e) {
            log.error("Failed to open repository at '{}': {}", repoPath, e.getMessage(), e);
            throw new RepoException("Unable to open repository at " + repoPath, e);
        }
    }

    /**
     * Resolves the commit an identifier refers to, making sure the repository really holds it.
     * <p>
     * Resolving alone is not enough to tell: a complete identifier is accepted at face value, without
     * checking that the object it names was ever received, so a version tagged on a commit the clone
     * does not hold would otherwise be noticed only once the working tree is already being rewritten.
     * Reading the commit here also peels an identifier pointing at an annotated tag down to the commit
     * the tag refers to.
     *
     * @param repository the repository the commit is looked up in
     * @param repoPath   the path of the repository, used for reporting
     * @param commitId   the identifier of the wanted commit
     * @return the commit the identifier refers to
     * @throws IOException   if the repository cannot be read
     * @throws RepoException if the repository holds no such commit
     */
    private static ObjectId resolveCommit(Repository repository, Path repoPath, String commitId) throws IOException {
        ObjectId resolved = repository.resolve(commitId);
        if (resolved == null) {
            throw new RepoException("Commit " + commitId + " does not exist in repository " + repoPath);
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            return revWalk.parseCommit(resolved).getId();
        } catch (MissingObjectException | IncorrectObjectTypeException e) {
            throw new RepoException("Commit " + commitId + " does not exist in repository " + repoPath, e);
        }
    }

    /**
     * Points HEAD at the given commit without touching the working tree, leaving the repository in a
     * detached state. Detaching is what keeps the hard reset that follows from dragging the branch that
     * happens to be checked out along with it.
     *
     * @param repository the repository whose HEAD has to be moved
     * @param commitId   the identifier of the commit, used for reporting
     * @param target     the commit HEAD has to point at
     * @throws IOException   if HEAD cannot be read or written
     * @throws RepoException if Git refuses to move HEAD
     */
    private static void moveHeadTo(Repository repository, String commitId, ObjectId target) throws IOException {
        RefUpdate refUpdate = repository.updateRef(Constants.HEAD, true);
        refUpdate.setNewObjectId(target);
        RefUpdate.Result result = refUpdate.forceUpdate();
        if (!SUCCESSFUL_HEAD_UPDATES.contains(result)) {
            throw new RepoException("Unable to move HEAD to commit " + commitId + ": Git reported " + result);
        }
    }

    /**
     * Helper method to forcefully delete a directory and all its contents recursively.
     *
     * @param path The path to the directory to delete.
     * @throws IOException If an I/O error occurs during deletion.
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        // Walk the file tree in reverse order to delete children before parents
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(filePath -> {
                try {
                    Files.delete(filePath);
                } catch (IOException | SecurityException e) {
                    log.error("Failed to delete file: {}", filePath, e);
                }
            });
        }
        log.debug("Successfully deleted existing directory: {}", path);
    }

    /**
     * Clones a remote Git repository to a specified local directory.
     *
     * @param repoUrl         the URL of the remote repository to be cloned
     * @param repoName        the name of the repository being cloned, used for logging and error reporting
     * @param targetDirectory the local directory where the repository will be cloned
     * @throws RepoException if an error occurs during the cloning process, such as a Git-related issue
     *                       or an invalid target directory path
     */
    private void cloneRepo(String repoUrl, String repoName, Path targetDirectory) throws RepoException {
        log.info("Cloning repository from {} to {}...", repoUrl, targetDirectory);

        // Use try-with-resources to automatically close the Git instance
        try (Git ignored = Git.cloneRepository().setURI(repoUrl).setDirectory(targetDirectory.toFile()).call()) {
            log.info("Repository cloned successfully to {}", targetDirectory);
        } catch (GitAPIException e) {
            log.error("Failed to clone the repository '{}': {}", repoName, e.getMessage(), e);
            throw new RepoException("Error while cloning repository", e);
        } catch (InvalidPathException e) {
            log.error("Invalid path: {}", targetDirectory, e);
            throw new RepoException("Unable to clone the repository: invalid destination path", e);
        }
    }
}