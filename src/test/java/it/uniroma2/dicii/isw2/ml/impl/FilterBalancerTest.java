package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.BalancerFactory;
import it.uniroma2.dicii.isw2.ml.DatasetBalancer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import weka.core.Instances;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks the two sampling techniques against the invariants they share, since what a caller may rely on
 * has to hold whichever of them was configured: the classes come out the same size, the header does not
 * move, the rows handed in are not written into, and two runs under one seed resample the same rows.
 * <p>
 * What the two do <i>not</i> share is how large the result is, and that is the whole difference between
 * them — undersampling lands on twice the minority, having thrown the rest of the majority away, and
 * oversampling on twice the majority, having drawn the minority up to meet it. So the expected size is
 * a parameter beside the technique rather than a case of its own.
 */
@RunWith(Parameterized.class)
public class FilterBalancerTest {

    private static final int ROWS = 200;
    private static final int BUGGY_ROWS = 40;
    private static final int NOT_BUGGY_ROWS = ROWS - BUGGY_ROWS;

    private final BalancingKind kind;
    private final int expectedRows;

    private BalancerFactory factory;

    public FilterBalancerTest(BalancingKind kind, int expectedRows) {
        this.kind = kind;
        this.expectedRows = expectedRows;
    }

    @Parameterized.Parameters(name = "{index}: {0} leaves {1} rows")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[] {BalancingKind.UNDERSAMPLING, 2 * BUGGY_ROWS},
                new Object[] {BalancingKind.SMOTE, 2 * NOT_BUGGY_ROWS});
    }

    @Before
    public void setUp() {
        factory = new WekaBalancerFactory(SyntheticInstances.settings());
    }

    @Test
    public void testTheTwoClassesComeOutTheSameSize() throws MlException {
        int[] counts = AbstractFilterBalancer.classCounts(balance(rows()));
        assertArrayEquals("The classes were not brought to the same size: " + Arrays.toString(counts),
                new int[] {expectedRows / 2, expectedRows / 2}, counts);
    }

    /**
     * Undersampling throws majority rows away and oversampling draws minority rows again, so the size
     * each one lands on is the only thing that says which of the two actually ran.
     */
    @Test
    public void testTheResultIsAsLargeAsTheTechniqueLeavesIt() throws MlException {
        assertEquals(expectedRows, balance(rows()).numInstances());
    }

    /**
     * A model trained on balanced rows is tested on rows nothing has touched, and Weka will only put the
     * two together if they agree on their attributes.
     */
    @Test
    public void testTheHeaderDoesNotMove() throws MlException {
        Instances data = rows();
        assertTrue(data.equalHeaders(balance(data)));
    }

    /**
     * The rows belong to the caller, who balances one half of a split and expects the other half to be
     * the one it was.
     */
    @Test
    public void testTheRowsHandedInAreNotWrittenInto() throws MlException {
        Instances data = rows();
        balance(data);
        assertEquals(ROWS, data.numInstances());
        assertArrayEquals(new int[] {NOT_BUGGY_ROWS, BUGGY_ROWS},
                AbstractFilterBalancer.classCounts(data));
    }

    /**
     * Both techniques draw a sample, and a figure measured on a sample nobody can draw again is a figure
     * nobody can check.
     */
    @Test
    public void testTwoRunsUnderTheSameSeedResampleTheSameRows() throws MlException {
        assertEquals(balance(rows()).toString(), balance(rows()).toString());
    }

    /**
     * @return the shape the real dataset has, far more not-buggy rows than buggy ones
     */
    private static Instances rows() {
        return SyntheticInstances.imbalanced(ROWS, BUGGY_ROWS);
    }

    /**
     * @param data the rows to balance
     * @return them balanced through the technique under test, by a balancer of its own — the point of
     * asking the factory again is that a balancer is never reused across two datasets
     * @throws MlException if they cannot be balanced
     */
    private Instances balance(Instances data) throws MlException {
        DatasetBalancer balancer = factory.build(kind);
        return balancer.balance(data);
    }

}
