package it.uniroma2.dicii.isw2.issues.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueResponseDTO {

    private Long startAt;
    private Long maxResults;
    private Long total;
    private List<JiraIssueDTO> issues;

}
