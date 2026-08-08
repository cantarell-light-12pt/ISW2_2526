package it.uniroma2.dicii.isw2.proportion.impl;

import it.uniroma2.dicii.isw2.proportion.model.DefectLifeCycle;
import it.uniroma2.dicii.isw2.proportion.model.VersionCatalog;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * The <em>Simple</em> method: it assumes the injected version simply corresponds to the opening version.
 * <p>
 * The rationale, as explained in Section 3.2 of the paper, is that by definition all the versions from
 * OV to FV are affected by the defect. Versions older than OV can be affected as well, though, so this
 * heuristic is expected to reach a very high precision but a low recall.
 */
@Slf4j
public class SimpleProportion extends AbstractProportion {

    @Override
    protected void estimateInjectedVersions(List<DefectLifeCycle> lifeCycles, VersionCatalog catalog) {
        int estimated = 0;
        for (DefectLifeCycle lifeCycle : lifeCycles) {
            if (lifeCycle.hasInjectedVersion()) {
                continue;
            }
            setInjectedVersion(lifeCycle, lifeCycle.ov(), catalog);
            estimated++;
        }
        log.info("Estimated the injected version of {} defects as their opening version", estimated);
    }
}
