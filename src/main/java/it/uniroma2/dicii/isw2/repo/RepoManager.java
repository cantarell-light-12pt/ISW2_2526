package it.uniroma2.dicii.isw2.repo;

import it.uniroma2.dicii.isw2.repo.exception.RepoException;

import java.nio.file.Path;

public interface RepoManager {

    /**
     * Clones a remote repository to a specified local file system path.
     *
     * @param repoUrl         the URL of the remote repository to be cloned
     * @param repoName        the name to assign to the cloned repository
     * @param destinationPath the local file system path where the repository should be cloned
     * @param forceOverwrite  whether to overwrite the existing repository at the destination path if it already exists
     * @throws RepoException if an error occurs during the cloning process
     */
    void cloneRepo(String repoUrl, String repoName, Path destinationPath, boolean forceOverwrite) throws RepoException;

    /**
     * Checks out the repository at the commit having the specified identifier,
     * which is all the objects tied to a commit through a tag — such as the released versions of the
     * project — carry.
     *
     * @param repoPath the file system path to the local Git repository
     * @param commitId the identifier of the commit to which the repository should be checked out
     * @throws RepoException if an error occurs during the checkout process
     */
    void checkoutAtCommit(Path repoPath, String commitId) throws RepoException;

}
