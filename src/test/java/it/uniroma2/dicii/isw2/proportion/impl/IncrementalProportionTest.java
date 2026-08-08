package it.uniroma2.dicii.isw2.proportion.impl;

import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.proportion.ProportionStrategy;
import it.uniroma2.dicii.isw2.proportion.exception.ProportionException;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static it.uniroma2.dicii.isw2.proportion.impl.ProportionTestData.defect;
import static it.uniroma2.dicii.isw2.proportion.impl.ProportionTestData.versions;

public class IncrementalProportionTest {

    private static final int RELEASES = 16;
    private static final String TARGET = "PROJ-TARGET";

    private List<Version> versions;
    private ProportionStrategy strategy;

    @Before
    public void setUp() {
        versions = versions(RELEASES);
        strategy = new IncrementalProportion();
    }

    /**
     * Builds {@code count} defects reporting a consistent injected version, all fixed in version 10 and
     * opened towards version 8, alternating the injected versions 6 and 7 so that their proportions
     * alternate between {@code (10-6)/(10-8) = 2.0} and {@code (10-7)/(10-8) = 1.5}. With five of them
     * the resulting average proportion is exactly {@code 9.0 / 5 = 1.8}.
     */
    private List<Issue> knownDefects(int count) {
        List<Issue> defects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            defects.add(defect("PROJ-KNOWN-" + i, versions, 8, 10, i % 2 == 0 ? 6 : 7));
        }
        return defects;
    }

    @Test
    public void appliesTheAverageProportionOfThePreviousVersions() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = knownDefects(5);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        // P = 1.8, hence IV = 16 - (16 - 14) * 1.8 = 12.4, i.e. version 13
        Assert.assertEquals("13.0", target.getInjected().getName());
    }

    @Test
    public void roundsTheEstimatedInjectedVersionUp() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = knownDefects(5);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        // A version is affected only from the estimated IV onwards, so 12.4 must yield 13 and not 12:
        // this is what makes the paper report version 14 of its Figure 2 example as a false negative
        Assert.assertEquals(13, target.getInjected().getIndex());
    }

    @Test
    public void labelsAsAffectedEveryVersionFromTheInjectedToTheFixedExcluded() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = knownDefects(5);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        List<String> affected = target.getAffectedVersions().stream().map(Version::getName).toList();
        Assert.assertEquals(List.of("13.0", "14.0", "15.0"), affected);
    }

    @Test
    public void fallsBackToTheSimpleMethodWhenTooFewDefectsWereFixedBefore() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = knownDefects(IncrementalProportion.MIN_DEFECTS_TO_ESTIMATE - 1);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        Assert.assertEquals("Too little history must degrade to IV = OV", "14.0", target.getInjected().getName());
    }

    @Test
    public void ignoresTheDefectsFixedInTheSameVersionOrLater() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = new ArrayList<>();
        // Five defects reporting a consistent IV, but all fixed in the very version of the target defect
        for (int i = 0; i < 5; i++) {
            issues.add(defect("PROJ-LATER-" + i, versions, 14, 16, 6));
        }
        issues.add(target);

        strategy.applyProportion(issues, versions);

        Assert.assertEquals("Only the defects fixed in the previous versions may be used",
                "14.0", target.getInjected().getName());
    }

    @Test
    public void doesNotLearnFromItsOwnEstimates() throws ProportionException {
        Issue target = defect(TARGET, versions, 14, 16, null);
        List<Issue> issues = knownDefects(5);
        // Five defects with no reported AV, fixed early enough that they fall back to the Simple method.
        // Were their estimates fed back into the average, it would drop from 1.8 to 1.4 and the target
        // would be estimated as version 14 instead of 13.
        for (int i = 0; i < 5; i++) {
            issues.add(defect("PROJ-ESTIMATED-" + i, versions, 2, 3, null));
        }
        issues.add(target);

        strategy.applyProportion(issues, versions);

        Assert.assertEquals("13.0", target.getInjected().getName());
    }

    @Test
    public void neverEstimatesAnInjectedVersionNewerThanTheOpeningVersion() throws ProportionException {
        // Defects fixed in the very version they were opened towards have a proportion of 1, so the raw
        // estimate for the target would land past its own opening version
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            issues.add(defect("PROJ-KNOWN-" + i, versions, 9, 10, 9));
        }
        Issue target = defect(TARGET, versions, 15, 16, null);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        Assert.assertTrue("The IV must never be newer than the OV",
                target.getInjected().getIndex() <= target.getOpening().getIndex());
    }

    @Test
    public void neverEstimatesAnInjectedVersionOlderThanTheFirstRelease() throws ProportionException {
        // Huge proportions, obtained from defects injected in the very first release and fixed much later
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            issues.add(defect("PROJ-KNOWN-" + i, versions, 12, 13, 1));
        }
        Issue target = defect(TARGET, versions, 14, 16, null);
        issues.add(target);

        strategy.applyProportion(issues, versions);

        Assert.assertEquals("The IV must be bounded to the first release", 1, target.getInjected().getIndex());
    }
}
