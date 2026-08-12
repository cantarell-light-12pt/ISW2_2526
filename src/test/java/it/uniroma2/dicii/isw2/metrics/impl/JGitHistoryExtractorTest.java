package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.Metric;
import it.uniroma2.dicii.isw2.metrics.MetricsExtractor;
import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.model.MetricsReport;
import it.uniroma2.dicii.isw2.metrics.model.Snapshot;
import it.uniroma2.dicii.isw2.repo.impl.GitRepoManager;
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
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Measures the evolution metrics over a repository whose whole history is known, so that every count
 * the extractor reports can be checked against the commits that produced it.
 * <p>
 * The history holds two releases and three classes: one written once and never touched again, one
 * changed in both releases — once by a bug fix — and one appearing only in the second release.
 * Together they cover the two flavours the metrics come in, the ones about the release being measured
 * and the ones about the whole life of a class.
 */
public class JGitHistoryExtractorTest {

    private static final String STABLE = "src/main/java/app/Stable.java";
    private static final String CHURNY = "src/main/java/app/Churny.java";
    private static final String FRESH = "src/main/java/app/Fresh.java";
    private static final String EXERCISING = "src/test/java/app/StableTest.java";

    /**
     * Three lines each, and every later revision only appends to or trims the end of the file, so that
     * the lines a commit is worth do not depend on how the diff algorithm lines the two sides up.
     */
    private static final String STABLE_SOURCE = "package app;\nclass Stable {\n}\n";
    private static final String FRESH_SOURCE = "package app;\nclass Fresh {\n}\n";
    private static final String CHURNY_WRITTEN = "package app;\nclass Churny {\n}\n";
    private static final String CHURNY_FIXED = CHURNY_WRITTEN + "// fix one\n// fix two\n";
    private static final String CHURNY_TRIMMED = CHURNY_WRITTEN + "// fix one\n";

    private static final double DELTA = 1e-9;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path repoPath;
    private JGitHistoryExtractor extractor;
    private Version first;
    private Version second;

    private static double metric(MetricsReport report, String path, Metric metric) {
        return report.forPath(path).get(metric).orElseThrow();
    }

    private static Version version(String id, String name, String commitId, int index) {
        Version version = new Version(id, name, true, false);
        version.setCommitId(commitId);
        version.setIndex(index);
        return version;
    }

    private static String commit(Git git, String message, String author, String email) throws GitAPIException {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).setAuthor(author, email).setCommitter(author, email).setSign(false).call().getName();
    }

    @Before
    public void setUp() throws Exception {
        repoPath = folder.getRoot().toPath();
        String fix;
        String trim;

        try (Git git = Git.init().setDirectory(folder.getRoot()).call()) {
            write(STABLE, STABLE_SOURCE);
            write(CHURNY, CHURNY_WRITTEN);
            write(EXERCISING, "package app;\nclass StableTest {\n}\n");
            commit(git, "Write the classes", "Alice", "alice@example.com");

            write(CHURNY, CHURNY_FIXED);
            fix = commit(git, "APP-1 Fix the churny class", "Bob", "bob@example.com");

            write(CHURNY, CHURNY_TRIMMED);
            write(FRESH, FRESH_SOURCE);
            trim = commit(git, "Trim it down and add a class", "Alice", "alice@example.com");
        }

        first = version("1", "1.0.0", fix, 1);
        second = version("2", "2.0.0", trim, 2);
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        // Only the commit fixing APP-1 is one of the commits the association with Jira reported
        extractor = new JGitHistoryExtractor(repoPath, List.of(first, second), Set.of(fix), filter);
    }

    /**
     * At the first release every class is as old as the project, and everything it has ever taken it
     * took in that release: its churn is its whole history.
     */
    @Test
    public void testTheFirstReleaseMeasuresTheClassesWrittenForIt() throws MetricsException {
        MetricsReport report = measure(first);

        assertEquals("Three lines written, none removed", 3, metric(report, STABLE, Metric.CH), DELTA);
        assertEquals(3, metric(report, STABLE, Metric.MCH), DELTA);
        assertEquals(3, metric(report, STABLE, Metric.CIS), DELTA);
        assertEquals(1, metric(report, STABLE, Metric.NR), DELTA);
        assertEquals(1, metric(report, STABLE, Metric.NDA), DELTA);
        assertEquals(1, metric(report, STABLE, Metric.AGE), DELTA);
        assertEquals(0, metric(report, STABLE, Metric.NLBF), DELTA);
        assertEquals(0, metric(report, STABLE, Metric.WNBF), DELTA);

        assertEquals("Three lines written and two appended by the fix", 5, metric(report, CHURNY, Metric.CH), DELTA);
        assertEquals(5, metric(report, CHURNY, Metric.CIS), DELTA);
        assertEquals("Written by Alice, fixed by Bob", 2, metric(report, CHURNY, Metric.NDA), DELTA);
        assertEquals(2, metric(report, CHURNY, Metric.NR), DELTA);
        assertEquals(1, metric(report, CHURNY, Metric.NLBF), DELTA);
        assertEquals("One fix over one release", 1, metric(report, CHURNY, Metric.WNBF), DELTA);
    }

    /**
     * At the second release the two flavours part ways: the churn and the latest bug fixes fall back
     * to what the release alone brought, while the revisions, the authors and the age keep the whole
     * history of the class.
     */
    @Test
    public void testTheSecondReleaseTellsTheReleaseFromTheWholeHistory() throws Exception {
        // The first release is measured too, so that the history is read once and reused, as it is
        // when the workflow walks the releases from the oldest to the newest
        measure(first);
        MetricsReport report = measure(second);

        assertEquals("Untouched by this release", 0, metric(report, STABLE, Metric.CH), DELTA);
        assertEquals("It still took three lines back when it was written", 3, metric(report, STABLE, Metric.MCH), DELTA);
        assertEquals(3, metric(report, STABLE, Metric.CIS), DELTA);
        assertEquals(1, metric(report, STABLE, Metric.NR), DELTA);
        assertEquals("It has been in the project for both releases", 2, metric(report, STABLE, Metric.AGE), DELTA);

        assertEquals("A single line removed", 1, metric(report, CHURNY, Metric.CH), DELTA);
        assertEquals("The first release was the heavier one", 5, metric(report, CHURNY, Metric.MCH), DELTA);
        assertEquals("Five lines written, one of them taken back", 4, metric(report, CHURNY, Metric.CIS), DELTA);
        assertEquals(3, metric(report, CHURNY, Metric.NR), DELTA);
        assertEquals(2, metric(report, CHURNY, Metric.NDA), DELTA);
        assertEquals(2, metric(report, CHURNY, Metric.AGE), DELTA);
        assertEquals("The fix belongs to the previous release", 0, metric(report, CHURNY, Metric.NLBF), DELTA);
        assertEquals("One fix over two releases", 0.5, metric(report, CHURNY, Metric.WNBF), DELTA);

        assertEquals(3, metric(report, FRESH, Metric.CH), DELTA);
        assertEquals(3, metric(report, FRESH, Metric.CIS), DELTA);
        assertEquals(1, metric(report, FRESH, Metric.NR), DELTA);
        assertEquals("Written for this release, it is as old as it", 1, metric(report, FRESH, Metric.AGE), DELTA);
    }

    /**
     * The rows of the dataset are the ones the sources of the snapshot hold, which is what lets the
     * composite check that every extractor described the same classes.
     */
    @Test
    public void testOnlyTheClassesOfTheSnapshotAreMeasured() throws Exception {
        MetricsReport report = measure(first);

        assertEquals(2, report.size());
        assertNull("The class does not exist yet at the first release", report.forPath(FRESH));
        assertNull("A class exercising another one is no row of the dataset", report.forPath(EXERCISING));
    }

    @Test
    public void testEveryDeclaredMetricIsMeasured() throws Exception {
        MetricsReport report = measure(second);

        assertEquals("No class should be missing a metric the extractor declares it measures", 0, report.incompleteClasses(extractor.extractedMetrics()).size());
    }

    /**
     * The whole composite, as the workflow assembles it: the three extractors read the snapshot
     * through three unrelated tools and have to come out describing the same classes, which is what
     * the checkpoint closing a version reports on.
     */
    @Test
    public void testTheThreeExtractorsAgreeOnTheClassesOfASnapshot() throws Exception {
        SourceFilter filter = new PathSourceFilter(".git,test,target", "*Test.java");
        MetricsExtractor composite = new CompositeMetricsExtractor().add(new CKExtractor(filter)).add(new JavaParserExtractor(filter)).add(extractor);
        new GitRepoManager().checkoutAtCommit(repoPath, second.getCommitId());

        MetricsReport report = composite.extract(new Snapshot(repoPath, second));

        assertEquals(3, report.size());
        assertTrue("The three extractors disagreed on the classes of the snapshot", report.incompleteClasses(composite.extractedMetrics()).isEmpty());
        assertEquals("Every metric of the catalogue but the code smells", 20, composite.extractedMetrics().size());
    }

    /**
     * A directory the release history says nothing about is no snapshot of a released version, and
     * neither is one that does not exist: measuring either would silently produce a slice of the
     * dataset describing nothing.
     */
    @Test
    public void testASnapshotWithNoReleaseIsRejected() {
        assertThrows(MetricsException.class, () -> extractor.extract(new Snapshot(repoPath)));
        assertThrows(MetricsException.class, () -> extractor.extract(new Snapshot(null, first)));
        assertThrows(MetricsException.class, () -> extractor.extract(new Snapshot(repoPath.resolve("absent"), first)));
    }

    /**
     * Brings the working tree to the release and measures it, as the workflow does.
     *
     * @param version the release to measure
     * @return the evolution metrics of its classes
     */
    private MetricsReport measure(Version version) throws MetricsException {
        new GitRepoManager().checkoutAtCommit(repoPath, version.getCommitId());
        return extractor.extract(new Snapshot(repoPath, version));
    }

    private void write(String path, String content) throws IOException {
        Path file = repoPath.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
