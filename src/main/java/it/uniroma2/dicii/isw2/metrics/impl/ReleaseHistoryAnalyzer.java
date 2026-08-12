package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.ClassHistory;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The reading of the release history of a project the evolution metrics are measured on, kept apart
 * from {@link JGitHistoryExtractor} so that the leaf is left with nothing but the metrics: everything
 * about JGit lives here, as everything about CK lives in the notifier of {@code CKExtractor} and
 * everything about cognitive complexity in {@code CognitiveComplexityCalculator}.
 * <p>
 * The whole history is read in a single pass, at the end of which every release is described. Reading
 * it release by release, on demand, would be enough for the workflow — it measures the versions from
 * the oldest to the newest — but it would make the result of a measurement depend on the measurements
 * taken before it, which is a property a leaf of the composite has no business having.
 */
@Slf4j
class ReleaseHistoryAnalyzer {

    private static final String JAVA_EXTENSION = ".java";

    private final SourceFilter filter;

    private final Set<String> bugFixCommits;

    /**
     * @param filter        the rule telling which sources are rows of the dataset, the very same one
     *                      the other extractors of the composite are given
     * @param bugFixCommits the identifiers of the commits that fixed a bug, as the association between
     *                      the Jira issues and the Git commits reports them
     */
    ReleaseHistoryAnalyzer(SourceFilter filter, Set<String> bugFixCommits) {
        this.filter = filter;
        this.bugFixCommits = bugFixCommits;
    }

    /**
     * Reads what the history of the repository says about each class of each released version.
     *
     * @param repoPath the root directory of the repository to read
     * @param versions the released versions of the project, already associated with their Git tags
     * @return the history of every class known at each release, keyed by the ordinal index of the
     * release and then by the path of the source file, relative to the root of the repository
     * @throws MetricsException if the repository cannot be read
     */
    Map<Integer, Map<String, ClassHistory>> analyse(Path repoPath, List<Version> versions) throws MetricsException {
        List<Version> ordered = versions.stream()
                .sorted(Comparator.comparingInt(Version::getIndex))
                .toList();
        Map<Integer, Map<String, ClassHistory>> history = new LinkedHashMap<>();
        Map<String, Accumulator> accumulators = new HashMap<>();
        // The releases already walked, whose commits must not be counted again by the following ones
        List<ObjectId> walked = new ArrayList<>();

        log.info("Reading the history of the {} released versions under {}...", ordered.size(), repoPath);
        try (Git git = Git.open(repoPath.toFile());
             DiffFormatter formatter = newFormatter(git.getRepository())) {
            Repository repository = git.getRepository();
            for (Version version : ordered) {
                walkRelease(repository, formatter, version, walked, accumulators);
                history.put(version.getIndex(), snapshot(accumulators, version.getIndex()));
            }
        } catch (IOException e) {
            throw new MetricsException("Unable to read the history of the repository at '" + repoPath + "'", e);
        }
        log.info("Read the history of {} classes over {} releases", accumulators.size(), history.size());
        return history;
    }

    /**
     * Builds the formatter the changes of a commit are read through. Renames are not detected: a class
     * is identified by the path of its source file, so a class that moved starts a history of its own,
     * as it does everywhere else in the dataset. Only the Java sources are scanned, which keeps the
     * walk off the far larger part of the tree no row of the dataset is about.
     *
     * @param repository the repository whose commits are read
     * @return the formatter to read them through, to be closed by the caller
     */
    private static DiffFormatter newFormatter(Repository repository) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repository);
        formatter.setDiffComparator(RawTextComparator.DEFAULT);
        formatter.setDetectRenames(false);
        formatter.setPathFilter(PathSuffixFilter.create(JAVA_EXTENSION));
        return formatter;
    }

    /**
     * Folds into the accumulators every commit a release brought in, i.e. the commits it can be
     * reached from that none of the releases before it could.
     * <p>
     * Every earlier release is marked uninteresting, rather than the immediately preceding one alone:
     * a project maintaining several lines at once publishes a fix of an old line long after a newer
     * one came out, and were only the previous release excluded, the commits of that old line would be
     * counted twice. Each commit is therefore attributed to the earliest release, in version order,
     * that holds it.
     *
     * @param repository   the repository being read
     * @param formatter    the formatter the changes are read through
     * @param version      the release to walk
     * @param walked       the commits of the releases already walked, which this call appends to
     * @param accumulators the history read so far, updated in place
     */
    private void walkRelease(Repository repository, DiffFormatter formatter, Version version,
                             List<ObjectId> walked, Map<String, Accumulator> accumulators) {
        accumulators.values().forEach(Accumulator::openRelease);
        ObjectId released = releaseCommit(repository, version);
        if (released != null) {
            walkCommits(repository, formatter, version, released, walked, accumulators);
            walked.add(released);
        }
        accumulators.values().forEach(Accumulator::closeRelease);
    }

    /**
     * Reads the commit a released version is tagged on. A version the clone holds no commit for is
     * reported and left with the history of the release before it, rather than aborting the reading of
     * the whole project.
     *
     * @param repository the repository being read
     * @param version    the release whose commit is wanted
     * @return that commit, or {@code null} if the repository does not hold it
     */
    private static ObjectId releaseCommit(Repository repository, Version version) {
        try {
            ObjectId released = repository.resolve(version.getCommitId());
            if (released == null) {
                log.warn("Version {} is tagged on commit {}, which the repository does not hold. " +
                                "Its history will be the one of the release before it",
                        version.getName(), version.getCommitId());
            }
            return released;
        } catch (IOException e) {
            log.warn("Unable to read the commit version {} is tagged on: {}. " +
                    "Its history will be the one of the release before it", version.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Folds into the accumulators the commits a release brought in.
     *
     * @param repository   the repository being read
     * @param formatter    the formatter the changes are read through
     * @param version      the release being walked, used for reporting
     * @param released     the commit it is tagged on
     * @param walked       the commits of the releases already walked
     * @param accumulators the history read so far, updated in place
     */
    private void walkCommits(Repository repository, DiffFormatter formatter, Version version,
                             ObjectId released, List<ObjectId> walked, Map<String, Accumulator> accumulators) {
        int commits = 0;
        try (RevWalk walk = new RevWalk(repository)) {
            walk.markStart(walk.parseCommit(released));
            for (ObjectId earlier : walked) {
                walk.markUninteresting(walk.parseCommit(earlier));
            }
            for (RevCommit commit : walk) {
                applyCommit(walk, formatter, commit, accumulators);
                commits++;
            }
        } catch (IOException e) {
            log.warn("Unable to walk the commits of version {}: {}. Its history will be incomplete",
                    version.getName(), e.getMessage());
        }
        log.debug("Version {} brought in {} commits", version.getName(), commits);
    }

    /**
     * Folds a single commit into the accumulators of the classes it touched.
     * <p>
     * A merge is left out: what it brings in was already counted on the branch it came from, and
     * comparing it with its first parent alone would count that work a second time.
     *
     * @param walk         the walk the commit was read from, used to read the tree of its parent
     * @param formatter    the formatter the changes are read through
     * @param commit       the commit to fold in
     * @param accumulators the history read so far, updated in place
     * @throws IOException if the trees being compared cannot be read
     */
    private void applyCommit(RevWalk walk, DiffFormatter formatter, RevCommit commit,
                             Map<String, Accumulator> accumulators) throws IOException {
        if (commit.getParentCount() > 1) {
            return;
        }
        // A root commit has no previous state to be compared with: the empty tree stands in for it
        ObjectId parentTree = commit.getParentCount() == 0
                ? null
                : walk.parseCommit(commit.getParent(0)).getTree();
        boolean bugFix = bugFixCommits.contains(commit.getName());
        String author = authorOf(commit);
        for (DiffEntry entry : formatter.scan(parentTree, commit.getTree())) {
            String path = entry.getNewPath();
            if (entry.getChangeType() == DiffEntry.ChangeType.DELETE || !filter.accepts(path)) {
                continue;
            }
            LineDelta delta = linesChanged(formatter, entry);
            accumulators.computeIfAbsent(path, key -> new Accumulator())
                    .recordCommit(author, delta, bugFix);
        }
    }

    /**
     * Identifies the author of a commit by the address they signed it with, which stays the same over
     * a history spanning years far more reliably than the name they display it under. The name stands
     * in whenever the address is missing.
     *
     * @param commit the commit whose author is wanted
     * @return the identity the author is counted under
     */
    private static String authorOf(RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        if (author == null) {
            return "";
        }
        String email = author.getEmailAddress();
        String identity = email == null || email.isBlank() ? author.getName() : email;
        return identity == null ? "" : identity.toLowerCase(Locale.ROOT);
    }

    /**
     * Counts the lines a commit added to and removed from a single source file.
     *
     * @param formatter the formatter the change is read through
     * @param entry     the change to measure
     * @return the lines it added and the ones it removed
     * @throws IOException if the two versions of the file cannot be read
     */
    private static LineDelta linesChanged(DiffFormatter formatter, DiffEntry entry) throws IOException {
        long added = 0;
        long deleted = 0;
        for (Edit edit : formatter.toFileHeader(entry).toEditList()) {
            added += edit.getEndB() - edit.getBeginB();
            deleted += edit.getEndA() - edit.getBeginA();
        }
        return new LineDelta(added, deleted);
    }

    /**
     * Freezes what is known about every class so far into the history of the release just walked.
     *
     * @param accumulators the history read so far
     * @param index        the ordinal index of the release being closed
     * @return the history of each class as of that release
     */
    private static Map<String, ClassHistory> snapshot(Map<String, Accumulator> accumulators, int index) {
        Map<String, ClassHistory> released = new HashMap<>();
        accumulators.forEach((path, accumulator) -> released.put(path, accumulator.freeze(index)));
        return released;
    }

    /**
     * The lines a single commit added to and removed from a single source file.
     *
     * @param added   the lines it added
     * @param deleted the lines it removed
     */
    private record LineDelta(long added, long deleted) {
    }

    /**
     * The history of one class as it is being read, i.e. the running counts the immutable
     * {@link ClassHistory} of each release is frozen out of.
     */
    private static final class Accumulator {

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
         * @param author the identity of whoever wrote the commit
         * @param delta  the lines it added to and removed from the class
         * @param bugFix whether it fixed a bug
         */
        private void recordCommit(String author, LineDelta delta, boolean bugFix) {
            revisions++;
            authors.add(author);
            releaseChurn += delta.added() + delta.deleted();
            sizeChange += delta.added() - delta.deleted();
            if (bugFix) {
                releaseBugFixes++;
                totalBugFixes++;
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
