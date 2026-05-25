package it.uniroma2.dicii.isw2.issues.model;

import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Issue {

    @NonNull
    private final String key;
    @NonNull
    private final LocalDateTime creationDate;

    private final LocalDateTime resolutionDate;
    @NonNull
    private String id;
    @NonNull
    private IssueType type;

    @NonNull
    private String assignee;

    @NonNull
    private ResolutionType resolution;

    @NonNull
    private String summary;

    @NonNull
    private IssueStatus status;

    private Version injected;
    private Version opening;
    private List<Version> fixed;
    private List<Version> affectedVersions;

}
