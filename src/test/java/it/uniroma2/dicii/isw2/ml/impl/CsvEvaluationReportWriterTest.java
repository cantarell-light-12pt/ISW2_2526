package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the report is the file a reader expects, and that it survives a run that does not finish.
 */
public class CsvEvaluationReportWriterTest {

    private static final List<String> COLUMNS = List.of("Classifier", "Phase", "Folds", "Accuracy",
            "Precision", "Recall", "F1", "AUC", "Kappa", "ElapsedSeconds");

    @Rule
    public TemporaryFolder output = new TemporaryFolder();

    private Path file;
    private Locale defaultLocale;

    @Before
    public void setUp() {
        file = output.getRoot().toPath().resolve("reports").resolve("PROJECT-evaluation.csv");
        defaultLocale = Locale.getDefault();
    }

    /**
     * Restores the locale one of the tests changes, so that it cannot leak into the rest of the suite.
     */
    @After
    public void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void testTheHeaderNamesEveryMeasureAndTheDirectoryIsCreated() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            assertTrue("The directory meant to hold the report was not created", Files.exists(file));
        }
        assertEquals(List.of(String.join(",", COLUMNS)), lines());
    }

    @Test
    public void testAModelIsWrittenAsOneRowOfItsScores() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.8532, 0.4211, 0.3125, 0.3587, 0.7419, 0.2604));
        }
        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertEquals("RandomForest,validation,10,0.8532,0.4211,0.3125,0.3587,0.7419,0.2604,12",
                lines.get(1));
    }

    /**
     * The inference phase cuts no folds, and a zero in that cell would read as a validation over none,
     * which is a different thing from not having been one.
     */
    @Test
    public void testTheInferencePhaseIsWrittenWithNoFoldCount() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(new EvaluationOutcome(ClassifierKind.IBK, EvaluationPhase.TEST, 0, 0.71,
                    0.32, 0.28, 0.2989, 0.6104, 0.1183, Duration.ofSeconds(3)));
        }
        String[] fields = lines().get(1).split(",", -1);
        assertEquals(COLUMNS.size(), fields.length);
        assertEquals("test", fields[COLUMNS.indexOf("Phase")]);
        assertEquals("", fields[COLUMNS.indexOf("Folds")]);
        assertEquals("0.71", fields[COLUMNS.indexOf("Accuracy")]);
    }

    /**
     * A model already measured is worth keeping when a later one fails, so its row is on disk before
     * the report is closed — which a run that dies never gets to do.
     */
    @Test
    public void testARowIsDurableBeforeTheReportIsClosed() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.8532, 0.4211, 0.3125, 0.3587, 0.7419, 0.2604));
            assertEquals(2, lines().size());
        }
    }

    /**
     * A measure of the buggy class comes to {@code NaN} when a model never predicted it, which is not a
     * score and must not read as one.
     */
    @Test
    public void testAMeasureThatIsNoNumberIsLeftBlank() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.8532, Double.NaN, 0.0, Double.NaN, Double.NaN, 0.0));
        }
        String[] fields = lines().get(1).split(",", -1);
        assertEquals(COLUMNS.size(), fields.length);
        assertEquals("", fields[COLUMNS.indexOf("Precision")]);
        assertEquals("", fields[COLUMNS.indexOf("F1")]);
        assertEquals("", fields[COLUMNS.indexOf("AUC")]);
        assertEquals("0", fields[COLUMNS.indexOf("Recall")]);
    }

    /**
     * On a machine whose decimal separator is a comma, a score formatted through the default locale
     * would split its row in two and the file would no longer be the table it claims to be.
     */
    @Test
    public void testTheScoresDoNotDependOnTheDefaultLocale() throws MlException, IOException {
        Locale.setDefault(Locale.ITALY);
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.8532, 0.4211, 0.3125, 0.3587, 0.7419, 0.2604));
        }
        String[] fields = lines().get(1).split(",", -1);
        assertEquals(COLUMNS.size(), fields.length);
        assertEquals("0.8532", fields[COLUMNS.indexOf("Accuracy")]);
    }

    /**
     * Two runs interleaved would describe an evaluation nobody performed.
     */
    @Test
    public void testASecondRunReplacesTheReportOfTheFirst() throws MlException, IOException {
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.8532, 0.4211, 0.3125, 0.3587, 0.7419, 0.2604));
        }
        try (CsvEvaluationReportWriter report = CsvEvaluationReportWriter.open(file)) {
            report.write(outcome(0.9, 0.5, 0.4, 0.44, 0.8, 0.3));
        }
        List<String> lines = lines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("RandomForest,validation,10,0.9,"));
    }

    /**
     * @return how one model scored, with the figures the caller wants to read back
     */
    private static EvaluationOutcome outcome(double accuracy, double precision, double recall,
                                             double fMeasure, double areaUnderRoc, double kappa) {
        return new EvaluationOutcome(ClassifierKind.RANDOM_FOREST, EvaluationPhase.VALIDATION, 10,
                accuracy, precision, recall, fMeasure, areaUnderRoc, kappa, Duration.ofSeconds(12));
    }

    /**
     * @return the report as it stands, one string per line
     * @throws IOException if it cannot be read back
     */
    private List<String> lines() throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

}
