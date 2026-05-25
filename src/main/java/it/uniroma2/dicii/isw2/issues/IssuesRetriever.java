package it.uniroma2.dicii.isw2.issues;

import it.uniroma2.dicii.isw2.issues.exception.IssueException;
import it.uniroma2.dicii.isw2.issues.filter.IssueFilter;
import it.uniroma2.dicii.isw2.issues.model.Issue;

import java.util.List;

public interface IssuesRetriever {

    /**
     * Retrieves a list of issues based on the specified filter criteria.
     *
     * @param filter an {@code IssueFilter} object containing the criteria for filtering issues.
     *               The filter may include statuses, types, resolutions, and other relevant fields.
     *               Providing a null filter will retrieve all issues without any filtering.
     * @return a list of {@code Issue} objects that match the specified filter criteria.
     * The returned list may be empty if no issues match the filter.
     * @throws IssueException if an error occurs during the retrieval process,
     *                        such as connectivity issues or unexpected errors, or if the filter is null.
     */
    List<Issue> retrieveIssues(IssueFilter filter) throws IssueException;

}
