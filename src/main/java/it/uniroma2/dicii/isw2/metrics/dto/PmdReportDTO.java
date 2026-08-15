package it.uniroma2.dicii.isw2.metrics.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * The report PMD produces, as its JSON format writes it down.
 * <p>
 * Deliberately partial, like the shapes the Jira responses are mapped through: of everything a report
 * says about a violation the dataset only counts how severe it was, and of everything it says about a
 * run it only reports how many sources were lost to a parse error.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmdReportDTO {

    @JsonProperty("pmdVersion")
    private String pmdVersion;

    @JsonProperty("files")
    private List<PmdFileDTO> files;

    @JsonProperty("processingErrors")
    private List<PmdProcessingErrorDTO> processingErrors;

}
