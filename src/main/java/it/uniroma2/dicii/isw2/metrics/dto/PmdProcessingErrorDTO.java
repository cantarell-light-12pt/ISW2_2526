package it.uniroma2.dicii.isw2.metrics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A source PMD could not analyse, which on a project whose releases span several versions of the
 * language is a normal enough event to be reported rather than raised.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmdProcessingErrorDTO {

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("message")
    private String message;

}
