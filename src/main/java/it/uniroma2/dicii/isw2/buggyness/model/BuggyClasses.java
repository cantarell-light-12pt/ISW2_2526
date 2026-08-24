package it.uniroma2.dicii.isw2.buggyness.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which classes held a defect at which release, i.e. the answer the buggy/not-buggy column of the
 * dataset is read out of.
 * <p>
 * Releases are keyed by their ordinal index rather than by the {@link
 * it.uniroma2.dicii.isw2.versions.model.Version} itself, which is a mutable Lombok {@code @Data} and
 * therefore changes its hash under whoever holds it as a key. Classes are keyed by their fully
 * qualified name, which is what the dataset names a row by and the only identity that survives a
 * project moving its sources around: the same class is a different path in the releases built with
 * Ant and in the ones built with Maven, but the same name in both.
 */
public class BuggyClasses {

    private final Map<Integer, Set<String>> byRelease = new HashMap<>();

    /**
     * Records that a set of classes held a defect at a release. Recording the same class twice, as
     * happens whenever two defects were fixed in the same class, leaves the release with one entry.
     *
     * @param releaseIndex   the ordinal index of the release the classes were buggy at
     * @param qualifiedNames the fully qualified names of those classes
     */
    public void recordDefect(int releaseIndex, Collection<String> qualifiedNames) {
        byRelease.computeIfAbsent(releaseIndex, index -> new HashSet<>()).addAll(qualifiedNames);
    }

    /**
     * @param releaseIndex  the ordinal index of the release being labelled
     * @param qualifiedName the fully qualified name of one of its classes
     * @return whether that class held at least one defect at that release
     */
    public boolean holds(int releaseIndex, String qualifiedName) {
        return byRelease.getOrDefault(releaseIndex, Set.of()).contains(qualifiedName);
    }

    /**
     * @param releaseIndex the ordinal index of a release
     * @return how many of its classes held a defect
     */
    public int size(int releaseIndex) {
        return byRelease.getOrDefault(releaseIndex, Set.of()).size();
    }

    /**
     * @return how many releases hold at least one buggy class
     */
    public int releases() {
        return byRelease.size();
    }
}
