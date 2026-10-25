package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
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
     * One model, since what is under test here is the settings and not the comparison.
     */
    private static final List<ClassifierKind> MODELS = List.of(ClassifierKind.IBK);

    /**
     * The rows as they are, which is what every case but the ones asserting on it wants.
     */
    private static final List<BalancingKind> BALANCING = List.of(BalancingKind.NONE);

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
                () -> new MlSettings(MODELS, BALANCING, FRACTION, 1, 1, BUGGY, 1, 100, 3));
    }

    /**
     * "The rows were not balanced" is a technique like the other two, and has to be named as one: a
     * settings object that accepted nothing there would let a run leave the choice unmade and report the
     * unbalanced figures under whatever heading the reader assumed.
     */
    @Test
    public void testAnUnnamedBalancingTechniqueIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, null, FRACTION, 10, 1, BUGGY, 1, 100, 3));
    }

    @Test
    public void testAnEmptyListOfBalancingTechniquesIsRefused() {
        List<BalancingKind> none = List.of();
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, none, FRACTION, 10, 1, BUGGY, 1, 100, 3));
    }

    /**
     * The comparison the project reports is of the rows as they are against the two techniques, in
     * that order, so the list is asserted rather than merely read.
     */
    @Test
    public void testTheConfiguredBalancingTechniquesAreReadInOrder() throws MlException {
        assertEquals(List.of(BalancingKind.NONE, BalancingKind.UNDERSAMPLING, BalancingKind.SMOTE),
                MlSettings.load().balancingTechniques());
    }

    /**
     * The settings hold a copy, so that the list they were built from cannot be written into behind
     * them and change which techniques a run is measuring halfway through it.
     */
    @Test
    public void testTheBalancingTechniquesCannotBeWrittenIntoBehindTheSettings() {
        List<BalancingKind> techniques = new ArrayList<>(List.of(BalancingKind.NONE));
        MlSettings settings = new MlSettings(MODELS, techniques, FRACTION, 10, 1, BUGGY, 1, 100, 3);
        techniques.add(BalancingKind.SMOTE);
        assertEquals(List.of(BalancingKind.NONE), settings.balancingTechniques());
    }

    /**
     * The balancing study cuts the training releases again, at a share of its own, and nothing else
     * about the run may change with it: the techniques compared have to be the ones the run measures.
     */
    @Test
    public void testOnlyTheTrainingFractionChangesWhenTheCutIsMoved() {
        MlSettings settings = settings(List.of(ClassifierKind.IBK, ClassifierKind.NAIVE_BAYES));
        MlSettings moved = settings.withTrainingFraction(0.5);
        assertEquals(0.5, moved.trainingFraction(), 0.0);
        assertEquals(new MlSettings(settings.classifiers(), settings.balancingTechniques(), 0.5,
                settings.folds(), settings.seed(), settings.positiveClass(), settings.threads(),
                settings.randomForestIterations(), settings.ibkNeighbours()), moved);
    }

    @Test
    public void testAMovedCutStillLeavesReleasesOnBothSides() {
        MlSettings settings = settings(MODELS);
        assertThrows(IllegalArgumentException.class, () -> settings.withTrainingFraction(1.0));
    }

    @Test
    public void testTheBalancingStudyCutsTheTrainingReleasesAtTwoThirds() {
        assertEquals(0.66, MlSettings.balancingStudyTrainingFraction(), 0.0);
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
                    () -> new MlSettings(MODELS, BALANCING, fraction, 10, 1, BUGGY, 1, 100, 3));
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
                () -> new MlSettings(MODELS, BALANCING, FRACTION, 10, 1, " ", 1, 100, 3));
    }

    @Test
    public void testAModelBuiltOutOfNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, FRACTION, 10, 1, BUGGY, 1, 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, FRACTION, 10, 1, BUGGY, 1, 100, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, FRACTION, 10, 1, BUGGY, 0, 100, 3));
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
        return new MlSettings(classifiers, BALANCING, FRACTION, 10, 1, BUGGY, 1, 100, 3);
    }

}
