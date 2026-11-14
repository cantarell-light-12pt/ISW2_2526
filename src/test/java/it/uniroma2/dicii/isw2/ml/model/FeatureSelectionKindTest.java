package it.uniroma2.dicii.isw2.ml.model;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that a selection is found by the name it is configured under, and that a name belonging to no
 * selection is refused rather than quietly taken for one.
 */
public class FeatureSelectionKindTest {

    @Test
    public void testEverySelectionIsFoundByTheLabelItReadsAs() throws MlException {
        for (FeatureSelectionKind kind : FeatureSelectionKind.values()) {
            assertEquals(kind, FeatureSelectionKind.from(kind.getLabel()));
        }
    }

    @Test
    public void testASelectionIsNamedWhateverItsCase() throws MlException {
        assertEquals(FeatureSelectionKind.INFO_GAIN, FeatureSelectionKind.from("infogain"));
        assertEquals(FeatureSelectionKind.FORWARD_SEARCH, FeatureSelectionKind.from("FORWARDSEARCH"));
        assertEquals(FeatureSelectionKind.BACKWARD_SEARCH, FeatureSelectionKind.from("backwardSearch"));
        assertEquals(FeatureSelectionKind.NONE, FeatureSelectionKind.from("nOnE"));
    }

    /**
     * The selection is read from the configuration, so a typo must fail loudly: a run that quietly fell
     * back on every attribute would report it under the heading of a selection.
     */
    @Test
    public void testAnUnknownSelectionNameIsRefused() {
        MlException thrown = assertThrows(MlException.class, () -> FeatureSelectionKind.from("InfoGian"));
        assertTrue("The message does not name what was asked for: " + thrown.getMessage(),
                thrown.getMessage().contains("InfoGian"));
        assertTrue("The message does not say what could have been meant: " + thrown.getMessage(),
                thrown.getMessage().contains(FeatureSelectionKind.INFO_GAIN.getLabel()));
    }

    @Test(expected = MlException.class)
    public void testAMissingSelectionNameIsRefused() throws MlException {
        FeatureSelectionKind.from(null);
    }

}
