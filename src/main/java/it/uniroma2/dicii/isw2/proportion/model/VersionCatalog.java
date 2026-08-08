package it.uniroma2.dicii.isw2.proportion.model;

import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An indexed, read-only view over the released versions of the project.
 * <p>
 * The Proportion approach reasons about versions as ordinal numbers, while the rest of the application
 * handles them by name or by release date. This catalog bridges the two: it holds the versions ordered
 * from the oldest to the newest and provides the lookups needed to build the life cycle of a defect.
 */
public class VersionCatalog {

    private final List<Version> ordered;
    private final Map<String, Version> byName;
    private final Map<Integer, Version> byIndex;

    /**
     * Builds a catalog over the given versions. The versions are not modified: they are only read and
     * indexed, so the caller keeps the ownership of the list.
     *
     * @param versions the released versions of the project, already numbered through
     *                 {@link Version#numberVersions(List)}
     * @throws ProportionException if the list is null or empty, or if any version has not been numbered
     */
    public VersionCatalog(List<Version> versions) throws ProportionException {
        if (versions == null || versions.isEmpty()) {
            throw new ProportionException("Cannot build a version catalog out of a null or empty list of versions");
        }
        for (Version version : versions) {
            if (version.getIndex() <= 0) {
                throw new ProportionException("Version " + version.getName() + " has not been numbered. "
                        + "Call Version.numberVersions before applying the Proportion method.");
            }
        }
        // Sorting on the assigned indices rather than on the names avoids parsing the names a second time
        this.ordered = versions.stream().sorted(Comparator.comparingInt(Version::getIndex)).toList();
        this.byName = new HashMap<>();
        this.byIndex = new HashMap<>();
        for (Version version : ordered) {
            byName.putIfAbsent(version.getName(), version);
            byIndex.putIfAbsent(version.getIndex(), version);
        }
    }

    /**
     * @return the index of the newest version of the project
     */
    public int lastIndex() {
        return ordered.getLast().getIndex();
    }

    /**
     * @param index the 1-based ordinal index of the wanted version
     * @return the version having the given index, or {@code null} if no version has it
     */
    public Version byIndex(int index) {
        return byIndex.get(index);
    }

    /**
     * Resolves a version against this catalog by name. Versions embedded in a Jira issue are distinct
     * objects carrying neither the release date nor the ordinal index, so they must be resolved against
     * the catalog before they can be used.
     *
     * @param name the name of the wanted version
     * @return the catalogued version having the given name, or {@code null} if the project has no such
     * released version
     */
    public Version byName(String name) {
        return byName.get(name);
    }

    /**
     * Returns the opening version (OV) of a defect reported at the given instant, i.e. the oldest
     * version released on or after the creation of the defect report — the release the project was
     * working towards when the failure was observed.
     *
     * @param creationDate the instant the defect report was created
     * @return the opening version, or {@code null} if the report was created after the newest release,
     * in which case the defect cannot be placed in the release history
     */
    public Version openingVersionFor(LocalDateTime creationDate) {
        if (creationDate == null) {
            return null;
        }
        LocalDate creationDay = creationDate.toLocalDate();
        for (Version version : ordered) {
            if (version.getReleaseDate() != null && !version.getReleaseDate().isBefore(creationDay)) {
                return version;
            }
        }
        return null;
    }

    /**
     * Returns the versions whose index falls in the given half-open range, used to label the versions
     * affected by a defect as all those from the injected version (included) to the fixed version
     * (excluded).
     *
     * @param fromInclusive the index of the first version to return
     * @param toExclusive   the index just past the last version to return
     * @return the matching versions ordered from the oldest to the newest, possibly empty
     */
    public List<Version> range(int fromInclusive, int toExclusive) {
        return ordered.stream()
                .filter(version -> version.getIndex() >= fromInclusive && version.getIndex() < toExclusive)
                .toList();
    }
}
