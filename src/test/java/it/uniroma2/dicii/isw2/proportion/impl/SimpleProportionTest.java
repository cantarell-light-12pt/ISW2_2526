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

public class SimpleProportionTest {

    private static final int RELEASES = 16;

    private List<Version> versions;
    private ProportionStrategy strategy;

    @Before
    public void setUp() {
        versions = versions(RELEASES);
        strategy = new SimpleProportion();
    }

    @Test
    public void estimatesTheInjectedVersionAsTheOpeningVersion() throws ProportionException {
        Issue issue = defect("PROJ-1", versions, 14, 16, null);

        strategy.applyProportion(new ArrayList<>(List.of(issue)), versions);

        Assert.assertEquals("14.0", issue.getInjected().getName());
        Assert.assertEquals("14.0", issue.getOpening().getName());
    }

    @Test
    public void labelsAsAffectedEveryVersionFromTheInjectedToTheFixedExcluded() throws ProportionException {
        Issue issue = defect("PROJ-1", versions, 14, 16, null);

        strategy.applyProportion(new ArrayList<>(List.of(issue)), versions);

        List<String> affected = issue.getAffectedVersions().stream().map(Version::getName).toList();
        Assert.assertEquals("The fix version must not be labelled as affected", List.of("14.0", "15.0"), affected);
    }

    @Test
    public void leavesUntouchedTheDefectsAlreadyReportingAConsistentInjectedVersion() throws ProportionException {
        Issue issue = defect("PROJ-1", versions, 14, 16, 5);

        strategy.applyProportion(new ArrayList<>(List.of(issue)), versions);

        Assert.assertEquals("A reported and consistent AV must not be overwritten", "5.0", issue.getInjected().getName());
        Assert.assertEquals(1, issue.getAffectedVersions().size());
    }

    @Test
    public void estimatesTheDefectsWhoseReportedAffectedVersionsAreInconsistent() throws ProportionException {
        // The oldest AV is newer than the OV: the defect cannot have been injected after its own report
        Issue issue = defect("PROJ-1", versions, 14, 16, 15);

        strategy.applyProportion(new ArrayList<>(List.of(issue)), versions);

        Assert.assertEquals("14.0", issue.getInjected().getName());
        List<String> affected = issue.getAffectedVersions().stream().map(Version::getName).toList();
        Assert.assertEquals("Inconsistent AVs must be overwritten", List.of("14.0", "15.0"), affected);
    }

    @Test
    public void skipsTheDefectsReportingNoFixVersion() throws ProportionException {
        Issue issue = defect("PROJ-1", versions, 14, 16, null);
        issue.setFixed(List.of());

        strategy.applyProportion(new ArrayList<>(List.of(issue)), versions);

        Assert.assertNull("A defect with no fix version cannot be placed in the release history", issue.getInjected());
        Assert.assertNull(issue.getOpening());
    }

    @Test
    public void skipsTheDefectsReportedAfterTheNewestRelease() throws ProportionException {
        Issue issue = defect("PROJ-1", versions, 14, 16, null);
        List<Version> firstReleasesOnly = new ArrayList<>(versions.subList(0, 3));
        Version.numberVersions(firstReleasesOnly);

        strategy.applyProportion(new ArrayList<>(List.of(issue)), firstReleasesOnly);

        Assert.assertNull(issue.getInjected());
    }

    @Test(expected = ProportionException.class)
    public void rejectsVersionsThatHaveNotBeenNumbered() throws ProportionException {
        List<Version> unnumbered = List.of(new Version("id1", "1.0", true, false));

        strategy.applyProportion(new ArrayList<>(), unnumbered);
    }

    @Test(expected = ProportionException.class)
    public void rejectsNullIssues() throws ProportionException {
        strategy.applyProportion(null, versions);
    }
}
