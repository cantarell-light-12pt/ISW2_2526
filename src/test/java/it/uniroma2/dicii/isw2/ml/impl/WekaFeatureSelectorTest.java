package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.FeatureSelector;
import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import org.junit.Before;
import org.junit.Test;
import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that each technique is the Weka evaluator and search it is documented as, that what it keeps
 * of a set of rows is what those rows give away, and that a selecting model travels the way a model has
 * to through a cross-validation.
 */
public class WekaFeatureSelectorTest {

    private static final int ROWS = 60;

    /**
     * How many attributes a selection by information gain keeps here: the one the label can be read
     * off, out of three.
     */
    private static final int KEPT_BY_GAIN = 1;

    private Instances data;
    private FeatureSelector selector;

    @Before
    public void setUp() {
        data = SyntheticInstances.separable(ROWS);
        MlSettings settings = new MlSettings(List.of(ClassifierKind.NAIVE_BAYES), List.of(BalancingKind.NONE),
                List.of(FeatureSelectionKind.values()), 0.67, 2, 1, SyntheticInstances.BUGGY, 1, 10, 1,
                KEPT_BY_GAIN);
        selector = new WekaFeatureSelector(settings);
    }

    /**
     * A model trained on every attribute is left as it was, so that its figures are those of the model
     * measured before selection existed, to the last digit.
     */
    @Test
    public void testKeepingEveryAttributeLeavesTheModelAsItWas() {
        Classifier model = new NaiveBayes();
        assertSame(model, selector.selecting(model, FeatureSelectionKind.NONE));
    }

    @Test
    public void testInformationGainRanksTheAttributesAndKeepsTheConfiguredNumber() {
        AttributeSelectedClassifier selecting = selecting(FeatureSelectionKind.INFO_GAIN);
        assertTrue(selecting.getEvaluator() instanceof InfoGainAttributeEval);
        assertTrue(selecting.getSearch() instanceof Ranker);
        assertEquals(KEPT_BY_GAIN, ((Ranker) selecting.getSearch()).getNumToSelect());
    }

    /**
     * The two searches walk the same subsets under the same judge, from opposite ends: telling them
     * apart is the direction and nothing else.
     */
    @Test
    public void testTheTwoSearchesWalkTheSubsetsFromOppositeEnds() {
        AttributeSelectedClassifier forward = selecting(FeatureSelectionKind.FORWARD_SEARCH);
        AttributeSelectedClassifier backward = selecting(FeatureSelectionKind.BACKWARD_SEARCH);
        for (AttributeSelectedClassifier search : List.of(forward, backward)) {
            assertTrue(search.getEvaluator() instanceof CfsSubsetEval);
            assertTrue(search.getSearch() instanceof GreedyStepwise);
        }
        assertFalse(((GreedyStepwise) forward.getSearch()).getSearchBackwards());
        assertTrue(((GreedyStepwise) backward.getSearch()).getSearchBackwards());
    }

    @Test
    public void testASelectingModelTrainsTheModelItWasGiven() {
        Classifier model = new NaiveBayes();
        AttributeSelectedClassifier selecting =
                (AttributeSelectedClassifier) selector.selecting(model, FeatureSelectionKind.INFO_GAIN);
        assertSame(model, selecting.getClassifier());
    }

    @Test
    public void testKeepingEveryAttributeKeepsAllButTheLabel() throws MlException {
        List<String> expected = new ArrayList<>();
        expected.add(SyntheticInstances.SIGNAL);
        expected.addAll(SyntheticInstances.NOISE);
        assertEquals(expected, selector.select(data, FeatureSelectionKind.NONE));
    }

    /**
     * One attribute gives the label away and the other two are noise, so every technique has to keep
     * the first — and none of them may report the label as an attribute it kept.
     */
    @Test
    public void testEveryTechniqueKeepsTheAttributeTheLabelCanBeReadOff() throws MlException {
        String label = data.classAttribute().name();
        for (FeatureSelectionKind kind : FeatureSelectionKind.values()) {
            List<String> kept = selector.select(data, kind);
            assertTrue(kind.getLabel() + " dropped the signal: " + kept, kept.contains(SyntheticInstances.SIGNAL));
            assertFalse(kind.getLabel() + " kept the label as an attribute", kept.contains(label));
        }
        assertEquals(List.of(SyntheticInstances.SIGNAL), selector.select(data, FeatureSelectionKind.INFO_GAIN));
    }

    /**
     * The caller holds the training rows the models are then measured over, which a selection writing
     * into them would change under every one of them.
     */
    @Test
    public void testTheRowsSelectedOutOfAreLeftAsTheyWere() throws MlException {
        Instances before = new Instances(data);
        for (FeatureSelectionKind kind : FeatureSelectionKind.values()) {
            selector.select(data, kind);
        }
        assertTrue(before.equalHeaders(data));
        assertEquals(before.numInstances(), data.numInstances());
        for (int i = 0; i < before.numInstances(); i++) {
            assertEquals(before.instance(i).toString(), data.instance(i).toString());
        }
    }

    @Test
    public void testRowsNamingNoLabelAreRefused() {
        Instances unlabelled = new Instances(data);
        unlabelled.setClassIndex(-1);
        assertThrows(MlException.class, () -> selector.select(unlabelled, FeatureSelectionKind.INFO_GAIN));
    }

    /**
     * Weka hands every fold of a cross-validation a copy of the model, made by serialising it, so a
     * selecting model has to come through that intact and trainable.
     */
    @Test
    public void testEverySelectingModelSurvivesBeingCopiedAndCanBeTrained() throws Exception {
        for (FeatureSelectionKind kind : FeatureSelectionKind.values()) {
            Classifier original = selector.selecting(new NaiveBayes(), kind);
            Classifier copy = AbstractClassifier.makeCopy(original);
            assertNotSame(original, copy);
            assertEquals(kind.getLabel() + " came back as something else", original.getClass(), copy.getClass());
            copy.buildClassifier(data);
        }
    }

    /**
     * @param kind a technique that selects
     * @return the model it wraps a naive Bayes in
     */
    private AttributeSelectedClassifier selecting(FeatureSelectionKind kind) {
        return (AttributeSelectedClassifier) selector.selecting(new NaiveBayes(), kind);
    }

}
