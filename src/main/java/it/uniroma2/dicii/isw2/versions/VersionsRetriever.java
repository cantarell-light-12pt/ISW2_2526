package it.uniroma2.dicii.isw2.versions;

import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.util.List;

public interface VersionsRetriever {

    /**
     * Retrieves a list of versions available in the system.
     *
     * @return a list of Version objects, where each object contains details such as
     *         version id, name, release date, and status (released or overdue).
     */
    List<Version> retrieveVersions() throws VersionsException;

}
