package it.uniroma2.dicii.isw2.metrics.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

/**
 * Reads the Java version out of the build files a release could ship, since a project old enough to be
 * worth mining was built by more than one tool over its life.
 */
public class JavaVersionDetectorTest {

    private static final String DEFAULT_VERSION = "8";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private JavaVersionDetector detector;

    private Path root;

    @Before
    public void setUp() {
        detector = new JavaVersionDetector(DEFAULT_VERSION);
        root = folder.getRoot().toPath();
    }

    @Test
    public void testReadsTheSourceLevelOfAMavenBuild() throws IOException {
        writeBuildFile("pom.xml", """
                <project>
                    <properties>
                        <maven.compiler.source>1.8</maven.compiler.source>
                        <maven.compiler.target>1.8</maven.compiler.target>
                    </properties>
                </project>
                """);
        assertEquals("java-8", detector.detect(root));
    }

    /**
     * A build stating both is compiled against the release it targets, so that is the one to parse it as.
     */
    @Test
    public void testTheTargetedReleaseWinsOverTheSourceLevel() throws IOException {
        writeBuildFile("pom.xml", """
                <project>
                    <properties>
                        <maven.compiler.release>11</maven.compiler.release>
                        <maven.compiler.source>8</maven.compiler.source>
                    </properties>
                </project>
                """);
        assertEquals("java-11", detector.detect(root));
    }

    @Test
    public void testReadsTheSourceLevelConfiguredOnTheCompilerPlugin() throws IOException {
        writeBuildFile("pom.xml", """
                <project>
                    <build><plugins><plugin>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <configuration>
                            <source>1.6</source>
                            <target>1.6</target>
                        </configuration>
                    </plugin></plugins></build>
                </project>
                """);
        assertEquals("java-6", detector.detect(root));
    }

    @Test
    public void testReadsTheSourceLevelOfAnAntBuild() throws IOException {
        writeBuildFile("build.xml", """
                <project name="sample" default="build">
                    <property name="javac.source" value="1.5" />
                    <target name="build">
                        <javac srcdir="src" destdir="build" source="1.5" target="1.5" />
                    </target>
                </project>
                """);
        assertEquals("java-5", detector.detect(root));
    }

    @Test
    public void testReadsTheSourceLevelOfAJavacTaskAlone() throws IOException {
        writeBuildFile("build.xml", """
                <project name="sample" default="build">
                    <target name="build">
                        <javac srcdir="src" destdir="build" source="1.7" target="1.7" />
                    </target>
                </project>
                """);
        assertEquals("java-7", detector.detect(root));
    }

    /**
     * The oldest Ant builds of the project being mined pin the bytecode level alone and let the javac
     * tasks read it from a property: falling back on it is the only way those releases are parsed as
     * anything but the configured default.
     */
    @Test
    public void testFallsBackOnTheBytecodeLevelOfAnAntBuild() throws IOException {
        writeBuildFile("build.xml", """
                <project name="sample" default="build">
                    <property name="javac.target" value="1.5" />
                    <target name="build">
                        <javac srcdir="src" destdir="build" target="${javac.target}" debug="on" />
                    </target>
                </project>
                """);
        assertEquals("java-5", detector.detect(root));
    }

    /**
     * The source level is the more telling of the two, wherever the bytecode level is also stated.
     */
    @Test
    public void testTheSourceLevelWinsOverTheBytecodeLevelOfAnAntBuild() throws IOException {
        writeBuildFile("build.xml", """
                <project name="sample" default="build">
                    <property name="javac.target" value="1.4" />
                    <property name="javac.source" value="1.6" />
                </project>
                """);
        assertEquals("java-6", detector.detect(root));
    }

    @Test
    public void testReadsTheSourceLevelOfABuildPropertiesFile() throws IOException {
        writeBuildFile("build.properties", """
                build.dir=build
                javac.source=1.6
                javac.target=1.6
                """);
        assertEquals("java-6", detector.detect(root));
    }

    /**
     * A Maven build is looked up before an Ant one, since a release shipping both was built by Maven and
     * only kept the older build file around.
     */
    @Test
    public void testTheMavenBuildIsPreferredToTheAntOne() throws IOException {
        writeBuildFile("pom.xml", "<project><properties>"
                + "<maven.compiler.source>8</maven.compiler.source></properties></project>");
        writeBuildFile("build.xml", "<project><property name=\"javac.source\" value=\"1.5\" /></project>");
        assertEquals("java-8", detector.detect(root));
    }

    @Test
    public void testFallsBackOnTheConfiguredVersionWhenNoBuildFileSaysAnything() {
        assertEquals("java-8", detector.detect(root));
    }

    @Test
    public void testFallsBackOnTheConfiguredVersionWhenTheBuildFileDoesNotSay() throws IOException {
        writeBuildFile("pom.xml", "<project><artifactId>sample</artifactId></project>");
        assertEquals("java-8", detector.detect(root));
    }

    /**
     * PMD only knows the two oldest releases of the language by their {@code 1.x} name, and every later
     * one by its number alone.
     */
    @Test
    public void testTheOldestReleasesKeepTheirLegacyName() throws IOException {
        writeBuildFile("pom.xml", "<project><properties>"
                + "<maven.compiler.source>1.4</maven.compiler.source></properties></project>");
        assertEquals("java-1.4", detector.detect(root));
    }

    @Test
    public void testAVersionThatIsNoNumberFallsBackOnTheConfiguredOne() throws IOException {
        writeBuildFile("pom.xml", "<project><properties>"
                + "<maven.compiler.source>${jdk.version}</maven.compiler.source></properties></project>");
        assertEquals("java-8", detector.detect(root));
    }

    /**
     * A detector configured with a version spelled the old way still has to hand PMD a name it answers to.
     */
    @Test
    public void testTheConfiguredVersionIsSpelledTheWayPmdNamesIt() {
        assertEquals("java-6", new JavaVersionDetector("1.6").detect(root));
    }

    private void writeBuildFile(String name, String contents) throws IOException {
        Files.writeString(root.resolve(name), contents);
    }
}
