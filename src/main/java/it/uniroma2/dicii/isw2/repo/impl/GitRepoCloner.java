package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.RepoCloner;
import it.uniroma2.dicii.isw2.repo.exception.RepoException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
public class GitRepoCloner implements RepoCloner {

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