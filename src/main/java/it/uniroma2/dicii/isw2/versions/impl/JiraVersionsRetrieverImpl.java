package it.uniroma2.dicii.isw2.versions.impl;

import it.uniroma2.dicii.isw2.utils.HttpFetcher;
import it.uniroma2.dicii.isw2.versions.VersionsRetriever;
import it.uniroma2.dicii.isw2.versions.dto.JiraVersionDTO;
import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JiraVersionsRetrieverImpl implements VersionsRetriever {

    private final String jiraUrl;
    private final ObjectMapper objectMapper;

    public JiraVersionsRetrieverImpl(String jiraUrl) {
        this.jiraUrl = jiraUrl;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    public List<Version> retrieveVersions() throws VersionsException {
        log.info("Retrieving versions from Jira...");
        log.debug("Jira URL: {}", jiraUrl);
        try {
            String response = new HttpFetcher().fetchUrl(jiraUrl);
            log.debug("Response: {}", response);
            List<JiraVersionDTO> dtos = objectMapper.readValue(response, new TypeReference<>() {
            });
            return convertDTOsToVersion(dtos);
        } catch (IOException e) {
            throw new VersionsException("Error retrieving or parsing versions from " + jiraUrl, e);
        }
    }

    /**
     * Converts a list of JiraVersionDTO objects into a list of Version objects.
     * Only DTOs with non-null id and name fields are converted. If a DTO has a release date,
     * it is converted to a Version object. DTOs missing a release date or having null id/name
     * are logged and skipped. The resulting Version list is sorted from the oldest to the newest and
     * each version is assigned its 1-based ordinal index, since Jira only provides the version name
     * and id.
     * <p>
     * Both the dates and the numbering are provisional. Jira records the day somebody marked a version
     * released, at the granularity of a day and with no guarantee it is the day the release was cut;
     * the association with the Git tags replaces every one of them with the moment of the commit the
     * release was built from, and numbers the versions that have a tag again.
     *
     * @param dtos the list of JiraVersionDTO objects to be converted
     * @return a list of Version objects derived from the input DTOs
     */
    private List<Version> convertDTOsToVersion(List<JiraVersionDTO> dtos) {
        log.debug("Converting {} DTOs to Version objects...", dtos.size());
        List<Version> versions = new ArrayList<>();
        for (JiraVersionDTO dto : dtos) {
            if (dto.getId() == null || dto.getName() == null) {
                log.warn("Version {} has no id or name. Skipping...", dto);
                continue;
            }
            Version version = new Version(dto.getId(), dto.getName(), dto.isReleased(), dto.isOverdue());
            if (dto.getReleaseDate() == null)
                log.warn("Version {} (id: {}) has no release date. It will be set later during commit association.", dto.getName(), dto.getId());
            else version.setReleaseDate(dto.getReleaseDate().atStartOfDay());
            versions.add(version);
        }
        log.info("Successfully retrieved {} versions out of {} total versions from Jira", versions.size(), dtos.size());
        // Order the releases from the oldest to the newest and number them from 1. Both the dates this
        // reads and the indices it assigns are replaced once the Git tags say when each release was cut
        Version.numberVersions(versions);
        return versions;
    }

}
