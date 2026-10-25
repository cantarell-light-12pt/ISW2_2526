package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.MlSettings;
import it.uniroma2.dicii.isw2.ml.ModelFactory;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import org.junit.Before;
import org.junit.Test;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.neighboursearch.KDTree;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Checks that each kind names the classifier it is expected to, and that the configured sizes reach it.
 */
public class WekaClassifierFactoryTest {

    private static final int TREES = 7;
    private static final int NEIGHBOURS = 5;
    private static final int SEED = 3;
    private static final int THREADS = 2;

    private ModelFactory factory;

    @Before
    public void setUp() {
        factory = new WekaClassifierFactory(new MlSettings(List.of(ClassifierKind.values()),
                List.of(BalancingKind.NONE), 0.67, 2, SEED, SyntheticInstances.BUGGY, THREADS, TREES, NEIGHBOURS));
    }

    @Test
    public void testEachKindBuildsTheClassifierItNames() {
        assertTrue(model(ClassifierKind.RANDOM_FOREST) instanceof RandomForest);
        assertTrue(model(ClassifierKind.NAIVE_BAYES) instanceof NaiveBayes);
        assertTrue(model(ClassifierKind.IBK) instanceof IBk);
    }

    /**
     * Every model is trained through the balancing wrapper, the rows left as they are included, so that
     * two techniques differ by the technique and never by the path the model was trained through.
     */
    @Test
    public void testEveryModelComesWrappedInItsBalancingWhateverTheTechnique() {
        for (ClassifierKind kind : ClassifierKind.values()) {
            for (BalancingKind balancing : BalancingKind.values()) {
                assertTrue(kind.getLabel() + " balanced by " + balancing.getLabel() + " was not wrapped",
                        factory.build(kind, balancing) instanceof BalancedClassifier);
            }
        }
    }

    @Test
    public void testTheForestIsGrownAsConfigured() {
        RandomForest forest = (RandomForest) model(ClassifierKind.RANDOM_FOREST);
        assertEquals(TREES, forest.getNumIterations());
        assertEquals(SEED, forest.getSeed());
        assertEquals(THREADS, forest.getNumExecutionSlots());
    }

    /**
     * Every prediction a nearest-neighbour classifier makes is a search through the training set, so
     * the tree it searches through matters as much as the number of neighbours it votes among.
     */
    @Test
    public void testTheNeighboursAreCountedAsConfiguredAndSearchedThroughATree() {
        IBk classifier = (IBk) model(ClassifierKind.IBK);
        assertEquals(NEIGHBOURS, classifier.getKNN());
        assertTrue(classifier.getNearestNeighbourSearchAlgorithm() instanceof KDTree);
    }

    /**
     * A model carried from one measurement to the next would answer with what the previous one taught
     * it, test rows included.
     */
    @Test
    public void testEveryCallBuildsAModelOfItsOwn() {
        for (ClassifierKind kind : ClassifierKind.values()) {
            Classifier first = factory.build(kind, BalancingKind.NONE);
            Classifier second = factory.build(kind, BalancingKind.NONE);
            assertNotSame(kind.getLabel() + " was handed out twice", first, second);
        }
    }

    /**
     * @param kind which model to build
     * @return it, as the factory configured it, out of the balancing wrapper it comes in
     */
    private Classifier model(ClassifierKind kind) {
        return ((BalancedClassifier) factory.build(kind, BalancingKind.NONE)).getClassifier();
    }

}
