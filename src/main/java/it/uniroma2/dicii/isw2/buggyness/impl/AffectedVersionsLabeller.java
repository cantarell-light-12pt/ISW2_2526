package it.uniroma2.dicii.isw2.buggyness.impl;

import it.uniroma2.dicii.isw2.buggyness.BuggynessLabeller;
import it.uniroma2.dicii.isw2.buggyness.exception.BuggynessException;
import it.uniroma2.dicii.isw2.buggyness.model.BuggyClasses;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.model.ClassMetrics;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Labels a class of a release buggy when a defect affecting that release was fixed in that class.
 * <p>
 * The two halves of that rule come from either end of the pipeline. <em>When</em> a class was buggy
 * is what the affected versions of a defect say: a defect is present in the project from the release
 * that injected it to the one that fixed it, and the Proportion has by this point written that range
 * into every defect it could place in the release history, whether it had to estimate the injected
 * version or read it from the report. <em>Which</em> class was buggy is what the commits closing the
 * defect say, and is read by {@link FixedClassesAnalyzer}.
 * <p>
 * A class is therefore labelled buggy at releases the fix was never part of, which is the whole
 * point: a model trained on this dataset has to recognise a class that is <em>about</em> to be found
 * faulty, not one that has already been repaired.
 * <p>
 * The whole repository is read once, when the labeller is built, rather than when the first release
 * is labelled. Measuring a release takes minutes, and a labeller that only discovered it could not
 * read the fixes after the first release had been measured would either abort a run halfway through
 * or, worse, write out release after release whose label column is all zeros for no reason the file
 * records.
 */
@Slf4j
public class AffectedVersionsLabeller implements BuggynessLabeller {

    private final BuggyClasses buggyClasses;

    private AffectedVersionsLabeller(BuggyClasses buggyClasses) {
        this.buggyClasses = buggyClasses;
    }

    /**
     * Reads out of the repository which classes held a defect at which release, and builds the
     * labeller answering that question one release at a time.
     *
     * @param repoPath the root directory of the repository holding the fixes to read
     * @param versions the released versions of the project, already associated with their Git tags
     *                 and numbered
     * @param fixes    the commits referencing each of the defects retrieved from Jira. The map is only
     *                 ever iterated, never queried: {@link Issue} is a mutable Lombok {@code @Data},
     *                 the Proportion has written its opening, injected and affected versions into it
     *                 since the map was built, and its hash changed with them
     * @param filter   the rule telling which sources are rows of the dataset, shared with the
     *                 extractors of the composite so that the label describes the classes they measured
     * @return a labeller over the defects that repository records
     * @throws BuggynessException if the fixes cannot be read out of the repository
     */
    public static AffectedVersionsLabeller reading(Path repoPath, List<Version> versions,
                                                   Map<Issue, List<Commit>> fixes, SourceFilter filter)
            throws BuggynessException {
        // The versions embedded in a Jira issue are distinct, partial objects carrying no index of
        // their own, so an affected version has to be resolved against the release history by name
        Map<String, Integer> releasesByName = new HashMap<>();
        versions.forEach(version -> releasesByName.putIfAbsent(version.getName(), version.getIndex()));
        Map<String, Set<String>> fixedClasses = new FixedClassesAnalyzer(filter).analyse(repoPath, fixes);
        return new AffectedVersionsLabeller(index(releasesByName, fixes, fixedClasses));
    }

    @Override
    public void label(Version version, MetricsReport report) throws BuggynessException {
        if (version == null) {
            throw new BuggynessException("The classes of a snapshot taken outside the release history "
                    + "cannot be labelled, since nothing says which defects were present in them");
        }
        int labelled = 0;
        for (ClassMetrics metrics : report.getClasses()) {
            boolean held = buggyClasses.holds(version.getIndex(), metrics.getClassName());
            metrics.setBuggy(held);
            if (held) {
                labelled++;
            }
        }
        report(version, report.size(), labelled);
    }

    /**
     * Spreads the classes each defect was fixed in over the releases that defect affected.
     *
     * @param releasesByName the ordinal index of each released version, by name
     * @param fixes          the commits referencing each of the defects
     * @param fixedClasses   the classes each defect was fixed in, by the key of the defect
     * @return which classes held a defect at which release
     */
    private static BuggyClasses index(Map<String, Integer> releasesByName, Map<Issue, List<Commit>> fixes,
                                      Map<String, Set<String>> fixedClasses) {
        BuggyClasses buggy = new BuggyClasses();
        int placed = 0;
        for (Map.Entry<Issue, List<Commit>> fix : fixes.entrySet()) {
            Issue defect = fix.getKey();
            Set<String> classes = fixedClasses.get(defect.getKey());
            if (classes != null && spread(releasesByName, buggy, defect, classes)) {
                placed++;
            }
        }
        log.info("{} defects left at least one class buggy, over {} of the released versions",
                placed, buggy.releases());
        return buggy;
    }

    /**
     * Records the classes a defect was fixed in as buggy at every release that defect affected.
     * <p>
     * An affected version the project never released — one dropped for having no Git tag, or one the
     * report named and the project never published — names no rows of the dataset, and is skipped. So
     * is a defect affecting nothing at all: a defect the Proportion could not place in the release
     * history, because it reports no fix version the project ever released, keeps whatever affected
     * versions Jira gave it, and those are quite often none.
     *
     * @param releasesByName the ordinal index of each released version, by name
     * @param buggy          the index being built
     * @param defect         the defect to place
     * @param classes        the classes it was fixed in
     * @return whether it left a buggy class at at least one release
     */
    private static boolean spread(Map<String, Integer> releasesByName, BuggyClasses buggy, Issue defect,
                                  Set<String> classes) {
        List<Version> affected = defect.getAffectedVersions();
        if (affected == null || affected.isEmpty()) {
            log.debug("Defect {} affects no released version: the {} classes it was fixed in are "
                    + "labelled nowhere", defect.getKey(), classes.size());
            return false;
        }
        boolean placed = false;
        for (Version version : affected) {
            Integer release = releasesByName.get(version.getName());
            if (release == null) {
                log.debug("Defect {} affects version {}, which the dataset holds no rows of: skipping it",
                        defect.getKey(), version.getName());
                continue;
            }
            buggy.recordDefect(release, classes);
            placed = true;
        }
        return placed;
    }

    /**
     * Reports how many rows of a release came out buggy.
     * <p>
     * A join gone wrong reads as a column of zeros, which nothing downstream would notice, so the
     * count is logged as each release is labelled. A release whose classes are none of the ones the
     * defects name, while the defects do name classes at it, is louder still: that is not a release
     * the project happened to keep clean, that is the two sides of the join speaking different names.
     *
     * @param version  the release that was labelled
     * @param classes  how many classes it holds
     * @param labelled how many of them came out buggy
     */
    private void report(Version version, int classes, int labelled) {
        log.info("{} of the {} classes of version {} held at least one defect",
                labelled, classes, version.getName());
        int expected = buggyClasses.size(version.getIndex());
        if (labelled == 0 && expected > 0) {
            log.warn("None of the {} classes the defects report as buggy at version {} is a row of the "
                            + "dataset: the label column of this release is all zeros because the two "
                            + "sides of the join name their classes differently, not because the "
                            + "release is clean",
                    expected, version.getName());
        }
    }
}
