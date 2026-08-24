package it.uniroma2.dicii.isw2.versions.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Data
@RequiredArgsConstructor
public class Version implements Comparable<Version> {

    /**
     * Orders the releases of the project from the oldest to the newest, by the moment they were
     * released rather than by the number they were given. The name breaks a tie, and a version whose
     * release date is unknown sorts last: a list where no date is known at all — as is the case when
     * the versions have just been read from Jira, before the tags say when they were really cut —
     * therefore degrades to the name ordering {@link #compareTo(Version)} defines.
     */
    private static final Comparator<Version> BY_RELEASE_DATE =
            Comparator.comparing(Version::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Comparator.naturalOrder());

    private final String id;

    private final String name;

    /**
     * The moment this version was released, as the commit its Git tag points at reports it, normalised
     * to UTC. The whole timestamp is kept, and not just the day: releases of two lines maintained in
     * parallel are published hours apart often enough — ZooKeeper cut 3.4.0 fifteen hours before
     * 3.3.4, and 3.9.2 eighty minutes before 3.8.4 — that a day would order them by name instead.
     */
    private LocalDateTime releaseDate;

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
     * index, comparing them by the date they were released and falling back on their name, as
     * {@link #compareTo(Version)} orders it, whenever two of them were released at the very same
     * moment.
     * <p>
     * Ordering by date rather than by name is what makes the index mean "this release came before that
     * one". A project maintaining several lines at once does not release them in the order its version
     * numbers suggest: ZooKeeper published 3.5.0 five years before 3.4.14, and 3.6.0 two years before
     * 3.5.10, so a name ordering claims a release contains work that had not been written yet. Every
     * step downstream reasons in these indices — the Proportion estimating where a defect was injected,
     * the range of releases a defect affects, the release a commit is attributed to — and reads that
     * claim as fact.
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
        versions.sort(BY_RELEASE_DATE);
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
     * <p>
     * This is the order the numbers of the releases define, not the order they were released in:
     * {@link #numberVersions(List)} orders by release date and only falls back on this comparison to
     * break a tie.
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
