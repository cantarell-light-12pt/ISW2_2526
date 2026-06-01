package it.uniroma2.dicii.isw2.repo;

import it.uniroma2.dicii.isw2.repo.exception.TagException;
import it.uniroma2.dicii.isw2.repo.model.Tag;

import java.nio.file.Path;
import java.util.List;

public interface TagsRetriever {

    /**
     * Retrieves a list of Git tags from the specified local repository path.
     *
     * @param repositoryPath the file system path to the local Git repository
     * @return a list of tags present in the repository
     * @throws TagException if an error occurs while retrieving the tags
     */
    List<Tag> getTags(Path repositoryPath) throws TagException;
}