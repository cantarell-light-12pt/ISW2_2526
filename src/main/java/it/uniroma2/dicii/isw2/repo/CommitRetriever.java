package it.uniroma2.dicii.isw2.repo;

import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.model.Commit;

import java.nio.file.Path;
import java.util.List;

public interface CommitRetriever {

    /**
     * Retrieves a list of commits from the specified repository.
     *
     * @param repositoryPath the file system path to the local Git repository
     * @return a list of commits present in the repository
     * @throws CommitException if an error occurs while retrieving the commits
     */
    List<Commit> getCommits(Path repositoryPath) throws CommitException;
}
