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

    public List<Commit> getCommits(Path repositoryPath) throws CommitException {
        List<Commit> commits = new ArrayList<>();

        log.info("Opening repository at {} to retrieve commit history...", repositoryPath);

        try (Git git = Git.open(repositoryPath.toFile())) {
            Iterable<RevCommit> logMessages = git.log().call();
            for (RevCommit revCommit : logMessages)
                try {
                    commits.add(convertCommit(revCommit));
                } catch (DateTimeException e) {
                    log.error("Error converting commit {}: {}. Skipping...", revCommit.getName(), e.getMessage(), e);
                }
            log.info("Successfully retrieved and mapped {} commits.", commits.size());
        } catch (IOException | GitAPIException e) {
            log.error("Error retrieving commits from repository '{}': {}", repositoryPath, e.getMessage(), e);
            throw new CommitException("Error retrieving commits from repository", e);
        }

        return commits;
    }

    private Commit convertCommit(RevCommit revCommit) throws DateTimeException {
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

    }
}
