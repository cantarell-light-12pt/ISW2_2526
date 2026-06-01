package it.uniroma2.dicii.isw2.association;

import it.uniroma2.dicii.isw2.repo.model.Tag;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.util.List;

public interface VersionTagAssociator {

    /**
     * Associates a list of Git tags with a list of versions and removes versions
     * that have no associated tags. Each version's {@code commitId} is matched against
     * the {@code commitId} of a tag. If no match is found, the version is removed.
     *
     * @param tags     the list of {@code Tag} objects to associate with the given versions.
     * @param versions the list of {@code Version} objects to be compared and processed;
     *                 versions without an associated tag will be removed.
     */
    void associateTagsToVersions(List<Tag> tags, List<Version> versions);

}
