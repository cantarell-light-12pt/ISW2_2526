package it.uniroma2.dicii.isw2.proportion.impl;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.proportion.ProportionStrategy;
import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.proportion.model.DefectLifeCycle;
import it.uniroma2.dicii.isw2.proportion.model.VersionCatalog;
import it.uniroma2.dicii.isw2.versions.model.Version;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Groups the work shared by every variant of the Proportion approach: reconstructing the life cycle of
 * each defect, deciding which defects actually need an estimate, and writing the estimated injected
 * version back into the issues. Subclasses only provide the estimation itself.
 */
@Slf4j
public abstract class AbstractProportion implements ProportionStrategy {

    protected static final int FIRST_VERSION_INDEX = 1;

    /**
     * Orders defects by fix date, as prescribed by the paper. The index of the fixed version is the
     * primary key, so that defects fixed in the same release form a single group; the resolution date
     * breaks ties within a release, with the defects missing it sorted last.
     */
    private static final Comparator<DefectLifeCycle> BY_FIX_DATE =
            Comparator.comparingInt(DefectLifeCycle::fv)
                    .thenComparing(lifeCycle -> lifeCycle.issue().getResolutionDate(),
                            Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public void applyProportion(List<Issue> issues, List<Version> versions) throws ProportionException {
        if (issues == null) {
            throw new ProportionException("The list of issues cannot be null");
        }
        VersionCatalog catalog = new VersionCatalog(versions);

        List<DefectLifeCycle> lifeCycles = new ArrayList<>();
        for (Issue issue : issues) {
            DefectLifeCycle lifeCycle = buildLifeCycle(issue, catalog);
            if (lifeCycle != null) {
                lifeCycles.add(lifeCycle);
            }
        }
        lifeCycles.sort(BY_FIX_DATE);

        long known = lifeCycles.stream().filter(DefectLifeCycle::hasInjectedVersion).count();
        log.info("Placed {} defects out of {} in the release history: {} report a usable injected version, "
                        + "{} will be estimated through the {} method",
                lifeCycles.size(), issues.size(), known, lifeCycles.size() - known, getClass().getSimpleName());

        estimateInjectedVersions(lifeCycles, catalog);
    }

    /**
     * Estimates the injected version of every defect of the given list that does not already report a
     * usable one, i.e. every defect for which {@link DefectLifeCycle#hasInjectedVersion()} is false. The
     * defects reporting one are still part of the list, since they carry the observations the estimation
     * learns from.
     * <p>
     * Implementations write their result through {@link #setInjectedVersion(DefectLifeCycle, int, VersionCatalog)}.
     *
     * @param lifeCycles the life cycle of every defect that could be placed in the release history,
     *                   ordered by fix date
     * @param catalog    the released versions of the project
     */
    protected abstract void estimateInjectedVersions(List<DefectLifeCycle> lifeCycles, VersionCatalog catalog);

    /**
     * Records the estimated injected version of a defect, together with the versions it affects.
     * <p>
     * The given index is bounded to the range {@code [1, OV]}: a defect cannot be injected before the
     * first release, nor after the failure it caused was observed. The affected versions are then
     * relabelled as all the versions from the injected version (included) to the fixed version
     * (excluded), which discards whatever inconsistent affected versions the defect report contained.
     *
     * @param lifeCycle the defect to label
     * @param index     the estimated index of the injected version, before bounding
     * @param catalog   the released versions of the project
     */
    protected void setInjectedVersion(DefectLifeCycle lifeCycle, int index, VersionCatalog catalog) {
        int bounded = Math.clamp(index, FIRST_VERSION_INDEX, lifeCycle.ov());
        Version injected = label(lifeCycle, catalog.byIndex(bounded), catalog);
        log.debug("Estimated the injected version of defect {} as {} (OV: {}, FV: {})",
                lifeCycle.issue().getKey(), injected.getName(),
                lifeCycle.openingVersion().getName(), lifeCycle.fixedVersion().getName());
    }

    /**
     * Writes into a defect the version it was injected in and the versions it affects, the latter
     * being all the versions from the injected one (included) to the fixed one (excluded).
     * <p>
     * Every defect that could be placed in the release history goes through here, whether its
     * injected version was estimated or read from its report, so that the two carry the same notion
     * of what "affected" means. The lists a defect report comes with are sparse — a defect reported
     * against 3.4.6 and fixed in 3.4.10 says nothing about the three releases in between, which it
     * was nonetheless present in — and whoever reads the affected versions afterwards, the
     * buggy/not-buggy labelling above all, would otherwise see the estimated defects affect far more
     * releases than the reported ones.
     *
     * @param lifeCycle the defect to label
     * @param injected  the version the defect was injected in, already bounded
     * @param catalog   the released versions of the project
     * @return the injected version it was labelled with
     */
    private Version label(DefectLifeCycle lifeCycle, Version injected, VersionCatalog catalog) {
        lifeCycle.issue().setInjected(injected);
        lifeCycle.issue().setAffectedVersions(catalog.range(injected.getIndex(), lifeCycle.fv()));
        return injected;
    }

    /**
     * Reconstructs the life cycle of a defect, resolving its opening, fixed and injected versions
     * against the released versions of the project, and records the opening and injected versions on the
     * issue itself.
     * <p>
     * A defect reporting a usable injected version is labelled here and now, since nothing about it is
     * left to estimate: its affected versions are rewritten as {@code [IV, FV)}, exactly as
     * {@link #setInjectedVersion(DefectLifeCycle, int, VersionCatalog)} rewrites the ones of the
     * defects that had to be estimated.
     *
     * @param issue   the defect to place in the release history
     * @param catalog the released versions of the project
     * @return the life cycle of the defect, or {@code null} if it cannot be placed in the release
     * history because its opening or fixed version is unknown
     */
    private DefectLifeCycle buildLifeCycle(Issue issue, VersionCatalog catalog) {
        Version opening = catalog.openingVersionFor(issue.getCreationDate());
        if (opening == null) {
            log.debug("Defect {} was reported on {}, after the newest release: skipping it",
                    issue.getKey(), issue.getCreationDate());
            return null;
        }
        Version fixed = resolveFixedVersion(issue, catalog);
        if (fixed == null) {
            log.debug("Defect {} does not report any known fix version: skipping it", issue.getKey());
            return null;
        }
        issue.setOpening(opening);
        issue.setInjected(resolveInjectedVersion(issue, catalog, opening));
        DefectLifeCycle lifeCycle = new DefectLifeCycle(issue, opening, fixed);
        if (lifeCycle.hasInjectedVersion()) {
            label(lifeCycle, issue.getInjected(), catalog);
        }
        return lifeCycle;
    }

    /**
     * Resolves the fixed version (FV) of a defect as the <em>oldest</em> of the fix versions listed in
     * its report, i.e. the first release that shipped the fix. Defects whose report lists no fix
     * version, or only versions that are not released versions of the project, have no fixed version
     * and cannot be labelled.
     * <p>
     * The oldest and not the newest, because a project maintaining several lines at once backports a
     * fix to every line still alive and Jira lists all of them: of the 637 defects ZooKeeper fixed on
     * the 3.5 line after 3.6 had branched off it, 528 were fixed on the 3.6 line as well. Reading the
     * newest of those as the fixed version stretches the range of affected releases written below over
     * every line the fix was carried to, so that the releases which actually shipped it — the whole
     * point of a backport — come out labelled as still holding the defect.
     *
     * @param issue   the defect whose fixed version is wanted
     * @param catalog the released versions of the project
     * @return the fixed version, or {@code null} if it cannot be determined
     */
    private Version resolveFixedVersion(Issue issue, VersionCatalog catalog) {
        if (issue.getFixed() == null) {
            return null;
        }
        return issue.getFixed().stream()
                .map(version -> catalog.byName(version.getName()))
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(Version::getIndex))
                .orElse(null);
    }

    /**
     * Resolves the injected version (IV) reported by a defect, i.e. the oldest of its affected versions,
     * and keeps it only if it is usable.
     * <p>
     * An injected version is usable when it is both <em>available</em>, i.e. the report lists affected
     * versions that are released versions of the project, and <em>consistent</em>, i.e. it is not newer
     * than the opening version — a defect cannot be injected after the failure it caused was observed.
     * Returning {@code null} marks the defect as one whose injected version has to be estimated.
     *
     * @param issue   the defect whose injected version is wanted
     * @param catalog the released versions of the project
     * @param opening the opening version of the defect
     * @return the reported injected version, or {@code null} if it is unavailable or inconsistent
     */
    private Version resolveInjectedVersion(Issue issue, VersionCatalog catalog, Version opening) {
        if (issue.getAffectedVersions() == null) {
            return null;
        }
        Version oldest = issue.getAffectedVersions().stream()
                .map(version -> catalog.byName(version.getName()))
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(Version::getIndex))
                .orElse(null);
        if (oldest == null) {
            return null;
        }
        if (oldest.getIndex() > opening.getIndex()) {
            log.debug("Defect {} reports {} as its oldest affected version, which is newer than its opening "
                            + "version {}: the reported affected versions are inconsistent and will be estimated",
                    issue.getKey(), oldest.getName(), opening.getName());
            return null;
        }
        return oldest;
    }
}
