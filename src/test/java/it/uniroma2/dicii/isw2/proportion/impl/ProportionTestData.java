package it.uniroma2.dicii.isw2.proportion.impl;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the releases and the defect reports the Proportion tests run against, reproducing how the rest
 * of the application hands them over: one release per month, and defects whose fix and affected versions
 * are the bare name/id pairs Jira embeds in an issue, i.e. carrying neither release date nor index.
 */
final class ProportionTestData {

    static final LocalDate FIRST_RELEASE = LocalDate.of(2020, 1, 1);

    private ProportionTestData() {
        // Test data holder
    }

    /**
     * @param count how many releases to build
     * @return {@code count} released, numbered versions named "1.0" to "{count}.0"
     */
    static List<Version> versions(int count) {
        List<Version> versions = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Version version = new Version("id" + i, i + ".0", true, false);
            version.setReleaseDate(FIRST_RELEASE.plusMonths(i - 1L));
            versions.add(version);
        }
        Version.numberVersions(versions);
        return versions;
    }

    /**
     * Builds a defect report opened while the project was working towards version {@code ov} and fixed
     * in version {@code fv}.
     *
     * @param key      the key of the defect
     * @param versions the releases of the project
     * @param ov       the index of the wanted opening version
     * @param fv       the index of the wanted fix version
     * @param iv       the index of the wanted oldest affected version, or {@code null} for a report
     *                 listing no affected version at all
     * @return the defect report
     */
    static Issue defect(String key, List<Version> versions, int ov, int fv, Integer iv) {
        Issue issue = new Issue(key, creationDateFor(versions, ov), releaseDateTimeOf(versions, fv), key,
                IssueType.BUG, "assignee", ResolutionType.FIXED, key, IssueStatus.CLOSED);
        issue.setFixed(List.of(asReportedByJira(versions.get(fv - 1))));
        issue.setAffectedVersions(iv == null ? List.of() : List.of(asReportedByJira(versions.get(iv - 1))));
        return issue;
    }

    /**
     * @param version a release of the project
     * @return the same version as Jira embeds it in an issue, i.e. without release date nor index, so
     * that the tests exercise the resolution of those versions against the catalogued ones
     */
    static Version asReportedByJira(Version version) {
        return new Version(version.getId(), version.getName(), version.isReleased(), version.isOverdue());
    }

    /**
     * @param versions the releases of the project
     * @param ov       the index of the wanted opening version
     * @return an instant falling between the release of version {@code ov - 1} and the release of
     * version {@code ov}, so that {@code ov} is the oldest release not older than it
     */
    private static LocalDateTime creationDateFor(List<Version> versions, int ov) {
        return ov == 1
                ? FIRST_RELEASE.minusDays(10).atStartOfDay()
                : versions.get(ov - 2).getReleaseDate().plusDays(1).atStartOfDay();
    }

    private static LocalDateTime releaseDateTimeOf(List<Version> versions, int index) {
        return versions.get(index - 1).getReleaseDate().atStartOfDay();
    }
}
