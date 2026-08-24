package it.uniroma2.dicii.isw2.buggyness.impl;

import it.uniroma2.dicii.isw2.buggyness.exception.BuggynessException;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The reading of which classes the fix of each defect changed, kept apart from
 * {@link AffectedVersionsLabeller} so that the labeller is left with nothing but the labelling:
 * everything about JGit lives here, as everything about it lives in {@code ReleaseHistoryAnalyzer}
 * for the evolution metrics. Naming the classes is left to {@link DeclaredClassNames}, which knows
 * nothing about defects in turn.
 * <p>
 * A class is reported by its <em>fully qualified name</em> rather than by the path of the file the
 * fix touched, and that is the whole point of this class. The label has to be joined onto rows the
 * dataset holds for releases the fix was never part of — a defect affects the releases from the one
 * that injected it to the one that fixed it — and a project does not keep its sources at the same
 * path for that long: ZooKeeper moved every one of them on becoming a Maven project, once per line
 * it was maintaining, so a fix committed on {@code branch-3.4} names
 * {@code src/java/main/org/apache/zookeeper/Foo.java} while release 3.6.0 holds
 * {@code zookeeper-server/src/main/java/org/apache/zookeeper/Foo.java}. Joining on the path would
 * label neither, silently — the same shape of failure that made {@code NLBF} and {@code WNBF} read
 * zero for every patch release. The qualified name is the same in both, and is what the dataset
 * already names a row by.
 * <p>
 * Nothing here reads {@code HEAD}: the commits are resolved by identifier, so the reading is
 * unaffected by the clone being left detached at the last release that was measured. It does depend
 * on the clone holding every ref, since the fixes of a patch release live on the maintenance branch
 * of its line — see {@code GitCommitRetriever}.
 */
@Slf4j
class FixedClassesAnalyzer {

    private static final String JAVA_EXTENSION = ".java";

    /**
     * How many sources a single commit may have moved for the ones it moved <em>and</em> edited to be
     * looked for as well, as in {@code ReleaseHistoryAnalyzer}: the settings of the two formatters
     * match because the two are reading the same history, not because either derives them from the
     * other.
     */
    private static final int RENAME_LIMIT = 5000;

    private final SourceFilter filter;

    /**
     * The classes changed by each commit already read. A commit closing two tickets is one fix of each
     * of them, and would otherwise be diffed once per ticket it cites.
     */
    private final Map<String, Set<String>> byCommit = new HashMap<>();

    /**
     * How many of the commits read were merges, and how many the clone did not hold. Both are skipped,
     * and both cost labels, so both are reported at the end of the reading.
     */
    private int merges;
    private int missing;

    /**
     * @param filter the rule telling which sources are rows of the dataset, the very same one the
     *               extractors of the composite are given: a class the dataset holds no row of cannot
     *               be labelled, and a test class must not be labelled in place of the class it
     *               exercises
     */
    FixedClassesAnalyzer(SourceFilter filter) {
        this.filter = filter;
    }

    /**
     * Reads which classes the commits closing each defect changed.
     *
     * @param repoPath the root directory of the repository to read
     * @param fixes    the commits referencing each of the defects retrieved from Jira
     * @return the fully qualified names of the classes each defect was fixed in, keyed by the key of
     * the defect; the defects whose fix changed no class of the dataset are left out
     * @throws BuggynessException if the repository cannot be opened
     */
    Map<String, Set<String>> analyse(Path repoPath, Map<Issue, List<Commit>> fixes) throws BuggynessException {
        Map<String, Set<String>> fixed = new HashMap<>();
        log.info("Reading the classes the fixes of {} defects changed, under {}...", fixes.size(), repoPath);
        try (Git git = Git.open(repoPath.toFile());
             DiffFormatter formatter = newFormatter(git.getRepository());
             RevWalk walk = new RevWalk(git.getRepository())) {
            Repository repository = git.getRepository();
            Reading reading = new Reading(repository, walk, formatter, new DeclaredClassNames(repository));
            // The map is only ever iterated, never queried: its keys are mutable Lombok objects the
            // Proportion has written into since it was built, so their buckets no longer hold them
            for (Map.Entry<Issue, List<Commit>> fix : fixes.entrySet()) {
                Set<String> classes = classesFixedBy(reading, fix.getValue());
                if (!classes.isEmpty()) {
                    fixed.put(fix.getKey().getKey(), classes);
                }
            }
            report(fixes.size(), fixed.size(), reading.names().size());
        } catch (IOException e) {
            throw new BuggynessException("Unable to read the fixes out of the repository at '"
                    + repoPath + "'", e);
        }
        return fixed;
    }

    /**
     * Builds the formatter the changes of a commit are read through: only the Java sources are
     * scanned, and renames are detected so that a fix moving a file is not read as a fix writing it.
     * <p>
     * Detecting them matters more here than it does for the evolution metrics. With detection off, a
     * commit that moves a source produces an addition of the new path and a deletion of the old one,
     * and the addition reads as the fix having written that class: the commit that made ZooKeeper a
     * Maven project moved 355 sources at once, so a single fix bundled with a reorganisation would
     * label the whole project buggy over every release it affected.
     *
     * @param repository the repository whose commits are read
     * @return the formatter to read them through, to be closed by the caller
     */
    private static DiffFormatter newFormatter(Repository repository) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repository);
        formatter.setDiffComparator(RawTextComparator.DEFAULT);
        formatter.setPathFilter(PathSuffixFilter.create(JAVA_EXTENSION));
        formatter.setDetectRenames(true);
        formatter.getRenameDetector().setRenameLimit(RENAME_LIMIT);
        return formatter;
    }

    /**
     * @param reading the repository being read
     * @param commits the commits closing a single defect
     * @return the classes they changed between them
     */
    private Set<String> classesFixedBy(Reading reading, List<Commit> commits) {
        Set<String> classes = new HashSet<>();
        for (Commit commit : commits) {
            classes.addAll(byCommit.computeIfAbsent(commit.id(), id -> classesChangedBy(reading, id)));
        }
        return classes;
    }

    /**
     * Reads the classes a single commit changed.
     * <p>
     * A merge is left out, as it is by the reading of the evolution metrics, though for a different
     * reason: there, comparing it with its first parent would count the work of the merged branch
     * twice; here, it would credit the fix with every class that branch happened to touch, which on a
     * long-lived release branch is most of the project. A commit the repository cannot offer is
     * reported and left contributing nothing, rather than aborting the labelling of the whole project.
     *
     * @param reading the repository being read
     * @param id      the identifier of the commit to read
     * @return the classes it changed, empty if it changed none or could not be read
     */
    private Set<String> classesChangedBy(Reading reading, String id) {
        try {
            ObjectId committed = reading.repository().resolve(id);
            if (committed == null) {
                log.warn("Commit {} fixed a defect, but the repository does not hold it: the classes "
                        + "it changed cannot be labelled", id);
                missing++;
                return Set.of();
            }
            RevCommit commit = reading.walk().parseCommit(committed);
            if (commit.getParentCount() > 1) {
                merges++;
                return Set.of();
            }
            // A root commit has no previous state to be compared with: the empty tree stands in for it
            ObjectId parentTree = commit.getParentCount() == 0
                    ? null
                    : reading.walk().parseCommit(commit.getParent(0)).getTree();
            return changedClasses(reading, parentTree, commit);
        } catch (IOException e) {
            log.warn("Unable to read commit {}: {}. The classes it changed will not be labelled",
                    id, e.getMessage());
            missing++;
            return Set.of();
        }
    }

    /**
     * Names the classes a commit changed.
     *
     * @param reading    the repository being read
     * @param parentTree the tree of the parent of the commit, null for a root commit
     * @param commit     the commit being read
     * @return the fully qualified names of the classes it changed
     * @throws IOException if the two trees being compared cannot be read
     */
    private Set<String> changedClasses(Reading reading, ObjectId parentTree, RevCommit commit) throws IOException {
        Set<String> classes = new HashSet<>();
        for (DiffEntry entry : reading.formatter().scan(parentTree, commit.getTree())) {
            if (changedSource(entry)) {
                classes.add(reading.names().of(entry.getNewPath(), content(entry)));
            }
        }
        return classes;
    }

    /**
     * Decides whether a single change of a commit changed a class the dataset holds a row of.
     * <p>
     * A deletion changes no class that any later release still holds. A source the filter rejects is
     * no row of the dataset, and labelling a test class in place of the class it exercises would be
     * worse than labelling nothing. And the two sides of a pure move hold the very same content: a
     * class that only moved was not fixed, whatever the commit that moved it was closing.
     *
     * @param entry the change to judge
     * @return whether the class at its new path was changed by it
     */
    private boolean changedSource(DiffEntry entry) {
        return entry.getChangeType() != DiffEntry.ChangeType.DELETE
                && !entry.getOldId().equals(entry.getNewId())
                && filter.accepts(entry.getNewPath());
    }

    /**
     * @param entry a change that left a source file behind
     * @return the identifier of the content it left, or {@code null} if the change does not report a
     * complete one
     */
    private static ObjectId content(DiffEntry entry) {
        AbbreviatedObjectId blob = entry.getNewId();
        return blob == null ? null : blob.toObjectId();
    }

    /**
     * Reports what the reading found and what it had to leave out, so that a labelling that comes out
     * empty can be told apart from a project whose defects were never fixed in its classes.
     *
     * @param defects the defects that were read
     * @param fixed   how many of them were fixed in a class the dataset holds a row of
     * @param sources how many distinct sources had to be read to name those classes
     */
    private void report(int defects, int fixed, int sources) {
        log.info("{} of the {} defects were fixed in a class the dataset holds a row of, named after "
                + "the {} sources read out of the repository", fixed, defects, sources);
        if (merges > 0) {
            log.warn("{} of the commits closing a defect are merges and were not read: the classes "
                    + "they fixed are labelled only through the other commits of their defect", merges);
        }
        if (missing > 0) {
            log.warn("{} of the commits closing a defect could not be read out of the clone: the "
                    + "classes they fixed go unlabelled", missing);
        }
    }

    /**
     * The handles a single reading of the repository is carried out through, bundled so that they
     * travel together rather than as four parameters of every method below.
     *
     * @param repository the repository being read
     * @param walk       the walk the commits are parsed through
     * @param formatter  the formatter the changes are read through
     * @param names      how the classes the changes touched are named, and the cache of those names
     */
    private record Reading(Repository repository, RevWalk walk, DiffFormatter formatter,
                           DeclaredClassNames names) {
    }
}
