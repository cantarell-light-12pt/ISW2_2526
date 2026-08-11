package it.uniroma2.dicii.isw2;

import it.uniroma2.dicii.isw2.association.VersionTagAssociator;
import it.uniroma2.dicii.isw2.association.impl.JiraGitAssociator;
import it.uniroma2.dicii.isw2.association.impl.VersionTagAssociatorImpl;
import it.uniroma2.dicii.isw2.issues.IssuesRetriever;
import it.uniroma2.dicii.isw2.issues.exception.IssueException;
import it.uniroma2.dicii.isw2.issues.filter.IssueFilter;
import it.uniroma2.dicii.isw2.issues.impl.JiraIssuesRetriever;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.impl.CKExtractor;
import it.uniroma2.dicii.isw2.metrics.impl.CompositeMetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.impl.JavaParserExtractor;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.properties.PropertiesManager;
import it.uniroma2.dicii.isw2.proportion.ProportionStrategy;
import it.uniroma2.dicii.isw2.proportion.ProportionStrategyFactory;
import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.repo.CommitRetriever;
import it.uniroma2.dicii.isw2.repo.RepoManager;
import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.exception.RepoException;
import it.uniroma2.dicii.isw2.repo.impl.GitCommitRetriever;
import it.uniroma2.dicii.isw2.repo.impl.GitRepoManager;
import it.uniroma2.dicii.isw2.repo.impl.GitTagsRetriever;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import it.uniroma2.dicii.isw2.repo.model.Tag;
import it.uniroma2.dicii.isw2.versions.VersionsRetriever;
import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.impl.JiraVersionsRetrieverImpl;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Workflow {

    private final String projectName;
    private final String jiraVersionUrl;
    private final String jiraIssuesUrl;
    private final String repoUrl;
    private final Path repoBasePath;
    private final Boolean forceOverwrite;
    private final String tagsPrefix;
    private final String proportionMethod;

    public Workflow() {
        this.projectName = PropertiesManager.getInstance().getProperty("project.name");
        String jiraBaseUrl = PropertiesManager.getInstance().getProperty("project.jira.baseUrl");
        String jiraVersionsParameterizedPath = PropertiesManager.getInstance().getProperty("project.jira.versionsParameterizedPath");
        this.jiraVersionUrl = String.format(jiraVersionsParameterizedPath, jiraBaseUrl, projectName);
        this.jiraIssuesUrl = String.format("%s/search?jql=\"project\"=\"%s\"", jiraBaseUrl, projectName);
        this.repoUrl = PropertiesManager.getInstance().getProperty("project.repo.url");
        this.repoBasePath = Path.of(PropertiesManager.getInstance().getProperty("project.repo.basePath"));
        this.forceOverwrite = Boolean.parseBoolean(PropertiesManager.getInstance().getProperty("project.repo.forceOverwrite"));
        this.tagsPrefix = PropertiesManager.getInstance().getProperty("project.tags.prefix");
        this.proportionMethod = PropertiesManager.getInstance().getProperty("project.proportion.method");
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

            // 5. Associate issues with commits
            Map<Issue, List<Commit>> associations = associateCommitsToIssues(issues, commits);

            // 6. Retrieve the tags from the repository and associate them with versions
            getTagsAndAssociateWithVersions(versions);

            // 7. Estimate the injected version of the issues that do not report a usable one
            applyProportion(issues, versions);

            // 8. Extract the class-level metrics of every released version
            Map<String, MetricsReport> metrics = extractMetrics(versions);
            log.info("The dataset will hold {} rows", metrics.values().stream().mapToInt(MetricsReport::size).sum());

            log.info("Workflow completed successfully!");
        } catch (VersionsException e) {
            log.error("Error retrieving versions from Jira", e);
        } catch (IssueException e) {
            log.error("Error retrieving issues from Jira", e);
        } catch (ProportionException e) {
            log.error("Error estimating the injected versions of the issues", e);
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
        RepoManager repoCloner = new GitRepoManager();
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

    /**
     * Associates a list of Jira issues with a list of Git commits based on matching patterns.
     * Each issue is examined to find commits that reference its key within the commit's
     * full message. The results are returned as a mapping between issues and their associated commits.
     *
     * @param issues  the list of {@code Issue} objects representing Jira issues to be associated
     * @param commits the list of {@code Commit} objects representing Git commits to be matched
     * @return a {@code Map<Issue, List<Commit>>} where each key is an {@code Issue} and its value
     * is a list of {@code Commit} objects that reference the issue
     */
    private Map<Issue, List<Commit>> associateCommitsToIssues(List<Issue> issues, List<Commit> commits) {
        JiraGitAssociator associator = new JiraGitAssociator();
        return associator.associate(issues, commits);
    }

    /**
     * Retrieves Git tags from the repository, associates them with the provided versions,
     * and removes versions that have no associated commit ID.
     * <p>
     * This method performs the following tasks:
     * <ol>
     * <li> Fetches Git tags from the repository located at the path resolved using {@code repoBasePath} and {@code projectName}.</li>
     * <li> Associates the retrieved tags with the provided versions using an implementation of {@code VersionTagAssociator}.</li>
     * <li> Removes versions that do not have an associated commit ID from the provided list.</li>
     * <li> Renumbers the surviving versions, so that their ordinal indices stay contiguous.</li>
     * </ol>
     *
     * @param versions the list of {@code Version} objects to associate with Git tags;
     *                 versions without an associated commit ID will be removed from this list.
     */
    private void getTagsAndAssociateWithVersions(List<Version> versions) {
        List<Tag> tags = new GitTagsRetriever().getTags(repoBasePath.resolve(projectName));
        VersionTagAssociator associator = new VersionTagAssociatorImpl(tagsPrefix);
        associator.associateTagsToVersions(tags, versions);
        // Dropping the untagged versions leaves gaps in the numbering assigned at retrieval time, which
        // would distort every difference between version indices computed by the Proportion method
        Version.numberVersions(versions);
        log.debug("Renumbered the {} versions having an associated tag", versions.size());
    }

    /**
     * Estimates the injected version of the issues that do not report a usable one, applying the variant
     * of the Proportion approach configured through the {@code project.proportion.method} property.
     * <p>
     * The issues are modified in place: each of them gets its opening version, and the ones whose
     * injected version had to be estimated also get their injected version and their affected versions.
     *
     * @param issues   the issues to label
     * @param versions the released versions of the project, already associated with their Git tags
     * @throws ProportionException if the configured method is unknown, or if the issues cannot be
     *                             placed in the release history
     */
    private void applyProportion(List<Issue> issues, List<Version> versions) throws ProportionException {
        ProportionStrategy strategy = new ProportionStrategyFactory().getStrategy(proportionMethod);
        strategy.applyProportion(issues, versions);
    }

    /**
     * Extracts the class-level metrics of every released version of the project.
     * <p>
     * The repository is checked out at the commit each version is tagged on, so that the sources being
     * measured are the ones the version was released with, and the whole set of extractors is then run
     * on that snapshot. A version whose snapshot cannot be checked out or measured is left out of the
     * result instead of aborting the extraction of the remaining ones. The repository is left checked
     * out at the newest version that could be measured.
     *
     * @param versions the released versions of the project, already associated with their Git tags
     * @return the metrics of the classes of each version, keyed by version name and ordered from the
     * oldest version to the newest
     */
    private Map<String, MetricsReport> extractMetrics(List<Version> versions) {
        Path repoPath = repoBasePath.resolve(projectName);
        RepoManager repoManager = new GitRepoManager();
        // The extractors measuring the evolution and the code smells of a class will join the composite
        // as further children, leaving this step unchanged
        MetricsExtractor extractor = new CompositeMetricsExtractor()
                .add(new CKExtractor())
                .add(new JavaParserExtractor());

        Map<String, MetricsReport> metrics = new LinkedHashMap<>();
        for (Version version : versions) {
            try {
                repoManager.checkoutAtCommit(repoPath, version.getCommitId());
                metrics.put(version.getName(), extractor.extract(repoPath));
            } catch (RepoException | MetricsException e) {
                log.error("Unable to extract the metrics of version {}. Skipping it...", version.getName(), e);
            }
        }
        log.info("Extracted the class-level metrics of {} versions out of {}", metrics.size(), versions.size());
        return metrics;
    }

}
