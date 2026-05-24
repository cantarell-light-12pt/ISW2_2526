package it.uniroma2.dicii.isw2.versions.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class Version implements Comparable<Version> {

    private final String id;

    private final String name;

    private final LocalDate releaseDate;

    private final boolean released;

    private final boolean overdue;

    private String commitId;

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
