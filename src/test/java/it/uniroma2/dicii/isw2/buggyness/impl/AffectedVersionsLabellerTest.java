package it.uniroma2.dicii.isw2.buggyness.impl;

import it.uniroma2.dicii.isw2.buggyness.BuggynessLabeller;
import it.uniroma2.dicii.isw2.buggyness.exception.BuggynessException;
import it.uniroma2.dicii.isw2.issues.model.Issue;
import it.uniroma2.dicii.isw2.issues.model.IssueStatus;
import it.uniroma2.dicii.isw2.issues.model.IssueType;
import it.uniroma2.dicii.isw2.issues.model.ResolutionType;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.impl.PathSourceFilter;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.repo.model.Commit;
import it.uniroma2.dicii.isw2.versions.model.Version;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Labels the classes of a handful of releases out of a repository holding a single fix, checking that
 * the label lands on the releases the defect affected and on no other, and that it lands on rows whose
 * path is not the one the fix was committed at.
 */
public class AffectedVersionsLabellerTest {

    /**
     * Where the fix was committed, and where the release being labelled keeps the same class. The two
     * differ on purpose: a defect affects releases the fix was never part of, and those releases are
     * quite capable of keeping their sources somewhere else.
     */
    private static final String FIXED_AT = "src/java/main/app/Broken.java";
    private static final String HELD_AT = "module/src/main/java/app/Broken.java";

    private static final String BROKEN = "app.Broken";
    private static final String SOUND = "app.Sound";

    private static final String SOURCE = "package app;\nclass Broken {\n}\n";

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Rome");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private List<Version> versions;
    private Commit fix;

    @Before
    public void setUp() throws IOException, GitAPIException {
        versions = releases(4);
        Path repoPath = folder.getRoot().toPath();
        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(repoPath, FIXED_AT, SOURCE);
            commit(git, "Write it");
            write(repoPath, FIXED_AT, SOURCE.replace("}\n", "// fixed\n}\n"));
            fix = commitOf(commit(git, "Fix PROJ-1"));
        }
    }

    @Test
    public void testAClassIsBuggyAtEveryVersionTheDefectAffectedAndAtNoOther() throws BuggynessException {
        BuggynessLabeller labeller = labeller(defect("PROJ-1", 2, 3));

        assertTrue(isBuggy(labeller, versions.get(1)));
        assertTrue(isBuggy(labeller, versions.get(2)));
        assertFalse("The release before the defect was injected held no defect", isBuggy(labeller, versions.getFirst()));
        assertFalse("The release that fixed it holds it no more", isBuggy(labeller, versions.get(3)));
    }

    /**
     * The join is on the qualified name and not on the path, which is the only reason the label reaches
     * a release that keeps the class somewhere else than the branch the fix was committed on.
     */
    @Test
    public void testAClassIsLabelledWhereverTheReleaseKeepsIt() throws BuggynessException {
        BuggynessLabeller labeller = labeller(defect("PROJ-1", 2));

        MetricsReport report = new MetricsReport();
        report.forClass(HELD_AT, BROKEN);
        labeller.label(versions.get(1), report);

        assertTrue("The row is at another path and is the same class", report.forPath(HELD_AT).isBuggy());
    }

    @Test
    public void testAClassNoFixTouchedIsNotBuggy() throws BuggynessException {
        BuggynessLabeller labeller = labeller(defect("PROJ-1", 2));

        MetricsReport report = new MetricsReport();
        report.forClass("module/src/main/java/app/Sound.java", SOUND);
        labeller.label(versions.get(1), report);

        assertFalse(report.forPath("module/src/main/java/app/Sound.java").isBuggy());
    }

    /**
     * A version the report names and the project never released, or one dropped for having no Git tag,
     * is no row of the dataset and cannot be labelled.
     */
    @Test
    public void testAnAffectedVersionTheProjectNeverReleasedIsIgnored() throws BuggynessException {
        Issue defect = defect("PROJ-1", 2);
        defect.setAffectedVersions(List.of(new Version("id-9", "9.0", true, false)));

        BuggynessLabeller labeller = labeller(defect);

        for (Version version : versions) {
            assertFalse("Version " + version.getName() + " must hold no defect", isBuggy(labeller, version));
        }
    }

    @Test
    public void testADefectAffectingNothingLabelsNothing() throws BuggynessException {
        Issue defect = defect("PROJ-1", 2);
        defect.setAffectedVersions(List.of());

        BuggynessLabeller labeller = labeller(defect);

        assertFalse(isBuggy(labeller, versions.get(1)));
    }

    /**
     * The label of a class is rewritten at every release, and never carried over from the one before:
     * a class that held a defect at 2.0 and was repaired for 3.0 is buggy at the first and clean at the
     * second, and the same report object measured twice must say so.
     */
    @Test
    public void testTheLabelDescribesTheReleaseBeingLabelledAndNotThePreviousOne() throws BuggynessException {
        BuggynessLabeller labeller = labeller(defect("PROJ-1", 2));

        MetricsReport report = new MetricsReport();
        report.forClass(HELD_AT, BROKEN);
        labeller.label(versions.get(1), report);
        labeller.label(versions.get(2), report);

        assertFalse(report.forPath(HELD_AT).isBuggy());
    }

    /**
     * A snapshot taken outside the release history is no release, and nothing says which defects were
     * present in it.
     */
    @Test
    public void testTheClassesOfNoVersionAreRejected() throws BuggynessException {
        BuggynessLabeller labeller = labeller(defect("PROJ-1", 2));

        assertThrows(BuggynessException.class, () -> labeller.label(null, new MetricsReport()));
    }

    /**
     * @param labeller the labeller under test
     * @param version  the release to label
     * @return whether the class the fix touched comes out buggy at that release
     */
    private static boolean isBuggy(BuggynessLabeller labeller, Version version) throws BuggynessException {
        MetricsReport report = new MetricsReport();
        report.forClass(HELD_AT, BROKEN);
        labeller.label(version, report);
        return report.forPath(HELD_AT).isBuggy();
    }

    private BuggynessLabeller labeller(Issue defect) throws BuggynessException {
        Map<Issue, List<Commit>> fixes = new HashMap<>();
        fixes.put(defect, List.of(fix));
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        return AffectedVersionsLabeller.reading(folder.getRoot().toPath(), versions, fixes, filter);
    }

    /**
     * @param key      the key of the defect
     * @param affected the indices of the releases it affected, as the Proportion leaves them
     * @return a defect report affecting those releases
     */
    private Issue defect(String key, int... affected) {
        Issue issue = new Issue(key, LocalDateTime.now(ZONE_ID), LocalDateTime.now(ZONE_ID), key, IssueType.BUG,
                "assignee", ResolutionType.FIXED, key, IssueStatus.CLOSED);
        List<Version> affectedVersions = new ArrayList<>();
        for (int index : affected) {
            affectedVersions.add(versions.get(index - 1));
        }
        issue.setAffectedVersions(affectedVersions);
        return issue;
    }

    private static List<Version> releases(int count) {
        List<Version> releases = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            releases.add(new Version("id" + i, i + ".0", true, false));
        }
        Version.numberVersions(releases);
        return releases;
    }

    private static void write(Path repoPath, String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String commit(Git git, String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
        return git.commit()
                .setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com")
                .setSign(false)
                .call()
                .getName();
    }

    private static Commit commitOf(String id) {
        return new Commit(id, "message", "message", "Alice", "alice@example.com", ZonedDateTime.now(ZONE_ID),
                new ArrayList<>());
    }
}
