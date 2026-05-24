package it.uniroma2.dicii.isw2.versions.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraVersionDTO {
    private String id;
    private String name;
    private LocalDate releaseDate;
    private boolean released;
    private boolean overdue;
}
