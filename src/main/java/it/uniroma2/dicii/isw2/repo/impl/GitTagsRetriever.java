package it.uniroma2.dicii.isw2.repo.impl;

import it.uniroma2.dicii.isw2.repo.TagsRetriever;
import it.uniroma2.dicii.isw2.repo.exception.TagException;
import it.uniroma2.dicii.isw2.repo.model.Tag;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GitTagsRetriever implements TagsRetriever {

    @Override
    public List<Tag> getTags(Path repositoryPath) throws TagException {
        List<Tag> tags = new ArrayList<>();
        log.info("Opening repository at {} to retrieve tags...", repositoryPath);

        try (Git git = Git.open(repositoryPath.toFile())) {
            Repository repository = git.getRepository();
            RevWalk revWalk = new RevWalk(repository);

            List<Ref> call = git.tagList().call();
            for (Ref ref : call) {
                // Strip the standard "refs/tags/" prefix to get a clean label
                String label = ref.getName().replace("refs/tags/", "");
                log.debug("Found tag: {}", label);

                // Peel the ref to resolve annotated tags to their actual commits
                Ref peeledRef = repository.getRefDatabase().peel(ref);
                ObjectId objectId = peeledRef.getPeeledObjectId() != null ? peeledRef.getPeeledObjectId() : peeledRef.getObjectId();

                if (objectId != null) {
                    RevCommit revCommit = revWalk.parseCommit(objectId);
                    Tag tag = convertTag(label, revCommit);
                    if (tag != null) {
                        tags.add(tag);
                        log.debug("Converted tag: {}", tag);
                    }
                }
            }
            log.info("Successfully retrieved {} tags.", tags.size());
        } catch (IOException | GitAPIException e) {
            log.error("Error retrieving tags from repository '{}': {}", repositoryPath, e.getMessage(), e);
            throw new TagException("Error retrieving tags from repository", e);
        } return tags;
    }

    /**
     * Converts a Git tag and its associated commit information into a {@code Tag} object.
     * <p>
     * This method processes a given tag label and its corresponding commit to create a Tag object
     * containing metadata such as the tag label, commit identifier, and the commit timestamp in
     * a {@code ZonedDateTime} format. If the commit date cannot be converted, the method logs
     * an error and returns {@code null}.
     *
     * @param label     the label of the Git tag to be converted
     * @param revCommit the {@code RevCommit} object representing the commit associated with the tag
     * @return a {@code Tag} object representing the converted tag, or {@code null} if an error occurs during the conversion
     */
    private Tag convertTag(String label, RevCommit revCommit) {
        try {
            PersonIdent authorIdent = revCommit.getAuthorIdent();
            ZonedDateTime commitDate = ZonedDateTime.ofInstant(authorIdent.getWhenAsInstant(), authorIdent.getZoneId());
            Tag tag = new Tag(label, revCommit.getName(), commitDate);
            log.debug("Retrieved tag: {} pointing to commit {}", tag.label(), tag.commitId());
            return tag;
        } catch (DateTimeException e) {
            log.error("Error converting date for tag {} (commit {}): {}. Skipping...", label, revCommit.getName(), e.getMessage(), e);
            return null;
        }
    }
}