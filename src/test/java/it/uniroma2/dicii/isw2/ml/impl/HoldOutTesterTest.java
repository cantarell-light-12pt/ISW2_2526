package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelTester;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import org.junit.Before;
import org.junit.Test;
import weka.core.Instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks the inference phase: that a model trained on one set of rows is measured on another, and that
 * the outcome says so.
 */
public class HoldOutTesterTest {

    private static final int TRAINING_ROWS = 60;
    private static final int TEST_ROWS = 40;

    private MlSettings settings;
    private ModelTester tester;

    @Before
    public void setUp() {
        settings = SyntheticInstances.settings();
        tester = new HoldOutTester(new WekaClassifierFactory(settings), settings);
    }

    /**
     * The label is readable off one of the attributes in both sets, so a model trained on the one and
     * asked about the other has to find it. A score around chance would mean it had not really been
     * trained, or had been asked about the wrong rows.
     */
    @Test
    public void testAModelTrainedOnOneSetPredictsTheOther() throws MlException {
        EvaluationOutcome outcome = tester.test(SyntheticInstances.separable(TRAINING_ROWS),
                SyntheticInstances.separable(TEST_ROWS), ClassifierKind.NAIVE_BAYES);

        assertTrue("The buggy class went unfound in the held-out rows", outcome.areaUnderRoc() > 0.9);
        assertTrue(outcome.accuracy() > 0.9);
        assertTrue(outcome.precision() > 0.9);
        assertTrue(outcome.recall() > 0.9);
        assertTrue(outcome.fMeasure() > 0.9);
        assertTrue(outcome.kappa() > 0.9);
    }

    /**
     * The report tells the two phases apart by this, and the inference phase cuts no folds — a zero
     * there is what makes the report write the cell blank rather than claim a validation over none.
     */
    @Test
    public void testTheOutcomeIsMarkedAsTheTestPhaseAndCutsNoFolds() throws MlException {
        EvaluationOutcome outcome = tester.test(SyntheticInstances.separable(TRAINING_ROWS),
                SyntheticInstances.separable(TEST_ROWS), ClassifierKind.NAIVE_BAYES);
        assertEquals(EvaluationPhase.TEST, outcome.phase());
        assertEquals(0, outcome.folds());
        assertEquals(ClassifierKind.NAIVE_BAYES, outcome.classifier());
    }

    /**
     * The figures have to be of the test rows and of nothing else. Measured on its training set instead,
     * a model that fits it would score differently — and better — than it does on rows it has not seen.
     */
    @Test
    public void testTheFiguresAreOfTheTestRowsAndNotOfTheTrainingOnes() throws MlException {
        Instances training = SyntheticInstances.separable(TRAINING_ROWS);
        Instances unlearnable = SyntheticInstances.imbalanced(TEST_ROWS, TEST_ROWS / 4);
        MlSettings singleNeighbour =
                SyntheticInstances.settings(ClassifierKind.IBK, SyntheticInstances.BUGGY);
        EvaluationOutcome outcome =
                new HoldOutTester(new WekaClassifierFactory(singleNeighbour), singleNeighbour)
                        .test(training, unlearnable, ClassifierKind.IBK);

        assertNotEquals("The model was measured on the rows it was trained on", 1.0,
                outcome.accuracy(), 1e-9);
        assertTrue("Noise was predicted better than noise can be", outcome.areaUnderRoc() < 0.9);
    }

    /**
     * Two halves of one split always describe the same columns. Two sets that do not are a mistake
     * upstream, and Weka would otherwise line their attributes up by position and answer nonsense.
     */
    @Test
    public void testTrainingAndTestRowsDescribingDifferentColumnsAreRefused() {
        Instances training = SyntheticInstances.separable(TRAINING_ROWS);
        Instances different = SyntheticInstances.separable(TEST_ROWS);
        different.deleteAttributeAt(0);
        assertThrows(MlException.class,
                () -> tester.test(training, different, ClassifierKind.NAIVE_BAYES));
    }

    @Test
    public void testEveryConfiguredModelCanBeTested() throws MlException {
        for (ClassifierKind kind : ClassifierKind.values()) {
            EvaluationOutcome outcome = tester.test(SyntheticInstances.separable(TRAINING_ROWS),
                    SyntheticInstances.separable(TEST_ROWS), kind);
            assertEquals(kind, outcome.classifier());
            assertTrue("The separable rows defeated " + kind.getLabel(), outcome.accuracy() > 0.9);
        }
    }

}
