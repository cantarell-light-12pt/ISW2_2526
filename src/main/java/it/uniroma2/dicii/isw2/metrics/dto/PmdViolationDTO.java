package it.uniroma2.dicii.isw2.metrics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One rule broken somewhere in a source file. The rule is kept for the logs alone: the dataset counts
 * the violation under the severity its priority stands for, whichever rule produced it.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmdViolationDTO {

    @JsonProperty("rule")
    private String rule;

    @JsonProperty("priority")
    private int priority;

}
