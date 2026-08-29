package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelEvaluator;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import org.junit.Before;
import org.junit.Test;
import weka.classifiers.rules.ZeroR;
import weka.core.Instances;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that a model is really trained and tested over the folds, and that the figures come back off
 * the buggy class.
 */
public class CrossValidatingEvaluatorTest {

    private static final int ROWS = 60;

    /**
     * The shape of the real dataset: a fifth of the rows buggy, and nothing to tell them apart.
     */
    private static final int IMBALANCED_ROWS = 100;
    private static final int IMBALANCED_BUGGY_ROWS = 20;

    private static final double TOLERANCE = 1e-9;

    private Instances data;
    private MlSettings settings;
    private ModelEvaluator evaluator;

    @Before
    public void setUp() {
        data = SyntheticInstances.separable(ROWS);
        settings = SyntheticInstances.settings();
        evaluator = new CrossValidatingEvaluator(new WekaClassifierFactory(settings), settings);
    }

    /**
     * The label is readable off one of the attributes, so a model that was actually trained and tested
     * has to find it. A score around chance would mean the folds were being cut or read wrongly.
     */
    @Test
    public void testEveryMeasureIsOfAModelThatFoundTheBuggyRows() throws MlException {
        EvaluationOutcome outcome = evaluator.evaluate(data, ClassifierKind.NAIVE_BAYES);
        assertTrue("The buggy class went unfound", outcome.areaUnderRoc() > 0.9);
        assertTrue(outcome.accuracy() > 0.9);
        assertTrue(outcome.precision() > 0.9);
        assertTrue(outcome.recall() > 0.9);
        assertTrue(outcome.fMeasure() > 0.9);
        assertTrue("Kappa says the model did no better than guessing", outcome.kappa() > 0.9);
    }

    /**
     * The one case that tells the two classes apart. A model answering "not buggy" to every row scores
     * a recall of 1 on the not-buggy class and of 0 on the buggy one, so a per-class figure read off
     * the wrong index comes back as the opposite of what it should be — and comes back looking
     * excellent, which is how the mistake survives.
     */
    @Test
    public void testThePerClassFiguresAreReadOffTheBuggyClass() throws MlException {
        Instances imbalanced = SyntheticInstances.imbalanced(IMBALANCED_ROWS, IMBALANCED_BUGGY_ROWS);
        ModelFactory alwaysNotBuggy = kind -> new ZeroR();
        EvaluationOutcome outcome = new CrossValidatingEvaluator(alwaysNotBuggy, settings)
                .evaluate(imbalanced, ClassifierKind.NAIVE_BAYES);

        assertEquals("The recall reported is the one of the not-buggy class", 0.0, outcome.recall(), TOLERANCE);
        // F1 of a class nothing was predicted for is 2*0*0/(0+0), which is why the report writes a
        // blank where a model that found nothing would otherwise read as having scored a zero
        assertFalse("F1 of a class nothing was predicted for is no number",
                Double.isFinite(outcome.fMeasure()));
        assertEquals("A model finding nothing still scores the majority share",
                1 - (double) IMBALANCED_BUGGY_ROWS / IMBALANCED_ROWS, outcome.accuracy(), TOLERANCE);
        assertEquals("Kappa is what says that accuracy was worth nothing", 0.0, outcome.kappa(), TOLERANCE);
    }

    /**
     * That every prediction is made on a row the model was not trained on, which is the whole claim a
     * cross-validation makes and the one nothing in this class does by hand — Weka cuts the folds.
     * <p>
     * A single-neighbour classifier is what settles it. Its nearest neighbour to a row it was trained on
     * is that row itself, at distance zero, so it answers with the label it was given and scores exactly
     * 1 on any training set, however unlearnable. These rows are pure noise: an accuracy below 1 can
     * only mean it was asked about rows it had not been shown.
     */
    @Test
    public void testAModelIsOnlyEverTestedOnRowsItWasNotTrainedOn() throws MlException {
        Instances unlearnable = SyntheticInstances.imbalanced(IMBALANCED_ROWS, IMBALANCED_BUGGY_ROWS);
        MlSettings singleNeighbour = SyntheticInstances.settings(ClassifierKind.IBK, SyntheticInstances.BUGGY);
        EvaluationOutcome outcome =
                new CrossValidatingEvaluator(new WekaClassifierFactory(singleNeighbour), singleNeighbour)
                        .evaluate(unlearnable, ClassifierKind.IBK);

        assertTrue("Every row was its own nearest neighbour: the model was tested on its training set",
                outcome.accuracy() < 1.0);
        assertTrue("Noise was predicted better than noise can be", outcome.areaUnderRoc() < 0.9);
    }

    @Test
    public void testTheOutcomeNamesTheModelAndTheFoldsItWasMeasuredOver() throws MlException {
        EvaluationOutcome outcome = evaluator.evaluate(data, ClassifierKind.NAIVE_BAYES);
        assertEquals(ClassifierKind.NAIVE_BAYES, outcome.classifier());
        assertEquals(settings.folds(), outcome.folds());
    }

    @Test
    public void testEveryConfiguredModelCanBeMeasured() throws MlException {
        for (ClassifierKind kind : ClassifierKind.values()) {
            EvaluationOutcome outcome = evaluator.evaluate(data, kind);
            assertEquals(kind, outcome.classifier());
            assertTrue("The separable rows defeated " + kind.getLabel(), outcome.accuracy() > 0.9);
        }
    }

    /**
     * The seed is fixed so that a figure can be quoted, which only holds if the same rows and the same
     * seed cut the same folds twice.
     */
    @Test
    public void testTwoRunsOfTheSameModelAgree() throws MlException {
        EvaluationOutcome first = evaluator.evaluate(data, ClassifierKind.RANDOM_FOREST);
        EvaluationOutcome second = evaluator.evaluate(data, ClassifierKind.RANDOM_FOREST);
        assertEquals(first.accuracy(), second.accuracy(), TOLERANCE);
        assertEquals(first.precision(), second.precision(), TOLERANCE);
        assertEquals(first.recall(), second.recall(), TOLERANCE);
        assertEquals(first.fMeasure(), second.fMeasure(), TOLERANCE);
        assertEquals(first.areaUnderRoc(), second.areaUnderRoc(), TOLERANCE);
        assertEquals(first.kappa(), second.kappa(), TOLERANCE);
    }

    /**
     * The instances are shuffled to cut the folds, which would reorder the copy the caller holds — and
     * the caller measures every model on that same copy.
     */
    @Test
    public void testTheInstancesItWasGivenAreLeftAsTheyWere() throws MlException {
        Instances before = new Instances(data);
        evaluator.evaluate(data, ClassifierKind.NAIVE_BAYES);
        assertEquals(before.numInstances(), data.numInstances());
        for (int i = 0; i < before.numInstances(); i++) {
            assertEquals(before.instance(i).toString(), data.instance(i).toString());
        }
    }

    /**
     * A label that never takes the configured value leaves precision, recall, F1 and AUC measuring
     * nothing, which has to fail rather than report the figures of whichever class happens to be first.
     */
    @Test
    public void testALabelWithoutTheConfiguredBuggyClassCannotBeMeasured() {
        MlSettings misconfigured = SyntheticInstances.settings(ClassifierKind.NAIVE_BAYES, "yes");
        ModelEvaluator misreading =
                new CrossValidatingEvaluator(new WekaClassifierFactory(misconfigured), misconfigured);
        MlException thrown =
                assertThrows(MlException.class, () -> misreading.evaluate(data, ClassifierKind.NAIVE_BAYES));
        assertTrue(thrown.getMessage().contains("yes"));
    }

}
