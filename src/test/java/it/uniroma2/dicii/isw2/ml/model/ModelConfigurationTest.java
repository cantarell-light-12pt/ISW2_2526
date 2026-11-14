package it.uniroma2.dicii.isw2.ml.model;

import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks that a model is only ever configured with all three of its knobs named, and that it reads as
 * all three of them.
 */
public class ModelConfigurationTest {

    @Test
    public void testAKnobLeftUnnamedIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new ModelConfiguration(null, BalancingKind.NONE, FeatureSelectionKind.NONE));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelConfiguration(ClassifierKind.IBK, null, FeatureSelectionKind.NONE));
        assertThrows(IllegalArgumentException.class,
                () -> new ModelConfiguration(ClassifierKind.IBK, BalancingKind.NONE, null));
    }

    /**
     * A log line or a failure naming the model alone would not say which of the twelve runs of it is
     * meant.
     */
    @Test
    public void testTheDescriptionNamesEveryKnob() {
        String description = new ModelConfiguration(ClassifierKind.NAIVE_BAYES, BalancingKind.SMOTE,
                FeatureSelectionKind.BACKWARD_SEARCH).describe();
        assertTrue(description, description.contains(ClassifierKind.NAIVE_BAYES.getLabel()));
        assertTrue(description, description.contains(BalancingKind.SMOTE.getLabel()));
        assertTrue(description, description.contains(FeatureSelectionKind.BACKWARD_SEARCH.getLabel()));
    }

}
