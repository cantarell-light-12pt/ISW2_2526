package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetSaver;
import it.uniroma2.dicii.isw2.dataset.exception.DatasetException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import weka.core.Instances;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks that a dataset written out of instances reads back as the dataset it was read from: every
 * column kept, a missing measure still missing, and the label still the nominal class it was, with the
 * buggy rows still the ones written {@code 1}.
 */
public class WekaCsvDatasetSaverTest {

    private static final String HEADER = "Version,ClassName,CBO,LOC,Buggy\n";

    /**
     * The first row is buggy, so that the label's values are introduced as {@code {1, 0}} — the opposite
     * order to the one the rest of the dataset tests see, and the order a round trip must not upset.
     */
    private static final String ROWS = HEADER
            + "3.0.0,sample.First,3,97,1\n"
            + "3.0.0,sample.Second,,5,0\n"
            + "3.1.0,sample.First,4,120,0\n";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private WekaCsvDatasetReader reader;
    private DatasetSaver saver;

    @Before
    public void setUp() {
        reader = new WekaCsvDatasetReader();
        saver = new WekaCsvDatasetSaver();
    }

    @Test
    public void testEveryColumnAndRowIsWrittenBack() throws Exception {
        Instances original = reader.readRetainingIdentity(write(ROWS));
        Instances saved = roundTrip(original);

        assertEquals(original.numAttributes(), saved.numAttributes());
        for (int i = 0; i < original.numAttributes(); i++) {
            assertEquals(original.attribute(i).name(), saved.attribute(i).name());
        }
        assertEquals(original.numInstances(), saved.numInstances());
        for (int i = 0; i < original.numInstances(); i++) {
            assertEquals(original.instance(i).stringValue(0), saved.instance(i).stringValue(0));
            assertEquals(original.instance(i).stringValue(1), saved.instance(i).stringValue(1));
            assertEquals(original.instance(i).value(3), saved.instance(i).value(3), 0.0);
        }
    }

    /**
     * A measure that was never taken is written empty, as the extraction writes it, and not as the
     * {@code ?} Weka would write — which reads back as a missing value all the same, and as nothing at
     * all to any other reader of the file.
     */
    @Test
    public void testAMissingMeasureIsWrittenEmptyAndReadBackMissing() throws Exception {
        Instances original = reader.readRetainingIdentity(write(ROWS));
        Path file = folder.getRoot().toPath().resolve("saved.csv");
        saver.save(original, file);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse("A missing value was written as Weka writes it", content.contains("?"));
        assertTrue(reader.readRetainingIdentity(file).instance(1).isMissing(2));
    }

    /**
     * The label comes back as the nominal class it was, and the rows that were buggy are still the
     * ones it calls {@code 1}, whichever position that value holds.
     */
    @Test
    public void testTheLabelIsStillTheNominalClassWithTheSameBuggyRows() throws Exception {
        Instances saved = roundTrip(reader.readRetainingIdentity(write(ROWS)));

        assertEquals("Buggy", saved.classAttribute().name());
        assertTrue(saved.classAttribute().isNominal());
        List<String> labels = List.of(saved.instance(0).stringValue(saved.classIndex()),
                saved.instance(1).stringValue(saved.classIndex()),
                saved.instance(2).stringValue(saved.classIndex()));
        assertEquals(List.of("1", "0", "0"), labels);
    }

    @Test
    public void testAFileAlreadyThereIsReplaced() throws Exception {
        Path file = write("stale content\n");
        saver.save(reader.readRetainingIdentity(write(ROWS)), file);
        assertEquals(3, reader.readRetainingIdentity(file).numInstances());
    }

    @Test
    public void testTheDirectoryOfTheFileIsCreated() throws Exception {
        Path file = folder.getRoot().toPath().resolve("missing").resolve("saved.csv");
        saver.save(reader.readRetainingIdentity(write(ROWS)), file);
        assertTrue(Files.isReadable(file));
    }

    /**
     * @param data the rows to write and read back
     * @return them, as they read back
     */
    private Instances roundTrip(Instances data) throws DatasetException {
        Path file = folder.getRoot().toPath().resolve("round-trip.csv");
        saver.save(data, file);
        return reader.readRetainingIdentity(file);
    }

    /**
     * @param content what the dataset file holds
     * @return where it was written
     */
    private Path write(String content) throws IOException {
        Path file = folder.newFile().toPath();
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

}
