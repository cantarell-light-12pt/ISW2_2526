package it.uniroma2.dicii.isw2.proportion.model;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.versions.model.Version;

/**
 * The life cycle of a defect expressed through the ordinal indices of the versions involved, as
 * illustrated in Figure 2 of Vandehei et al. (TOSEM 30(2), 2021): the defect is injected in the code at
 * the injected version (IV), the resulting failure is observed and reported at the opening version (OV),
 * and the defect is finally fixed at the fixed version (FV).
 * <p>
 * The opening and fixed versions are resolved once and held here, while the injected version is read
 * from and written back to the underlying issue, since it is the value the Proportion method estimates.
 *
 * @param issue          the defect this life cycle belongs to
 * @param openingVersion the version related to the creation of the defect report
 * @param fixedVersion   the version related to the fix of the defect
 */
public record DefectLifeCycle(Issue issue, Version openingVersion, Version fixedVersion) {

    /**
     * @return the index of the opening version (OV)
     */
    public int ov() {
        return openingVersion.getIndex();
    }

    /**
     * @return the index of the fixed version (FV)
     */
    public int fv() {
        return fixedVersion.getIndex();
    }

    /**
     * @return whether the injected version of this defect is known, i.e. it was reported in the defect
     * report and it is consistent, and therefore does not need to be estimated
     */
    public boolean hasInjectedVersion() {
        return issue.getInjected() != null;
    }

    /**
     * @return the index of the injected version (IV)
     * @throws NullPointerException if the injected version is not known
     */
    public int iv() {
        return issue.getInjected().getIndex();
    }

    /**
     * Returns the number of versions the project needed to discover and fix this defect, i.e.
     * {@code FV - OV}.
     * <p>
     * As prescribed by the paper, the result is forced to one when FV equals OV, both to avoid a
     * division by zero when computing the proportion and to guarantee that the estimated injected
     * version does not degenerate into the fixed version itself.
     *
     * @return {@code FV - OV}, or 1 when that difference is not strictly positive
     */
    public int versionsToFix() {
        return Math.max(1, fv() - ov());
    }

    /**
     * Computes the proportion {@code P = (FV - IV) / (FV - OV)} of this defect, i.e. the share of the
     * defect life cycle spent before the failure was observed. Only meaningful for defects whose
     * injected version is known, as those are the ones the estimation learns from.
     *
     * @return the proportion of this defect
     * @throws NullPointerException if the injected version is not known
     */
    public double proportion() {
        return (double) (fv() - iv()) / versionsToFix();
    }
}
