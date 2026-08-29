package it.uniroma2.dicii.isw2.dataset.impl;

import it.uniroma2.dicii.isw2.dataset.DatasetReader;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the dataset comes back as a classification problem: the columns naming a row gone, the
 * ones measuring it kept, and the label read as a class rather than as the number it is written as.
 */
public class WekaCsvDatasetReaderTest {

    private static final String HEADER = "Version,ClassName,CBO,LOC,Buggy\n";

    /**
     * How many metrics the header above describes a class by.
     */
    private static final int METRICS = 2;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private DatasetReader reader;

    @Before
    public void setUp() {
        reader = new WekaCsvDatasetReader();
    }

    @Test
    public void testTheColumnsNamingARowAreNoPartOfWhatDescribesIt() throws Exception {
        Instances data = reader.read(write(HEADER
                + "3.0.0,sample.First,3,97,0\n"
                + "3.0.0,sample.Second,0,5,1\n"));
        assertEquals(METRICS + 1, data.numAttributes());
        assertEquals("CBO", data.attribute(0).name());
        assertEquals("LOC", data.attribute(1).name());
        assertEquals(2, data.numInstances());
    }

    @Test
    public void testTheLabelIsTheClassAndIsNominal() throws Exception {
        Instances data = reader.read(write(HEADER
                + "3.0.0,sample.First,3,97,0\n"
                + "3.0.0,sample.Second,0,5,1\n"));
        assertEquals(data.numAttributes() - 1, data.classIndex());
        assertEquals("Buggy", data.classAttribute().name());
        assertTrue("The label has to be a class, not the number it is written as",
                data.classAttribute().isNominal());
        assertEquals(2, data.classAttribute().numValues());
    }

    /**
     * The values of a nominal attribute are ordered by where they first appear in the file, so which of
     * them is the buggy one is not something the dataset can be relied on to keep. Here the first row
     * is not buggy, which is what makes the buggy class the second one — and what would make any code
     * assuming it is the first read every figure off the wrong class.
     */
    @Test
    public void testTheBuggyClassIsFoundByItsLabelAndNotByItsPosition() throws Exception {
        Instances data = reader.read(write(HEADER
                + "3.0.0,sample.First,3,97,0\n"
                + "3.0.0,sample.Second,0,5,1\n"));
        assertEquals(1, data.classAttribute().indexOfValue("1"));
        assertNotEquals(0, data.classAttribute().indexOfValue("1"));
    }

    @Test
    public void testTheBuggyClassIsFirstWhenTheFirstRowIsBuggy() throws Exception {
        Instances data = reader.read(write(HEADER
                + "3.0.0,sample.First,3,97,1\n"
                + "3.0.0,sample.Second,0,5,0\n"));
        assertEquals(0, data.classAttribute().indexOfValue("1"));
    }

    @Test
    public void testAnUnmeasuredMetricIsMissingAndNotZero() throws Exception {
        Instances data = reader.read(write(HEADER
                + "3.0.0,sample.First,3,,0\n"
                + "3.0.0,sample.Second,0,5,1\n"));
        assertTrue("A metric nobody could measure is unknown, not zero",
                data.instance(0).isMissing(data.attribute("LOC")));
        assertEquals(5.0, data.instance(1).value(data.attribute("LOC")), 0.0);
    }

    @Test
    public void testADatasetHoldingNoRowLabelledBuggyIsRefused() throws IOException {
        Path file = write(HEADER
                + "3.0.0,sample.First,3,97,0\n"
                + "3.0.0,sample.Second,0,5,0\n");
        DatasetException failure = assertThrows(DatasetException.class, () -> reader.read(file));
        assertTrue(failure.getMessage().contains("no buggy class to predict"));
    }

    @Test
    public void testAFileWhoseColumnsHaveMovedIsRefusedRatherThanReadAsIfTheyHadNot()
            throws IOException {
        Path file = write("ClassName,Version,CBO,LOC,Buggy\n3.0.0,sample.First,3,97,1\n");
        DatasetException failure = assertThrows(DatasetException.class, () -> reader.read(file));
        assertTrue(failure.getMessage().contains("Version"));
    }

    @Test
    public void testAFileWhoseLastColumnIsNoLabelIsRefused() throws IOException {
        Path file = write("Version,ClassName,CBO,LOC\n3.0.0,sample.First,3,97\n");
        DatasetException failure = assertThrows(DatasetException.class, () -> reader.read(file));
        assertTrue(failure.getMessage().contains("Buggy"));
    }

    @Test
    public void testAMissingDatasetIsReportedAsSuchRatherThanAsAParsingFailure() {
        Path missing = folder.getRoot().toPath().resolve("nothing.csv");
        DatasetException failure = assertThrows(DatasetException.class, () -> reader.read(missing));
        assertTrue(failure.getMessage().contains("does not exist"));
    }

    /**
     * @param content the dataset to read
     * @return where it was written
     * @throws IOException if it cannot be written
     */
    private Path write(String content) throws IOException {
        Path file = folder.getRoot().toPath().resolve("PROJECT.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

}
