package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Checks what the properties file is read into, and that a setting no evaluation could run under is
 * refused rather than carried into one.
 */
public class MlSettingsTest {

    private static final String BUGGY = "1";

    /**
     * A share leaving releases on both sides of the cut, which is all these tests need of it.
     */
    private static final double FRACTION = 0.67;

    /**
     * The validation the project is required to report on is a ten-fold one, so the number is asserted
     * rather than merely read: a run configured with another one would report figures answering a
     * different question under the same heading.
     */
    @Test
    public void testTheProjectIsConfiguredForATenFoldValidation() throws MlException {
        assertEquals(10, MlSettings.load().folds());
    }

    @Test
    public void testTheConfiguredModelsAndKnobsAreRead() throws MlException {
        MlSettings settings = MlSettings.load();
        assertFalse("There is no model to measure", settings.classifiers().isEmpty());
        assertEquals(BUGGY, settings.positiveClass());
        assertTrue(settings.seed() >= 0);
        assertTrue(settings.randomForestIterations() >= 1);
        assertTrue(settings.ibkNeighbours() >= 1);
    }

    /**
     * Zero threads is the property's way of saying "the whole machine", which the models are measured
     * one after another precisely so that they can have.
     */
    @Test
    public void testTheThreadsLeftOpenComeToOnePerProcessor() throws MlException {
        assertEquals(Runtime.getRuntime().availableProcessors(), MlSettings.load().threads());
    }

    /**
     * The list is what the workflow loops over, so a caller holding the list it was built from must not
     * be able to add a model to a run that has already been described.
     */
    @Test
    public void testTheModelsAreCopiedOutOfTheListTheyCameIn() {
        List<ClassifierKind> configured = new ArrayList<>(List.of(ClassifierKind.RANDOM_FOREST));
        MlSettings settings = settings(configured);
        configured.add(ClassifierKind.IBK);
        assertEquals(List.of(ClassifierKind.RANDOM_FOREST), settings.classifiers());
    }

    @Test
    public void testAValidationOfFewerThanTwoFoldsIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(List.of(ClassifierKind.IBK), FRACTION, 1, 1, BUGGY, 1, 100, 3));
    }

    @Test
    public void testAnEvaluationWithoutAModelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> settings(List.of()));
    }

    /**
     * A model is trained on some of the releases and tested on the rest, so a fraction at either end
     * would leave one of the two sets empty and there would be nothing to train on, or nothing to
     * report.
     */
    @Test
    public void testATrainingShareLeavingNothingOnOneSideIsRefused() {
        for (double fraction : new double[] {0.0, 1.0, -0.1, 1.5, Double.NaN}) {
            assertThrows("A training fraction of " + fraction + " was accepted",
                    IllegalArgumentException.class,
                    () -> new MlSettings(List.of(ClassifierKind.IBK), fraction, 10, 1, BUGGY, 1, 100, 3));
        }
    }

    @Test
    public void testTheConfiguredTrainingShareLeavesReleasesOnBothSides() throws MlException {
        double fraction = MlSettings.load().trainingFraction();
        assertTrue("The whole history would be trained on", fraction < 1);
        assertTrue("There would be nothing to train on", fraction > 0);
    }

    @Test
    public void testAnUnnamedBuggyClassIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(List.of(ClassifierKind.IBK), FRACTION, 10, 1, " ", 1, 100, 3));
    }

    @Test
    public void testAModelBuiltOutOfNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(List.of(ClassifierKind.IBK), FRACTION, 10, 1, BUGGY, 1, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(List.of(ClassifierKind.IBK), FRACTION, 10, 1, BUGGY, 1, 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(List.of(ClassifierKind.IBK), FRACTION, 10, 1, BUGGY, 0, 100, 3));
    }

    /**
     * The models are read from the configuration, so a typo must fail loudly instead of quietly leaving
     * one out of a comparison its reader believes is complete.
     */
    @Test
    public void testAnUnknownModelNameIsRefused() {
        MlException thrown = assertThrows(MlException.class, () -> ClassifierKind.from("RandomForrest"));
        assertTrue(thrown.getMessage().contains("RandomForrest"));
        assertTrue("The message does not say what could have been meant",
                thrown.getMessage().contains(ClassifierKind.RANDOM_FOREST.getLabel()));
    }

    @Test
    public void testAModelIsNamedWhateverItsCase() throws MlException {
        assertEquals(ClassifierKind.NAIVE_BAYES, ClassifierKind.from("naivebayes"));
        assertEquals(ClassifierKind.IBK, ClassifierKind.from("IBk"));
    }

    /**
     * @param classifiers the models to measure
     * @return settings holding them, everything else left at something workable
     */
    private static MlSettings settings(List<ClassifierKind> classifiers) {
        return new MlSettings(classifiers, FRACTION, 10, 1, BUGGY, 1, 100, 3);
    }

}
