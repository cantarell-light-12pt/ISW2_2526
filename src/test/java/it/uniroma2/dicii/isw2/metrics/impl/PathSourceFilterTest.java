package it.uniroma2.dicii.isw2.metrics.impl;

import it.uniroma2.dicii.isw2.metrics.SourceFilter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks the rule telling the functional code of a snapshot from the rest of the Java found in it,
 * against the layouts the project being mined actually went through: the {@code src/java/test} of its
 * Ant era, the {@code src/test/java} of its Maven one, and the modules each of them was split into.
 */
public class PathSourceFilterTest {

    private static final String EXCLUDED_DIRECTORIES = ".git,test,tests,target,build,generated";

    private static final String EXCLUDED_FILES =
            "package-info.java,module-info.java,Info.java,*Test.java,*Tests.java,*TestCase.java,*IT.java";

    private static final String MAIN_CLASS = "zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java";

    private SourceFilter filter;

    @Before
    public void setUp() {
        filter = new PathSourceFilter(EXCLUDED_DIRECTORIES, EXCLUDED_FILES);
    }

    @Test
    public void testFunctionalCodeIsARowOfTheDataset() {
        assertTrue(filter.accepts(MAIN_CLASS));
        assertTrue(filter.accepts("zookeeper-jute/src/main/java/org/apache/jute/Record.java"));
        assertTrue(filter.accepts("Standalone.java"));
    }

    @Test
    public void testTestSourcesAreLeftOutWhicheverLayoutTheyAreIn() {
        assertFalse(filter.accepts("zookeeper-server/src/test/java/org/apache/zookeeper/ClientTest.java"));
        assertFalse(filter.accepts("src/java/test/org/apache/zookeeper/test/AsyncTest.java"));
        assertFalse(filter.accepts("zookeeper-it/src/test/java/org/apache/zookeeper/Fixture.java"));
    }

    @Test
    public void testBuiltAndGeneratedSourcesAreLeftOut() {
        assertFalse(filter.accepts("target/generated-sources/org/apache/zookeeper/Stub.java"));
        assertFalse(filter.accepts("build/classes/org/apache/zookeeper/Stub.java"));
        assertFalse(filter.accepts("src/java/generated/org/apache/jute/Index.java"));
        assertFalse(filter.accepts(".git/modules/hooks/Sample.java"));
    }

    /**
     * The version stamp some releases carry declares nothing but constants written by the build, and
     * the two {@code *-info} files declare no class at all.
     */
    @Test
    public void testNonFunctionalFilesAreLeftOut() {
        assertFalse(filter.accepts("src/java/main/org/apache/zookeeper/version/Info.java"));
        assertFalse(filter.accepts("zookeeper-server/src/main/java/org/apache/zookeeper/package-info.java"));
        assertFalse(filter.accepts("zookeeper-server/src/main/java/module-info.java"));
    }

    /**
     * A test class that ended up outside a test directory is still no row of the dataset, which is what
     * the name patterns are for. They are matched against the name of the file alone, so that a
     * production class whose name merely starts with {@code Test} survives.
     */
    @Test
    public void testTestClassesAreToldApartFromProductionClassesNamedAfterThem() {
        assertFalse(filter.accepts("src/java/main/org/apache/zookeeper/ZooKeeperTest.java"));
        assertFalse(filter.accepts("src/java/main/org/apache/zookeeper/QuorumBaseTestCase.java"));
        assertFalse(filter.accepts("src/java/main/org/apache/zookeeper/ReadOnlyModeIT.java"));
        assertTrue(filter.accepts("zookeeper-server/src/main/java/org/apache/zookeeper/TestableZooKeeper.java"));
        assertTrue(filter.accepts("zookeeper-server/src/main/java/org/apache/zookeeper/Testable.java"));
    }

    /**
     * The rules are properties, so they have to survive a project configured with fewer of them, or
     * with none at all: an unconfigured rule excludes nothing rather than everything.
     */
    @Test
    public void testUnconfiguredRulesExcludeNothing() {
        assertTrue(new PathSourceFilter(null, null).accepts("src/test/java/ClientTest.java"));
        assertTrue(new PathSourceFilter("", "   ").accepts("src/test/java/ClientTest.java"));
    }

    @Test
    public void testBlankEntriesOfARuleAreIgnored() {
        SourceFilter sparse = new PathSourceFilter(" test , , ", " Info.java ,,");

        assertFalse(sparse.accepts("src/test/java/Client.java"));
        assertFalse(sparse.accepts("src/main/java/version/Info.java"));
        assertTrue(sparse.accepts(MAIN_CLASS));
    }

    /**
     * The directories rule is about the directories a file is nested in, so a source named after one of
     * them is not dropped by it.
     */
    @Test
    public void testDirectoryRuleDoesNotApplyToTheFileItself() {
        assertTrue(filter.accepts("zookeeper-server/src/main/java/org/apache/zookeeper/build.java"));
    }
}
