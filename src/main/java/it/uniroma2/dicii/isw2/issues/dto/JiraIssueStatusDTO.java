package it.uniroma2.dicii.isw2.issues.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueStatusDTO {

    private String id;
    private String name;

}
