package it.uniroma2.dicii.isw2.association.impl;

import it.uniroma2.dicii.isw2.association.VersionTagAssociator;
import it.uniroma2.dicii.isw2.repo.model.Tag;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
public class VersionTagAssociatorImpl implements VersionTagAssociator {

    private final String tagsPrefix;

    public VersionTagAssociatorImpl(String tagsPrefix) {
        this.tagsPrefix = tagsPrefix;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The release date a version comes out of Jira with is kept, and the one its tag reports only
     * completes it when Jira left it blank. The date of a tag is the date of the commit it points at,
     * which is not always the commit the release was built from: the eleven releases ZooKeeper cut
     * before leaving Subversion, 3.0.0 to 3.3.2, are tagged on the commits that moved them when the
     * project became a top-level one, all dated 2010-11-24T21:19:37. Read from the tags, two years of
     * releases would come out published in the same second, and every defect reported in those two
     * years would be opened against 3.0.0. Jira dates each of them on the day it was published.
     * <p>
     * The tag is still what a version Jira left undated falls back on, rather than leaving it undated:
     * an undated version sorts after every dated one, and ZooKeeper's 3.6.0, released and tagged, is
     * the one version Jira reports no date for.
     */
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
                    LocalDateTime releaseDate = releaseDateOf(winner);
                    version.setReleaseDate(releaseDate);
                    log.warn("Version {} has no release date in Jira: dated {} after its tag",
                            version.getName(), releaseDate);
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
     * Reads out of a tag the moment the version it marks was released, as the commit the tag points at
     * reports it. Only read for a version Jira reports no release date for.
     * <p>
     * The instant is normalised to UTC, and not merely stripped of its zone: the committers of a
     * project the size of this one are spread across the world, and ZooKeeper's release tags carry
     * offsets from {@code -07:00} to {@code +05:30}. Comparing the local times of two tags written in
     * different zones would order the releases by the wall clock of whoever cut them rather than by
     * the moment they happened, which is the one thing this date is read for.
     *
     * @param tag the tag marking the release
     * @return the moment the release was cut, in UTC
     */
    private static LocalDateTime releaseDateOf(Tag tag) {
        return tag.date().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
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
