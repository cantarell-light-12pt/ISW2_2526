package it.uniroma2.dicii.isw2.issues.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDTO {

    private String id;
    private String key;
    @JsonProperty("fields")
    private JiraIssueFieldsDTO fields;

}
