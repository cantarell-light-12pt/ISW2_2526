package it.uniroma2.dicii.isw2.metrics;

import it.uniroma2.dicii.isw2.metrics.exception.MetricsException;
import it.uniroma2.dicii.isw2.metrics.impl.PathSourceFilter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks the enumeration every extractor of the composite is driven by, on a snapshot laid out the way
 * the project being mined is: a main source tree, a test one beside it, and a file that is not Java.
 */
public class SourceScannerTest {

    private static final String SOURCE = """
            package sample;

            public class Sample {
            }
            """;

    @Rule
    public TemporaryFolder sources = new TemporaryFolder();

    private Path root;

    @Before
    public void setUp() throws IOException {
        Path main = sources.newFolder("src", "main", "java", "sample").toPath();
        Files.writeString(main.resolve("Client.java"), SOURCE);
        Files.writeString(main.resolve("Server.java"), SOURCE);
        Files.writeString(main.resolve("notes.txt"), "not a source");
        Path test = sources.newFolder("src", "test", "java", "sample").toPath();
        Files.writeString(test.resolve("ClientTest.java"), SOURCE);
        root = sources.getRoot().toPath().toAbsolutePath().normalize();
    }

    @Test
    public void testOnlyTheAcceptedJavaSourcesAreScanned() throws MetricsException {
        List<Path> scanned = SourceScanner.scan(root, new PathSourceFilter("test", ""));

        assertEquals(List.of(
                        root.resolve("src/main/java/sample/Client.java"),
                        root.resolve("src/main/java/sample/Server.java")),
                scanned);
    }

    @Test
    public void testWithoutAFilterEverySourceIsScanned() throws MetricsException {
        List<Path> scanned = SourceScanner.scan(root, SourceFilter.everything());

        assertEquals(3, scanned.size());
        assertTrue(scanned.contains(root.resolve("src/test/java/sample/ClientTest.java")));
    }

    /**
     * Two runs over the same snapshot have to process its sources in the same order, or the reports
     * they produce cannot be compared with one another.
     */
    @Test
    public void testSourcesAreScannedInAStableOrder() throws MetricsException {
        List<Path> first = SourceScanner.scan(root, SourceFilter.everything());
        List<Path> second = SourceScanner.scan(root, SourceFilter.everything());

        assertEquals(first, second);
        assertEquals(first.stream().sorted().toList(), first);
    }

    @Test
    public void testMissingDirectoryIsReported() {
        assertThrows(MetricsException.class,
                () -> SourceScanner.scan(root.resolve("absent"), SourceFilter.everything()));
    }
}
