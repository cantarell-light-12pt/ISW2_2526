package it.uniroma2.dicii.isw2.association;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.repo.model.Commit;

import java.util.List;
import java.util.Map;

public interface IssueCommitAssociator {

    /**
     * Associates a list of Jira issues with their corresponding Git commits.
     * The association is based on the presence of the issue key (e.g., ZOOKEEPER-1)
     * in the commit message.
     *
     * @param issues  the list of Jira issues to associate.
     * @param commits the list of Git commits to search through.
     * @return a map where each key is an Issue and the value is a list of associated Commits.
     */
    Map<Issue, List<Commit>> associate(List<Issue> issues, List<Commit> commits);

}