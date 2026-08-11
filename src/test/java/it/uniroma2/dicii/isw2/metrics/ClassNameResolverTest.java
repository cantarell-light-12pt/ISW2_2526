package it.uniroma2.dicii.isw2.metrics;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 * Checks the rule every extractor names a class by, including the cases the sources of a real project
 * run into: the default package, and a file whose path is not the one the sources are measured under.
 */
public class ClassNameResolverTest {

    private static final Path ROOT = Path.of("/tmp/repos/PROJECT").toAbsolutePath().normalize();

    @Test
    public void testPathIsExpressedRelativeToTheRoot() {
        assertEquals("src/main/java/sample/Base.java",
                ClassNameResolver.relativePath(ROOT, ROOT.resolve("src/main/java/sample/Base.java")));
    }

    @Test
    public void testPathOutsideTheRootIsLeftAbsolute() {
        Path outside = Path.of("/elsewhere/Base.java").toAbsolutePath().normalize();
        assertEquals(outside.toString().replace('\\', '/'), ClassNameResolver.relativePath(ROOT, outside));
    }

    @Test
    public void testQualifiedNameIsThePackageFollowedByTheNameOfTheFile() {
        assertEquals("org.apache.zookeeper.ZooKeeper",
                ClassNameResolver.qualifiedName("org.apache.zookeeper", "src/main/java/org/apache/zookeeper/ZooKeeper.java"));
    }

    @Test
    public void testQualifiedNameOfTheDefaultPackageIsTheNameOfTheFileAlone() {
        assertEquals("Base", ClassNameResolver.qualifiedName("", "Base.java"));
    }

    @Test
    public void testPackageIsEverythingBeforeTheLastDot() {
        assertEquals("sample.nested", ClassNameResolver.packageOf("sample.nested.Base"));
        assertEquals("", ClassNameResolver.packageOf("Base"));
    }

    @Test
    public void testSimpleNameIsEverythingAfterTheLastDot() {
        assertEquals("Base", ClassNameResolver.simpleName("sample.nested.Base"));
        assertEquals("Base", ClassNameResolver.simpleName("Base"));
    }

    @Test
    public void testFileNameDropsTheDirectoriesAndTheExtension() {
        assertEquals("Base", ClassNameResolver.fileName("sample/Base.java"));
        assertEquals("Base", ClassNameResolver.fileName("Base"));
    }
}
