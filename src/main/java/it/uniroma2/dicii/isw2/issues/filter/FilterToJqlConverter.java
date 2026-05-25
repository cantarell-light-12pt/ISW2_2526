package it.uniroma2.dicii.isw2.issues.filter;

import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class FilterToJqlConverter {

    /**
     * Converts the given {@code IssueFilter} into a JQL-formatted query string.
     * The method processes the provided filter's statuses, types, resolutions,
     * startAt, and maxResults, appending them to the output string in the correct
     * JQL format.
     *
     * @param filter an {@code IssueFilter} object containing the filtering criteria
     *               such as statuses, types, resolutions, startAt, and maxResults.
     *               Null or empty lists for statuses, types, and resolutions are ignored.
     *               Null values for startAt and maxResults are also ignored.
     * @return a {@code String} containing the JQL query generated based on the provided filter.
     */
    public String convert(IssueFilter filter) {
        StringBuilder url = new StringBuilder();

        if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) appendStatuses(url, filter.getStatuses());

        if (filter.getTypes() != null && !filter.getTypes().isEmpty()) appendTypes(url, filter.getTypes());

        if (filter.getResolutions() != null && !filter.getResolutions().isEmpty())
            appendResolutions(url, filter.getResolutions());

        if (filter.getStartAt() != null) appendStartAt(url, filter.getStartAt());
        if (filter.getMaxResults() != null) appendMaxResults(url, filter.getMaxResults());

        String jqlQuery = url.toString();
        log.debug("JQL query: {}", jqlQuery);
        return jqlQuery;
    }

    /**
     * Appends the given issue statuses to the provided {@code StringBuilder} in the form of a JQL query condition.
     * The statuses are formatted as a logical "AND" group containing "OR" conditions for each status.
     *
     * @param url      the {@code StringBuilder} to which the formatted JQL condition for statuses is appended.
     * @param statuses a {@code List} of {@code IssueStatus} objects representing the issue statuses to be included
     *                 in the JQL query. If the list is empty, no statuses are appended.
     */
    private void appendStatuses(StringBuilder url, List<IssueStatus> statuses) {
        url.append("AND(");
        boolean first = true;
        for (IssueStatus status : statuses) {
            if (!first) url.append("OR");
            url.append("\"status\"=\"").append(status.getStatus().toUpperCase()).append("\"");
            first = false;
        }
        url.append(")");
    }

    /**
     * Appends the given issue types to the provided {@code StringBuilder} in the form of a JQL query condition.
     * The types are formatted as a logical "AND" group containing "OR" conditions for each issue type.
     *
     * @param url   the {@code StringBuilder} to which the formatted JQL condition for issue types is appended.
     * @param types a {@code List} of {@code IssueType} objects representing the issue types to be included
     *              in the JQL query. If the list is empty, no issue types are appended.
     */
    private void appendTypes(StringBuilder url, List<IssueType> types) {
        url.append("AND(");
        boolean first = true;
        for (IssueType type : types) {
            if (!first) url.append("OR");
            url.append("\"issueType\"=\"").append(type.getType().toUpperCase()).append("\"");
            first = false;
        }
        url.append(")");
    }

    /**
     * Appends the given issue resolutions to the provided {@code StringBuilder} in the form
     * of a JQL query condition. The resolutions are formatted as a logical "AND" group
     * containing "OR" conditions for each resolution.
     *
     * @param url         the {@code StringBuilder} to which the formatted JQL condition for resolutions
     *                    is appended.
     * @param resolutions a {@code List} of {@code ResolutionType} objects representing the
     *                    issue resolutions to be included in the JQL query. If the list
     *                    is empty, no resolutions are appended.
     */
    private void appendResolutions(StringBuilder url, List<ResolutionType> resolutions) {
        url.append("AND(");
        boolean first = true;
        for (ResolutionType type : resolutions) {
            if (!first) url.append("OR");
            url.append("\"resolution\"=\"").append(type.getResolution().toUpperCase()).append("\"");
            first = false;
        }
        url.append(")");
    }

    /**
     * Appends the startAt parameter to the provided {@code StringBuilder} in the form
     * of a JQL query condition. The startAt parameter represents the starting index
     * for the paginated results in the query.
     *
     * @param url     the {@code StringBuilder} to which the startAt parameter is appended.
     * @param startAt the starting index for the paginated results. If this value is non-null,
     *                it is appended to the JQL query in the format "&startAt={startAt}".
     */
    private void appendStartAt(StringBuilder url, Long startAt) {
        url.append(String.format("&startAt=%d", startAt));
    }

    /**
     * Appends the specified maximum number of results to the provided {@code StringBuilder}
     * in the format required for a JQL query parameter.
     *
     * @param url        the {@code StringBuilder} to which the maxResults parameter is appended.
     *                   The parameter is formatted as "&maxResults={maxResults}".
     * @param maxResults the maximum number of results to include in the query. If this value
     *                   is non-null, it will be appended to the JQL query.
     */
    private void appendMaxResults(StringBuilder url, Long maxResults) {
        url.append(String.format("&maxResults=%d", maxResults));
    }
}
