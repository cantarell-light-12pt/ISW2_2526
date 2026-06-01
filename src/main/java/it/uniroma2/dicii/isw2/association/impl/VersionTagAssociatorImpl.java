package it.uniroma2.dicii.isw2.association.impl;

import it.uniroma2.dicii.isw2.association.VersionTagAssociator;
import it.uniroma2.dicii.isw2.repo.model.Tag;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class VersionTagAssociatorImpl implements VersionTagAssociator {

    private final String tagsPrefix;

    public VersionTagAssociatorImpl(String tagsPrefix) {
        this.tagsPrefix = tagsPrefix;
    }

    @Override
    public void associateTagsToVersions(List<Tag> tags, List<Version> versions) {
        List<Tag> candidates;
        Tag winner;
        int counter = 0;
        for (Version version : versions) {
            candidates = tags.stream().filter(t -> t.label().equals(tagsPrefix + version.getName())).toList();
            if (candidates.isEmpty()) {
                log.warn("No tag found for version {}. This version will be removed.", version.getName());
            } else if (candidates.size() == 1) {
                winner = candidates.getFirst();
                version.setCommitId(winner.commitId());
                log.debug("Associated tag {} to version {}", candidates.getFirst().label(), version.getName());
                if (version.getReleaseDate() == null) {
                    version.setReleaseDate(winner.date().toLocalDate());
                    log.debug("Set release date for version {} to {}", version.getName(), winner.date().toLocalDate());
                }
                counter++;
            } else {
                String tagLabels = candidates.stream().map(Tag::label).collect(java.util.stream.Collectors.joining(", "));
                log.warn("Multiple tags found for version {}: {}", version.getName(), tagLabels);
            }
        }
        log.info("Found a tag for {} out of {} versions", counter, versions.size());
        removeUntaggedVersions(versions);
    }

    /**
     * Removes versions from the provided list that do not have an associated commit ID.
     * This method performs the removal in-place and logs the number of removed versions.
     *
     * @param versions the list of {@code Version} objects to filter. Versions without an associated commit ID
     *                 (where {@code getCommitId} returns {@code null}) will be removed from this list.
     */
    private void removeUntaggedVersions(List<Version> versions) {
        int originalSize = versions.size();
        versions.removeIf(version -> version.getCommitId() == null);
        log.info("Removed {} versions that do not have an associated commit ID", originalSize - versions.size());
    }
}
