package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.BalancerFactory;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import org.junit.Before;
import org.junit.Test;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instance;
import weka.core.Instances;

import java.io.Serial;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the wrapper balances the rows a model is trained on and touches nothing else: not the rows
 * it was handed, not the rows it is asked about, and not whether Weka can copy it into every fold.
 */
public class BalancedClassifierTest {

    private static final int ROWS = 200;
    private static final int BUGGY_ROWS = 40;
    private static final int NOT_BUGGY_ROWS = ROWS - BUGGY_ROWS;

    private static final double TOLERANCE = 1e-9;

    private BalancerFactory balancers;
    private Instances data;

    @Before
    public void setUp() {
        balancers = new WekaBalancerFactory(SyntheticInstances.settings());
        data = SyntheticInstances.separable(ROWS, BUGGY_ROWS);
    }

    /**
     * What the wrapped model is trained on is the balanced rows, which a model recording what it was
     * shown is the one way to see from outside.
     */
    @Test
    public void testTheWrappedModelIsTrainedOnTheBalancedRows() throws MlException {
        assertArrayEquals(new int[] {NOT_BUGGY_ROWS, BUGGY_ROWS}, trainedOn(BalancingKind.NONE));
        assertArrayEquals(new int[] {BUGGY_ROWS, BUGGY_ROWS}, trainedOn(BalancingKind.UNDERSAMPLING));
        assertArrayEquals(new int[] {NOT_BUGGY_ROWS, NOT_BUGGY_ROWS}, trainedOn(BalancingKind.SMOTE));
    }

    /**
     * The caller trains every model on the same training half, so the one this wrapper was handed has to
     * come back as it went in — SMOTE adds rows, and adding them to the caller's set would hand the next
     * model a dataset nobody configured.
     */
    @Test
    public void testTheRowsItIsTrainedOnAreNotWrittenInto() throws MlException {
        Instances before = new Instances(data);
        for (BalancingKind balancing : BalancingKind.values()) {
            new BalancedClassifier(new NaiveBayes(), balancers.build(balancing)).buildClassifier(data);
            assertEquals(balancing.getLabel() + " changed the rows it was handed",
                    before.numInstances(), data.numInstances());
            for (int i = 0; i < before.numInstances(); i++) {
                assertEquals(before.instance(i).toString(), data.instance(i).toString());
            }
        }
    }

    /**
     * A row asked about is predicted as it is, by the model wrapped: left unbalanced, the wrapper has to
     * answer exactly what the model would have answered on its own.
     */
    @Test
    public void testAPredictionIsTheWrappedModelsOwn() throws Exception {
        BalancedClassifier wrapped =
                new BalancedClassifier(new NaiveBayes(), balancers.build(BalancingKind.NONE));
        wrapped.buildClassifier(data);
        NaiveBayes alone = new NaiveBayes();
        alone.buildClassifier(data);
        for (int i = 0; i < data.numInstances(); i++) {
            assertArrayEquals(alone.distributionForInstance(data.instance(i)),
                    wrapped.distributionForInstance(data.instance(i)), TOLERANCE);
        }
    }

    /**
     * Weka hands each fold of a cross-validation a copy of the model, made by serialising it, so a
     * wrapper whose balancer could not be serialised would fail the validation before its first fold.
     */
    @Test
    public void testEveryTechniqueSurvivesBeingCopiedIntoAFold() throws Exception {
        for (BalancingKind balancing : BalancingKind.values()) {
            BalancedClassifier original =
                    new BalancedClassifier(new NaiveBayes(), balancers.build(balancing));
            Classifier copy = AbstractClassifier.makeCopy(original);
            assertNotSame(original, copy);
            assertTrue(balancing.getLabel() + " came back as something else",
                    copy instanceof BalancedClassifier);
            copy.buildClassifier(data);
        }
    }

    /**
     * @param balancing how to balance the rows
     * @return how many rows of each class the wrapped model was trained on
     * @throws MlException if it could not be trained
     */
    private int[] trainedOn(BalancingKind balancing) throws MlException {
        RecordingClassifier recorder = new RecordingClassifier();
        new BalancedClassifier(recorder, balancers.build(balancing)).buildClassifier(data);
        return recorder.counts;
    }

    /**
     * A model that learns nothing and remembers how many rows of each class it was trained on.
     */
    private static final class RecordingClassifier extends AbstractClassifier {

        @Serial
        private static final long serialVersionUID = 1L;

        private int[] counts;

        @Override
        public void buildClassifier(Instances data) {
            counts = AbstractFilterBalancer.classCounts(data);
        }

        @Override
        public double[] distributionForInstance(Instance row) {
            return new double[counts.length];
        }

    }

}
