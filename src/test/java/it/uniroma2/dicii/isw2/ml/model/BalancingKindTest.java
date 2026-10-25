package it.uniroma2.dicii.isw2.ml.model;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks that a technique is found by the name it is configured under, and that a name belonging to no
 * technique is refused rather than quietly taken for one.
 */
public class BalancingKindTest {

    @Test
    public void testEveryTechniqueIsFoundByTheLabelItReadsAs() throws MlException {
        for (BalancingKind kind : BalancingKind.values()) {
            assertEquals(kind, BalancingKind.from(kind.getLabel()));
        }
    }

    @Test
    public void testATechniqueIsNamedWhateverItsCase() throws MlException {
        assertEquals(BalancingKind.UNDERSAMPLING, BalancingKind.from("undersampling"));
        assertEquals(BalancingKind.SMOTE, BalancingKind.from("smote"));
        assertEquals(BalancingKind.NONE, BalancingKind.from("nOnE"));
    }

    /**
     * The technique is read from the configuration, so a typo must fail loudly: a run that quietly fell
     * back on leaving the rows alone would report the unbalanced figures under the heading of a balanced
     * run, which is the one mistake nothing downstream could catch.
     */
    @Test
    public void testAnUnknownTechniqueNameIsRefused() {
        try {
            BalancingKind.from("Undersample");
            fail("An unknown balancing technique must be rejected");
        } catch (MlException e) {
            assertTrue("The message does not name what was asked for: " + e.getMessage(),
                    e.getMessage().contains("Undersample"));
            assertTrue("The message does not say what could have been meant: " + e.getMessage(),
                    e.getMessage().contains(BalancingKind.UNDERSAMPLING.getLabel()));
        }
    }

    @Test(expected = MlException.class)
    public void testAMissingTechniqueNameIsRefused() throws MlException {
        BalancingKind.from(null);
    }

}
