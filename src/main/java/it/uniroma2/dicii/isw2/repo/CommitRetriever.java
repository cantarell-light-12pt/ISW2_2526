package it.uniroma2.dicii.isw2.repo;

import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.model.Commit;

import java.nio.file.Path;
import java.util.List;

public interface CommitRetriever {

    /**
     * Retrieves a list of commits from the specified repository.
     * <p>
     * The commits are the ones of the whole repository, i.e. the ones reachable from any of its refs,
     * and not only the ones of the branch that happens to be checked out: a project maintains a branch
     * per released line, and the commits that fixed the bugs of a patch release are on it.
     *
     * @param repositoryPath the file system path to the local Git repository
     * @return a list of commits present in the repository
     * @throws CommitException if an error occurs while retrieving the commits
     */
    List<Commit> getCommits(Path repositoryPath) throws CommitException;
}
