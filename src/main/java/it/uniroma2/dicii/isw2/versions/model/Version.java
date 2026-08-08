package it.uniroma2.dicii.isw2.versions.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Version implements Comparable<Version> {

    private final String id;

    private final String name;

    private LocalDate releaseDate;

    private final boolean released;

    private final boolean overdue;

    private String commitId;

    /**
     * The 1-based ordinal position of this version among the versions of the project, where 1 is the
     * oldest one. Jira only provides the version name and id, so this number is assigned by
     * {@link #numberVersions(List)}. A value of {@code 0} means the version has not been numbered yet.
     */
    private int index;

    /**
     * Sorts the given versions from the oldest to the newest and assigns each of them a 1-based ordinal
     * index, comparing them by name through {@link #compareTo(Version)} (e.g. "1.2.0" gets index 1 and
     * "1.10.0" gets index 2).
     * <p>
     * Both the sorting and the numbering are performed in place. The assigned indices are contiguous:
     * the oldest version gets index 1 and the newest gets index {@code versions.size()}. Whenever
     * versions are added to or removed from the list, this method must be invoked again, otherwise the
     * indices stop forming a contiguous range and any arithmetic based on them — such as the Proportion
     * method used to estimate injected versions — silently produces wrong results.
     *
     * @param versions the list of versions to sort and number, modified in place
     * @throws NumberFormatException if a version name contains a non-numeric segment
     */
    public static void numberVersions(List<Version> versions) {
        versions.sort(Version::compareTo);
        for (int i = 0; i < versions.size(); i++) {
            versions.get(i).setIndex(i + 1);
        }
    }

    /**
     * Compares two versions based on their semantic versioning.
     * E.g., 5.0.1 > 5.0.0 > 4.2.1 > 4.2.0 etc.
     * <p>
     * The version name is assumed to strictly follow a numerical format (e.g., MAJOR.MINOR.PATCH)
     * without any alphabetic characters or suffixes. Segments are compared numerically.
     * Missing segments are treated as zero (e.g., "1.2" is equivalent to "1.2.0").
     *
     * @param other the other version to compare with
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     * @throws NumberFormatException if a version segment is not a valid integer.
     */
    @Override
    public int compareTo(Version other) {
        String[] segments = this.name.split("\\.");
        String[] otherSegments = other.getName().split("\\.");
        int length = Math.max(segments.length, otherSegments.length);

        for (int i = 0; i < length; i++) {
            int v1 = i < segments.length ? Integer.parseInt(segments[i]) : 0;
            int v2 = i < otherSegments.length ? Integer.parseInt(otherSegments[i]) : 0;

            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }

}
