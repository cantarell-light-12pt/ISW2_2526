package it.uniroma2.dicii.isw2.repo;

import it.uniroma2.dicii.isw2.repo.exception.RepoException;

import java.nio.file.Path;

public interface RepoCloner {

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

}
