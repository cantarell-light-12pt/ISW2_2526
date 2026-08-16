package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.model.ClassHistory;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The history of every class of a project as it is being read, kept apart from
 * {@link ReleaseHistoryAnalyzer} so that the analyzer is left with nothing but JGit: nothing here
 * knows what a commit or a repository is, and nothing in the analyzer knows how a running count is
 * carried from one release to the next.
 * <p>
 * A class is identified by the path of its source file, which is the only name a walk of the history
 * can give it. A project does move its sources, though, and a moved class is the same class: the
 * paths a class has been known under are therefore <em>aliases</em> of one another, all of them
 * naming the one history below. Aliasing rather than renaming is what makes the metrics of a project
 * maintaining several lines at once come out right — a maintenance release published after the move
 * still holds the sources where they used to be, and looking its classes up under the new path alone
 * would leave every one of them with no history at all.
 */
@Slf4j
class ClassHistories {

    /**
     * The history of each class, under every path it has ever been known by. Several keys map to the
     * very same object, which is what makes a moved class keep the counts it had before the move.
     */
    private final Map<String, Accumulator> byPath = new HashMap<>();

    /**
     * The histories themselves, each of them once. {@link Accumulator} inherits the identity equality
     * of {@link Object}, so a history aliased by ten paths is one element here, which is what lets a
     * release be opened, closed and frozen exactly once per class rather than once per alias.
     */
    private final Set<Accumulator> histories = new LinkedHashSet<>();

    /**
     * Returns the history of the class a source file holds, starting one if the file is being seen for
     * the first time.
     *
     * @param path the path of the source file, relative to the root of the repository
     * @return the history to fold the commits touching it into
     */
    Accumulator forPath(String path) {
        Accumulator known = byPath.get(path);
        if (known != null) {
            return known;
        }
        Accumulator started = new Accumulator();
        bind(path, started);
        histories.add(started);
        return started;
    }

    /**
     * Records that a class moved, i.e. that two paths name the same history. Whichever of the two the
     * class is looked up under from now on — the walk reads the history backwards, and the releases of
     * an older line still hold the sources where they were — the same counts are found.
     *
     * @param previous the path the class was at before the move
     * @param current  the path it is at after it
     */
    void alias(String previous, String current) {
        Accumulator before = byPath.get(previous);
        Accumulator after = byPath.get(current);
        if (before == null && after == null) {
            Accumulator started = new Accumulator();
            bind(previous, started);
            bind(current, started);
            histories.add(started);
        } else if (before == null) {
            bind(previous, after);
        } else if (after == null) {
            bind(current, before);
        } else if (before != after) {
            absorb(before, after);
        }
    }

    /**
     * @return how many classes have been read so far, counting a class that moved once rather than
     * once per path it has been known by
     */
    int size() {
        return histories.size();
    }

    /**
     * Starts a new release, whose per-release counts begin at zero for every class.
     */
    void openRelease() {
        histories.forEach(Accumulator::openRelease);
    }

    /**
     * Closes the release, promoting the churn of every class into the highest one it has ever taken.
     */
    void closeRelease() {
        histories.forEach(Accumulator::closeRelease);
    }

    /**
     * Freezes what is known about every class so far into the history of the release just closed.
     *
     * @param index the ordinal index of that release
     * @return the history of each class as of it, under every path the class is known by
     */
    Map<String, ClassHistory> freeze(int index) {
        Map<String, ClassHistory> released = new HashMap<>();
        for (Accumulator history : histories) {
            ClassHistory frozen = history.freeze(index);
            history.paths.forEach(path -> released.put(path, frozen));
        }
        return released;
    }

    /**
     * Names a history after one more path.
     *
     * @param path    the path to look the history up under
     * @param history the history it names
     */
    private void bind(String path, Accumulator history) {
        byPath.put(path, history);
        history.paths.add(path);
    }

    /**
     * Folds a history into another one, the two having turned out to be the same class reached by two
     * different routes. It takes a move the walk reads after having already started a history at the
     * path moved to — a class deleted on one line and moved on another, say — which no project mined so
     * far has done, but which would otherwise leave one of the two histories orphaned.
     *
     * @param keeper   the history the other one is folded into
     * @param absorbed the history to fold in, discarded afterwards
     */
    private void absorb(Accumulator keeper, Accumulator absorbed) {
        log.debug("The histories of {} and {} are the history of one class: folding them together",
                keeper.paths, absorbed.paths);
        keeper.absorb(absorbed);
        Set<String> moved = Set.copyOf(absorbed.paths);
        moved.forEach(path -> bind(path, keeper));
        histories.remove(absorbed);
    }

    /**
     * The running counts the immutable {@link ClassHistory} of each release is frozen out of, i.e. the
     * history of one class as it is being read.
     */
    static final class Accumulator {

        /**
         * Every path this history is known by, which {@link #freeze(int)} reports it under.
         */
        private final Set<String> paths = new LinkedHashSet<>();

        private final Set<String> authors = new HashSet<>();

        private int firstRelease;
        private int revisions;
        private long maxChurn;
        private long sizeChange;
        private int totalBugFixes;

        private long releaseChurn;
        private int releaseBugFixes;

        /**
         * Starts a new release, whose per-release counts begin at zero: a class no commit of the
         * release touched took no churn and no bug fix in it.
         */
        private void openRelease() {
            releaseChurn = 0;
            releaseBugFixes = 0;
        }

        /**
         * Closes the release, promoting its churn into the highest one the class has ever taken.
         */
        private void closeRelease() {
            maxChurn = Math.max(maxChurn, releaseChurn);
        }

        /**
         * Folds a commit touching this class into its history.
         *
         * @param author  the identity of whoever wrote the commit
         * @param added   the lines it added to the class
         * @param deleted the lines it removed from it
         * @param bugFix  whether it fixed a bug
         */
        void recordCommit(String author, long added, long deleted, boolean bugFix) {
            revisions++;
            authors.add(author);
            releaseChurn += added + deleted;
            sizeChange += added - deleted;
            if (bugFix) {
                releaseBugFixes++;
                totalBugFixes++;
            }
        }

        /**
         * Folds another history of the same class into this one. The class is as old as the older of
         * the two, and a release neither of them has been frozen at yet has no age at all.
         *
         * @param other the history to fold in
         */
        private void absorb(Accumulator other) {
            revisions += other.revisions;
            authors.addAll(other.authors);
            maxChurn = Math.max(maxChurn, other.maxChurn);
            sizeChange += other.sizeChange;
            totalBugFixes += other.totalBugFixes;
            releaseChurn += other.releaseChurn;
            releaseBugFixes += other.releaseBugFixes;
            if (firstRelease == 0 || (other.firstRelease != 0 && other.firstRelease < firstRelease)) {
                firstRelease = other.firstRelease;
            }
        }

        /**
         * @param index the ordinal index of the release being closed
         * @return the history of the class as of that release
         */
        private ClassHistory freeze(int index) {
            if (firstRelease == 0) {
                firstRelease = index;
            }
            return new ClassHistory(revisions, authors.size(), releaseChurn, maxChurn, sizeChange,
                    index - firstRelease + 1, releaseBugFixes, totalBugFixes);
        }
    }
}
