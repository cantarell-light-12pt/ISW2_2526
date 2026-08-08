package it.uniroma2.dicii.isw2.proportion.impl;

import it.uniroma2.dicii.isw2.proportion.model.DefectLifeCycle;
import it.uniroma2.dicii.isw2.proportion.model.VersionCatalog;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The <em>Proportion_Increment</em> method: the proportion of the current defect is computed as the
 * average proportion of the defects of the same project fixed in the previous versions.
 * <p>
 * As described in Section 3.2 of the paper, for a defect fixed in version R the proportion
 * {@code P_Increment} is the average {@code P = (FV - IV) / (FV - OV)} among the defects fixed in
 * versions 1 to R-1, and the injected version is then estimated as
 * {@code IV = FV - (FV - OV) * P_Increment}. Using only the defects fixed in the previous versions is
 * what makes the method incremental: no defect is ever estimated using information that was not yet
 * available when it was fixed.
 * <p>
 * Two points where this implementation had to take a stance:
 * <ul>
 * <li>The paper falls back to <em>Proportion_ColdStart</em>, which borrows the proportion of other
 * projects, whenever fewer than {@value #MIN_DEFECTS_TO_ESTIMATE} defects are available. Since this
 * application mines a single project, ColdStart is not applicable and the fallback is the
 * {@link SimpleProportion} method instead.</li>
 * <li>Only the defects whose injected version was reported and is consistent contribute to the average,
 * so that estimated values never feed back into later estimates.</li>
 * </ul>
 */
@Slf4j
public class IncrementalProportion extends AbstractProportion {

    /**
     * The minimum number of defects the average proportion must be computed on for the estimate to be
     * considered trustworthy, as per step (d)(ii) of the measurement procedure of the paper.
     */
    protected static final int MIN_DEFECTS_TO_ESTIMATE = 5;

    @Override
    protected void estimateInjectedVersions(List<DefectLifeCycle> lifeCycles, VersionCatalog catalog) {
        int lastIndex = catalog.lastIndex();

        // For every version R, accumulate the proportions of the defects fixed exactly in R...
        double[] proportionSum = new double[lastIndex + 1];
        int[] defectCount = new int[lastIndex + 1];
        for (DefectLifeCycle lifeCycle : lifeCycles) {
            if (lifeCycle.hasInjectedVersion()) {
                proportionSum[lifeCycle.fv()] += lifeCycle.proportion();
                defectCount[lifeCycle.fv()]++;
            }
        }
        // ...then turn the totals into running ones, so that entry R covers every defect fixed in 1 to R
        for (int index = FIRST_VERSION_INDEX; index <= lastIndex; index++) {
            proportionSum[index] += proportionSum[index - 1];
            defectCount[index] += defectCount[index - 1];
        }

        int estimated = 0;
        int fellBack = 0;
        for (DefectLifeCycle lifeCycle : lifeCycles) {
            if (lifeCycle.hasInjectedVersion()) {
                continue;
            }
            int previousVersions = lifeCycle.fv() - 1;
            int count = defectCount[previousVersions];
            if (count < MIN_DEFECTS_TO_ESTIMATE) {
                log.debug("Only {} defects were fixed before version {}: falling back to the Simple method "
                        + "for defect {}", count, lifeCycle.fixedVersion().getName(), lifeCycle.issue().getKey());
                setInjectedVersion(lifeCycle, lifeCycle.ov(), catalog);
                fellBack++;
            } else {
                double proportion = proportionSum[previousVersions] / count;
                setInjectedVersion(lifeCycle, injectedVersionIndex(lifeCycle, proportion), catalog);
                estimated++;
            }
        }
        log.info("Estimated the injected version of {} defects through the average proportion of the "
                + "previous versions, {} through the Simple method for lack of enough defects", estimated, fellBack);
    }

    /**
     * Applies {@code IV = FV - (FV - OV) * P} and turns the result into a version index.
     * <p>
     * The result is rounded up rather than to the nearest integer: the paper labels as affected every
     * version from the estimated IV to the FV, so a version is affected only when its index is not lower
     * than the estimated value. With the example of Figure 2 — FV 16, OV 15 and P 1.7775 — this yields
     * 14.2225, hence version 15 rather than version 14, which is exactly the estimate the paper reports
     * as missing version 14.
     *
     * @param lifeCycle  the defect being estimated
     * @param proportion the proportion to apply
     * @return the estimated index of the injected version, before bounding
     */
    private int injectedVersionIndex(DefectLifeCycle lifeCycle, double proportion) {
        return (int) Math.ceil(lifeCycle.fv() - lifeCycle.versionsToFix() * proportion);
    }
}
