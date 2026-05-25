package it.uniroma2.dicii.isw2.issues.impl;

import it.uniroma2.dicii.isw2.issues.IssuesRetriever;
import it.uniroma2.dicii.isw2.issues.dto.*;
import it.uniroma2.dicii.isw2.issues.exception.IssueException;
import it.uniroma2.dicii.isw2.issues.filter.FilterToJqlConverter;
import it.uniroma2.dicii.isw2.issues.filter.IssueFilter;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.utils.HttpFetcher;
import it.uniroma2.dicii.isw2.versions.dto.JiraVersionDTO;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JiraIssuesRetriever implements IssuesRetriever {

    private final String jiraUrl;
    private final ObjectMapper objectMapper;

    public JiraIssuesRetriever(String jiraUrl) {
        this.jiraUrl = jiraUrl;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Override
    public List<Issue> retrieveIssues(IssueFilter filter) throws IssueException {
        if (filter == null) throw new IssueException("Filter cannot be null");
        log.info("Retrieving issues from Jira...");
        List<Issue> retrievedIssue = new ArrayList<>();
        JiraIssueResponseDTO dto;
        String url;
        Long fetched = 0L;
        Long total = null;
        Long maxResults = filter.getMaxResults();
        do {
            url = jiraUrl + new FilterToJqlConverter().convert(filter);
            log.debug("Jira URL: {}", url);
            dto = retrievePage(url);
            if (total == null) total = dto.getTotal();
            fetched += maxResults;
            retrievedIssue.addAll(convertDTOsToIssues(dto.getIssues()));
            filter.setStartAt(fetched);
            log.info("Fetched {} out of {} issues from Jira", fetched, total);
        } while (fetched < total);
        log.info("Successfully retrieved {} issues from Jira", retrievedIssue.size());
        return retrievedIssue;
    }

    private JiraIssueResponseDTO retrievePage(String urlWithJqlParams) throws IssueException {
        try {
            String response = new HttpFetcher().fetchUrl(urlWithJqlParams);
            log.debug("Response: {}", response);
            return objectMapper.readValue(response, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IssueException("Error retrieving or parsing issues from " + jiraUrl, e);
        }
    }

    /**
     * Converts a list of {@code JiraIssueDTO} objects into a list of {@code Issue} objects.
     * Each {@code JiraIssueDTO} contains data about a specific issue, including its ID, key,
     * creation date, resolution date, status, type, resolution, assignee, summary, and associated versions.
     * This method processes the relevant fields of each DTO to construct corresponding {@code Issue} instances.
     *
     * @param dtos a list of {@code JiraIssueDTO} objects to be converted into {@code Issue} objects.
     *             Each DTO must contain all necessary data to build a valid {@code Issue}.
     * @return a list of {@code Issue} objects created from the input {@code JiraIssueDTO} objects.
     */
    private List<Issue> convertDTOsToIssues(List<JiraIssueDTO> dtos) {
        log.debug("Converting {} DTOs to Issue objects...", dtos.size());
        List<Issue> issues = new ArrayList<>();
        Issue issue;
        for (JiraIssueDTO dto : dtos) {
            String id = dto.getId();
            String key = dto.getKey();
            JiraIssueFieldsDTO fields = dto.getFields();
            LocalDateTime creationDate = fields.getCreated();
            LocalDateTime resolutionDate = fields.getResolutionDate();
            List<Version> affectedVersions = convertVersionsDTOsToVersions(fields.getVersions());
            List<Version> fixVersions = convertVersionsDTOsToVersions(fields.getFixVersions());
            IssueStatus status = convertStatusDTOToEnum(fields.getStatus());
            IssueType type = convertTypeDTOToEnum(fields.getIssueType());
            ResolutionType resolution = convertResolutionDTOToEnum(fields.getResolution());
            String assignee = fields.getAssignee() != null ? fields.getAssignee().getName() : "";
            String summary = fields.getSummary();
            issue = new Issue(key, creationDate, resolutionDate, id, type, assignee, resolution, summary, status);
            issue.setAffectedVersions(affectedVersions);
            issue.setFixed(fixVersions);
            issues.add(issue);
        }
        log.debug("Successfully retrieved {} issues from Jira", issues.size());
        return issues;
    }

    /**
     * Converts a list of {@code JiraVersionDTO} objects to a list of {@code Version} objects.
     * Each {@code JiraVersionDTO} provides relevant data such as id, name, release status, and overdue status,
     * which are used to create corresponding {@code Version} instances. The {@code releaseDate} in the resulting
     * {@code Version} objects is set to {@code null} since {@code JiraVersionDTO} does not contain this information
     * when retrieving issues.
     *
     * @param dtos a list of {@code JiraVersionDTO} objects to be converted
     * @return a list of {@code Version} objects created from the input {@code JiraVersionDTO} objects
     */
    private List<Version> convertVersionsDTOsToVersions(List<JiraVersionDTO> dtos) {
        List<Version> versions = new ArrayList<>();
        for (JiraVersionDTO dto : dtos)
            // Note: JiraVersionDTO does not contain release date when retrieving issues
            versions.add(new Version(dto.getId(), dto.getName(), null, dto.isReleased(), dto.isOverdue()));
        return versions;
    }

    /**
     * Converts a {@code JiraIssueStatusDTO} object to its corresponding {@code IssueStatus} enum value.
     * The conversion is based on the {@code name} field of the provided DTO, which is matched
     * (case-insensitively) against the predefined status values of the {@code IssueStatus} enum.
     *
     * @param dto the {@code JiraIssueStatusDTO} object containing the status name to be converted.
     *            It must have a non-null and non-empty {@code name} field for a successful conversion.
     * @return the corresponding {@code IssueStatus} enum value if the name matches a predefined value,
     * otherwise {@code IssueStatus.OTHER}.
     * @throws IllegalArgumentException if the {@code name} field of the given DTO is null or empty.
     */
    private IssueStatus convertStatusDTOToEnum(JiraIssueStatusDTO dto) {
        if (dto != null) {
            String name = dto.getName();
            if (name != null && !name.isEmpty()) return IssueStatus.from(name);
        }
        return IssueStatus.OTHER;
    }

    /**
     * Converts a {@code JiraIssueTypeDTO} object to its corresponding {@code IssueType} enum value.
     * The conversion is based on the {@code name} field of the provided DTO, which is matched
     * (case-insensitively) against the predefined type values of the {@code IssueType} enum.
     *
     * @param dto the {@code JiraIssueTypeDTO} object containing the type name to be converted.
     *            The {@code name} field must not be null or empty for a successful conversion.
     * @return the corresponding {@code IssueType} enum value if the name matches a predefined value;
     * otherwise, {@code IssueType.OTHER}.
     * @throws IllegalArgumentException if the {@code name} field of the given DTO is null or empty.
     */
    private IssueType convertTypeDTOToEnum(JiraIssueTypeDTO dto) {
        if (dto != null) {
            String name = dto.getName();
            if (name != null && !name.isEmpty()) return IssueType.from(name);
        }
        return IssueType.OTHER;
    }

    /**
     * Converts a {@code JiraIssueResolutionTypeDTO} object to its corresponding {@code ResolutionType} enum value.
     * The conversion is based on the {@code name} field of the provided DTO, which is matched (case-insensitively)
     * against the predefined resolution values of the {@code ResolutionType} enum.
     *
     * @param dto the {@code JiraIssueResolutionTypeDTO} object containing the resolution name to be converted.
     *            The {@code name} field must not be null or empty for a successful conversion.
     * @return the corresponding {@code ResolutionType} enum value if the name matches a predefined value.
     * Returns {@code null} if the name does not match any predefined value or is null.
     */
    private ResolutionType convertResolutionDTOToEnum(JiraIssueResolutionTypeDTO dto) {
        if (dto != null) {
            String name = dto.getName();
            if (name != null && !name.isEmpty()) return ResolutionType.from(name);
        }
        return ResolutionType.OTHER;
    }

}
