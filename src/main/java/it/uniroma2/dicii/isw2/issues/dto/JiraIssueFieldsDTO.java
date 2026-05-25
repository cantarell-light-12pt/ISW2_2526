package it.uniroma2.dicii.isw2.issues.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.uniroma2.dicii.isw2.versions.dto.JiraVersionDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueFieldsDTO {

    private List<JiraVersionDTO> versions;
    private List<JiraVersionDTO> fixVersions;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private LocalDateTime created;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private LocalDateTime resolutionDate;
    @JsonProperty("status")
    private JiraIssueStatusDTO status;
    @JsonProperty("issuetype")
    private JiraIssueTypeDTO issueType;
    @JsonProperty("resolution")
    private JiraIssueResolutionTypeDTO resolution;
    @JsonProperty("assignee")
    private JiraIssueAssigneeDTO assignee;
    private String summary;

}
