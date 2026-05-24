package it.uniroma2.dicii.isw2.versions.impl;

import it.uniroma2.dicii.isw2.versions.VersionsRetriever;
import it.uniroma2.dicii.isw2.versions.dto.JiraVersionDTO;
import it.uniroma2.dicii.isw2.versions.exception.VersionsException;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
            String response = fetchUrl();
            List<JiraVersionDTO> dtos = objectMapper.readValue(response, new TypeReference<>() {
            });
            return convertDTOsToVersion(dtos);
        } catch (IOException e) {
            throw new VersionsException("Error retrieving or parsing versions from " + jiraUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VersionsException("Error retrieving versions from " + jiraUrl, e);
        }
    }

    /**
     * Sends an HTTP GET request to the Jira URL and retrieves the response body as a string.
     * <p>
     * This method uses the {@link HttpClient} to perform the request with a timeout of 10 seconds.
     * If the request is successful, the response body is returned. If an error occurs during the
     * request or the execution is interrupted, the corresponding exception is thrown.
     *
     * @return the response body as a string
     * @throws IOException          if an I/O error occurs when sending or receiving
     * @throws InterruptedException if the operation is interrupted
     */
    private String fetchUrl() throws IOException, InterruptedException {
        // In Java 21+, HttpClient is AutoCloseable
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(jiraUrl)).GET().build();
            log.debug("Sending GET request...");
            // BodyHandlers.ofString() automatically reads and closes the underlying stream
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        }
    }

    /**
     * Converts a list of JiraVersionDTO objects into a list of Version objects.
     * Only DTOs with non-null id and name fields are converted. If a DTO has a release date,
     * it is converted to a Version object. DTOs missing a release date or having null id/name
     * are logged and skipped. The resulting Version list is sorted by name in ascending order.
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
            if (dto.getReleaseDate() != null) {
                versions.add(new Version(dto.getId(), dto.getName(), dto.getReleaseDate(), dto.isReleased(), dto.isOverdue()));
            } else {
                log.warn("Version {} (id: {}) has no release date. Skipping...", dto.getName(), dto.getId());
            }
        }
        log.info("Successfully retrieved {} versions out of {} total versions from Jira", versions.size(), dtos.size());
        // Order releases by name using Version::compareTo
        versions.sort(Version::compareTo);
        return versions;
    }

}
