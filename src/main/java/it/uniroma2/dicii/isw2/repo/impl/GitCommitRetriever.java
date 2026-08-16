package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.CommitRetriever;
import it.uniroma2.dicii.isw2.repo.exception.CommitException;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class GitCommitRetriever implements CommitRetriever {

    @Override
    public List<Commit> getCommits(Path repositoryPath) throws CommitException {
        List<Commit> commits = new ArrayList<>();

        log.info("Opening repository at {} to retrieve commit history...", repositoryPath);

        try (Git git = Git.open(repositoryPath.toFile())) {
            // The log is read from every ref, and not from HEAD alone, because the branch that happens
            // to be checked out is not where the history of the project is: the fixes of a patch release
            // are committed on the maintenance branch of its line, so walking HEAD leaves every one of
            // them out of the association with the bug tickets. The extraction of the metrics moreover
            // leaves HEAD detached at the last release it measured, and the clone is reused across runs,
            // so walking HEAD would also make the commits retrieved depend on where the previous run
            // stopped
            Iterable<RevCommit> logMessages = git.log().all().call();
            Commit commit;
            for (RevCommit revCommit : logMessages) {
                commit = convertCommit(revCommit);
                if (commit != null) commits.add(commit);
            }
            log.info("Successfully retrieved and mapped {} commits.", commits.size());
        } catch (IOException | GitAPIException e) {
            log.error("Error retrieving commits from repository '{}': {}", repositoryPath, e.getMessage(), e);
            throw new CommitException("Error retrieving commits from repository", e);
        }

        return commits;
    }

    /**
     * Converts a {@link RevCommit} object to a custom {@link Commit} record.
     *
     * @param revCommit the {@link RevCommit} object representing the commit to be converted
     * @return a {@link Commit} instance containing the details of the converted commit,
     * including ID, messages, author information, commit date, and parent commit IDs
     * @throws DateTimeException if any error occurs while converting the commit date to {@link ZonedDateTime}
     */
    private Commit convertCommit(RevCommit revCommit) throws DateTimeException {
        try {
            // PersonIdent contains author details and the timestamp of the commit
            PersonIdent authorIdent = revCommit.getAuthorIdent();
            // Convert JGit's time representation to a modern Java ZonedDateTime
            ZonedDateTime commitDate = ZonedDateTime.ofInstant(authorIdent.getWhenAsInstant(), authorIdent.getZoneId());
            // Extract parent commit hashes
            List<String> parentIds = Arrays.stream(revCommit.getParents()).map(RevCommit::getName).toList();
            // Construct the custom record
            Commit commit = new Commit(revCommit.getName(), revCommit.getShortMessage(), revCommit.getFullMessage(), authorIdent.getName(), authorIdent.getEmailAddress(), commitDate, parentIds);
            log.debug("Retrieved commit: {} by {}", commit.id(), commit.authorName());
            return commit;
        } catch (DateTimeException e) {
            log.error("Error converting commit {}: {}. Skipping...", revCommit.getName(), e.getMessage(), e);
            return null;
        }
    }
}
