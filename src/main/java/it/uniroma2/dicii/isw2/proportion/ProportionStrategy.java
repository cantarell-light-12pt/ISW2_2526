package it.uniroma2.dicii.isw2.proportion;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.versions.model.Version;

import java.util.List;

/**
 * Estimates the injected version (IV) of the defects that do not report a usable one.
 * <p>
 * Implementations apply one of the variants of the Proportion approach described by Vandehei et al.,
 * "Leveraging the Defects Life Cycle to Label Affected Versions and Defective Classes" (TOSEM 30(2),
 * 2021), Section 3.2. The variant to use is chosen at runtime through {@link ProportionStrategyFactory}.
 */
public interface ProportionStrategy {

    /**
     * Estimates the injected version of every issue that does not report a usable one and writes the
     * result back into the given issues.
     * <p>
     * For each processed issue this method sets its opening version, and — only for the issues whose
     * injected version had to be estimated — its injected version and its affected versions, the latter
     * being all the versions from the injected version (included) to the fixed version (excluded).
     * Issues that already report a usable injected version are left untouched, as are the issues whose
     * opening or fixed version cannot be determined.
     *
     * @param issues   the issues to label, modified in place
     * @param versions the released versions of the project, already numbered through
     *                 {@link Version#numberVersions(List)}
     * @throws ProportionException if the arguments are null, or if the versions have not been numbered
     */
    void applyProportion(List<Issue> issues, List<Version> versions) throws ProportionException;
}
