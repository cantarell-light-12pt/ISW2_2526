package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.BalancerFactory;
import it.uniroma2.dicii.isw2.ml.DatasetBalancer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import org.junit.Before;
import org.junit.Test;
import weka.core.Instances;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that each kind names the technique it is expected to, and that the rows a balancer refuses are
 * the same ones whichever technique is configured.
 */
public class WekaBalancerFactoryTest {

    private static final int ROWS = 200;
    private static final int BUGGY_ROWS = 40;

    private BalancerFactory factory;

    @Before
    public void setUp() {
        factory = new WekaBalancerFactory(SyntheticInstances.settings());
    }

    @Test
    public void testEachKindBuildsTheTechniqueItNames() {
        assertTrue(factory.build(BalancingKind.NONE) instanceof NoResampling);
        assertTrue(factory.build(BalancingKind.UNDERSAMPLING) instanceof MajorityUndersampler);
        assertTrue(factory.build(BalancingKind.SMOTE) instanceof SmoteOversampler);
    }

    /**
     * A filter holds the header it was last given, so one carried from one dataset to the next would
     * resample the second against the shape of the first.
     */
    @Test
    public void testEveryCallBuildsABalancerOfItsOwn() {
        for (BalancingKind kind : BalancingKind.values()) {
            DatasetBalancer first = factory.build(kind);
            DatasetBalancer second = factory.build(kind);
            assertNotSame(kind.getLabel() + " was handed out twice", first, second);
        }
    }

    /**
     * The technique that resamples nothing still has to hand back a set of the caller's own, or a caller
     * would be able to tell the configured-away case apart by whether the rows it got back were shared
     * with the ones it passed in.
     */
    @Test
    public void testLeavingTheRowsAloneStillHandsBackRowsOfTheirOwn() throws MlException {
        Instances data = SyntheticInstances.imbalanced(ROWS, BUGGY_ROWS);
        Instances balanced = factory.build(BalancingKind.NONE).balance(data);
        assertNotSame(data, balanced);
        assertTrue(data.equalHeaders(balanced));
        assertEquals(ROWS, balanced.numInstances());
    }

    /**
     * Whether a dataset can be balanced at all must not depend on which technique was configured: a run
     * that failed under one setting and succeeded under another would be saying something about the
     * settings and nothing about the data.
     */
    @Test
    public void testEveryTechniqueRefusesTheSameRows() {
        Instances unlabelled = SyntheticInstances.imbalanced(ROWS, BUGGY_ROWS);
        unlabelled.setClassIndex(-1);
        for (BalancingKind kind : BalancingKind.values()) {
            DatasetBalancer balancer = factory.build(kind);
            assertThrows(kind.getLabel() + " accepted rows naming no label", MlException.class,
                    () -> balancer.balance(unlabelled));
            assertThrows(kind.getLabel() + " accepted rows that are not there", MlException.class,
                    () -> balancer.balance(null));
        }
    }

    /**
     * A training half holding a single class is nothing to balance, which is not a failure: the rows
     * come back as they are, so that whatever reads them is left to decide what a model trained on one
     * class is worth.
     */
    @Test
    public void testRowsOfASingleClassComeBackAsTheyAre() throws MlException {
        Instances oneClass = SyntheticInstances.imbalanced(ROWS, 0);
        for (BalancingKind kind : BalancingKind.values()) {
            Instances balanced = factory.build(kind).balance(oneClass);
            assertEquals(kind.getLabel() + " resampled rows of a single class",
                    ROWS, balanced.numInstances());
            assertArrayEquals(kind.getLabel() + " changed the classes of rows holding only one",
                    new int[] {ROWS, 0}, AbstractFilterBalancer.classCounts(balanced));
        }
    }

}
