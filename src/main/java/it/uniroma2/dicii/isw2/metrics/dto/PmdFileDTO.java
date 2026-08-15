package it.uniroma2.dicii.isw2.metrics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * The violations PMD found in one source file. Only the files it found something in are reported, so a
 * source missing from a report is a class no rule was broken by.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmdFileDTO {

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("violations")
    private List<PmdViolationDTO> violations;

}
