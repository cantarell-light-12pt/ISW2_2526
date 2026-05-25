package it.uniroma2.dicii.isw2.issues.filter;

import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IssueFilter {

    private static final Long DEFAULT_MAX_RESULTS = 100L;
    private static final Long DEFAULT_START_AT = 0L;

    private List<IssueStatus> statuses;
    private List<IssueType> types;
    private List<ResolutionType> resolutions;
    private List<String> fields;
    private Long maxResults;
    private Long startAt;

    public IssueFilter() {
        this.maxResults = DEFAULT_MAX_RESULTS;
        this.startAt = DEFAULT_START_AT;
    }

}
