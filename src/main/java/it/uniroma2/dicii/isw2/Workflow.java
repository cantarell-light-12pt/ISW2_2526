package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.issues.IssuesRetriever;
import it.uniroma2.dicii.isw2.issues.exception.IssueException;
import it.uniroma2.dicii.isw2.issues.filter.IssueFilter;
import it.uniroma2.dicii.isw2.issues.impl.JiraIssuesRetriever;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import it.uniroma2.dicii.isw2.repo.CommitRetriever;
import it.uniroma2.dicii.isw2.repo.RepoCloner;
import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.impl.GitCommitRetriever;
import it.uniroma2.dicii.isw2.repo.impl.GitRepoCloner;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import it.uniroma2.dicii.isw2.versions.VersionsRetriever;
import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.impl.JiraVersionsRetrieverImpl;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class Workflow {

    private final String projectName;
    private final String jiraVersionUrl;
    private final String jiraIssuesUrl;
    private final String repoUrl;
    private final Path repoBasePath;
    private final Boolean forceOverwrite;

    public Workflow() {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        String jiraBaseUrl = PropertiesManager.getInstance().getProperty("project.jira.baseUrl");
        String jiraVersionsParameterizedPath = PropertiesManager.getInstance().getProperty("project.jira.versionsParameterizedPath");
        this.jiraVersionUrl = String.format(jiraVersionsParameterizedPath, jiraBaseUrl, projectName);
        this.jiraIssuesUrl = String.format("%s/search?jql=\"project\"=\"%s\"", jiraBaseUrl, projectName);
        this.repoUrl = PropertiesManager.getInstance().getProperty("project.repo.url");
        this.repoBasePath = Path.of(PropertiesManager.getInstance().getProperty("project.repo.basePath"));
        this.forceOverwrite = Boolean.parseBoolean(PropertiesManager.getInstance().getProperty("project.repo.forceOverwrite"));
    }

    public void execute() {
        try {
            // 1. Retrieve the list of versions from Jira
            List<Version> versions = retrieveVersions();

            // 2. Retrieve the list of issues from Jira
            List<Issue> issues = retrieveIssues();

            // 3. Clone the repository
            cloneRepo();

            // 4. Retrieve the list of commits from the repository
            List<Commit> commits = retrieveCommits();

            log.info("Workflow completed successfully!");
        } catch (VersionsException e) {
            log.error("Error retrieving versions from Jira", e);
        } catch (IssueException e) {
            log.error("Error retrieving issues from Jira", e);
        }
    }

    /**
     * Retrieves a list of versions from the Jira Versions API.
     * The returned list contains Version objects, each providing details
     * such as version id, name, release date, and status (released or overdue).
     * <p>
     * Logged messages include basic debugging information about the versions retrieved.
     *
     * @return a list of Version objects representing the available versions.
     * @throws VersionsException if there is an error retrieving or parsing the versions.
     */
    private List<Version> retrieveVersions() throws VersionsException {
        VersionsRetriever versionsRetriever = new JiraVersionsRetrieverImpl(jiraVersionUrl);
        List<Version> versions = versionsRetriever.retrieveVersions();
        versions.forEach(version -> log.debug("Version: {}", version.getName()));
        return versions;
    }

    /**
     * Retrieves a filtered list of issues from the Jira Issues API.
     * The method uses predefined filter criteria to include only issues of type "BUG"
     * that are resolved with a "FIXED" resolution and have statuses "RESOLVED" or "CLOSED".
     * Debug logs are generated for each issue retrieved, including its key.
     *
     * @return a list of {@code Issue} objects matching the filter criteria.
     * The list may be empty if no issues satisfy the criteria.
     * @throws IssueException if an error occurs during the retrieval process or if filtering fails.
     */
    private List<Issue> retrieveIssues() throws IssueException {
        IssuesRetriever issuesRetriever = new JiraIssuesRetriever(jiraIssuesUrl);
        IssueFilter filter = new IssueFilter();
        filter.setTypes(List.of(IssueType.BUG));
        filter.setResolutions(List.of(ResolutionType.FIXED));
        filter.setStatuses(List.of(IssueStatus.RESOLVED, IssueStatus.CLOSED));
        List<Issue> issues = issuesRetriever.retrieveIssues(filter);
        issues.forEach(issue -> log.debug("Issue: {}", issue.getKey()));
        return issues;
    }

    /**
     * Clones a remote repository to a local file system directory using the configured repository URL,
     * project name, and destination base path. The repository is cloned into a subdirectory of the base path
     * named after the project. If the target directory already exists, it can be overwritten based on the
     * specified configuration.
     * <p>
     * The method utilizes the {@code RepoCloner} interface, specifically the {@code GitRepoCloner} implementation,
     * to perform the actual cloning operation. Debug logs are generated to provide information about the cloning
     * process, including completion status and error details if the operation fails.
     * <p>
     * The following fields are used during the cloning operation:
     * - {@code repoUrl}: The URL of the remote repository to be cloned.
     * - {@code projectName}: The name of the project, which is used as the repository's clone directory name.
     * - {@code repoBasePath}: The local base path where the repository will be cloned.
     * - {@code forceOverwrite}: A flag indicating whether to overwrite the target directory if it already exists.
     * <p>
     * Debug log messages include:
     * - A message indicating successful cloning with the target repository path and URL.
     * - Potential warnings or errors related to overwriting an existing directory or other issues.
     * <p>
     * This method is invoked during the execution of the overall workflow to ensure the repository is properly
     * prepared for subsequent operations.
     */
    private void cloneRepo() {
        RepoCloner repoCloner = new GitRepoCloner();
        repoCloner.cloneRepo(repoUrl, projectName, repoBasePath, forceOverwrite);
        log.debug("Repository cloned successfully from {} to {}", repoUrl, repoBasePath.resolve(projectName));
    }

    /**
     * Retrieves the list of commits present in the specified Git repository.
     * This method uses an instance of {@code CommitRetriever} to fetch
     * and process commit information from the repository located at the provided path.
     * Debug logs are generated for each commit retrieved, displaying its unique identifier.
     *
     * @return a list of {@code Commit} objects retrieved from the repository
     * @throws CommitException if an error occurs while retrieving or processing the commits
     */
    private List<Commit> retrieveCommits() throws CommitException {
        CommitRetriever commitRetriever = new GitCommitRetriever();
        List<Commit> commits = commitRetriever.getCommits(repoBasePath.resolve(projectName));
        commits.forEach(commit -> log.debug("Retrieved commit: {}", commit.id()));
        return commits;
    }

}
