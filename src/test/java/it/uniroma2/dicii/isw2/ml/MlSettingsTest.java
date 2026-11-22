package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.FeatureSelectionKind;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
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
     * The attribute NPofB20 measures the effort of an inspection by, as the dataset names it.
     */
    private static final String SIZE = "LOC";

    /**
     * One model, since what is under test here is the settings and not the comparison.
     */
    private static final List<ClassifierKind> MODELS = List.of(ClassifierKind.IBK);

    /**
     * The rows as they are, which is what every case but the ones asserting on it wants.
     */
    private static final List<BalancingKind> BALANCING = List.of(BalancingKind.NONE);

    /**
     * Every attribute, which is what every case but the ones asserting on it wants.
     */
    private static final List<FeatureSelectionKind> SELECTION = List.of(FeatureSelectionKind.NONE);

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
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 1, 1, BUGGY, 1, 100, 3, 10, SIZE));
    }

    /**
     * "The rows were not balanced" is a technique like the other two, and has to be named as one: a
     * settings object that accepted nothing there would let a run leave the choice unmade and report the
     * unbalanced figures under whatever heading the reader assumed.
     */
    @Test
    public void testAnUnnamedBalancingTechniqueIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, null, SELECTION, 10, 1, BUGGY, 1, 100, 3, 10, SIZE));
    }

    @Test
    public void testAnEmptyListOfBalancingTechniquesIsRefused() {
        List<BalancingKind> none = List.of();
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, none, SELECTION, 10, 1, BUGGY, 1, 100, 3, 10, SIZE));
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
        MlSettings settings = new MlSettings(MODELS, techniques, SELECTION, 10, 1, BUGGY, 1, 100, 3, 10, SIZE);
        techniques.add(BalancingKind.SMOTE);
        assertEquals(List.of(BalancingKind.NONE), settings.balancingTechniques());
    }

    /**
     * "Every attribute" is a selection like the other three, and has to be named as one for the reason
     * leaving the rows unbalanced does.
     */
    @Test
    public void testAnUnnamedFeatureSelectionIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, null, 10, 1, BUGGY, 1, 100, 3, 10, SIZE));
        List<FeatureSelectionKind> none = List.of();
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, none, 10, 1, BUGGY, 1, 100, 3, 10, SIZE));
    }

    /**
     * The comparison the project reports is of every attribute against the three selections, in that
     * order, so the list is asserted rather than merely read.
     */
    @Test
    public void testTheConfiguredFeatureSelectionsAreReadInOrder() throws MlException {
        assertEquals(List.of(FeatureSelectionKind.NONE, FeatureSelectionKind.INFO_GAIN,
                        FeatureSelectionKind.FORWARD_SEARCH, FeatureSelectionKind.BACKWARD_SEARCH),
                MlSettings.load().featureSelectionTechniques());
    }

    @Test
    public void testTheFeatureSelectionsCannotBeWrittenIntoBehindTheSettings() {
        List<FeatureSelectionKind> techniques = new ArrayList<>(SELECTION);
        MlSettings settings = new MlSettings(MODELS, BALANCING, techniques, 10, 1, BUGGY, 1, 100, 3,
                10, SIZE);
        techniques.add(FeatureSelectionKind.INFO_GAIN);
        assertEquals(SELECTION, settings.featureSelectionTechniques());
    }

    @Test
    public void testASelectionByInformationGainKeepingNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, BUGGY, 1, 100, 3, 0, SIZE));
    }

    @Test
    public void testTheConfiguredSelectionByInformationGainKeepsSomething() throws MlException {
        assertTrue(MlSettings.load().infoGainAttributes() >= 1);
    }

    /**
     * Every model is measured under every pairing of the two kinds of technique, in the order the lists
     * are written, the selection varying fastest: that is the order the rows are reported in, and a tie
     * between two of them goes to the one listed first.
     */
    @Test
    public void testEveryModelIsConfiguredUnderEveryPairingOfTechniques() {
        MlSettings settings = new MlSettings(List.of(ClassifierKind.IBK, ClassifierKind.NAIVE_BAYES),
                List.of(BalancingKind.NONE, BalancingKind.SMOTE),
                List.of(FeatureSelectionKind.NONE, FeatureSelectionKind.INFO_GAIN), 10, 1, BUGGY, 1,
                100, 3, 10, SIZE);
        List<ModelConfiguration> configurations = settings.configurations();
        assertEquals(8, configurations.size());
        assertEquals(new ModelConfiguration(ClassifierKind.IBK, BalancingKind.NONE, FeatureSelectionKind.NONE),
                configurations.getFirst());
        assertEquals(new ModelConfiguration(ClassifierKind.IBK, BalancingKind.NONE,
                FeatureSelectionKind.INFO_GAIN), configurations.get(1));
        assertEquals(new ModelConfiguration(ClassifierKind.IBK, BalancingKind.SMOTE, FeatureSelectionKind.NONE),
                configurations.get(2));
        assertEquals(new ModelConfiguration(ClassifierKind.NAIVE_BAYES, BalancingKind.SMOTE,
                FeatureSelectionKind.INFO_GAIN), configurations.getLast());
    }

    /**
     * NPofB20 charges an inspection by the size of the class inspected, so which attribute that is has
     * to be named, and the dataset names it LOC.
     */
    @Test
    public void testAnUnnamedSizeAttributeIsRefused() {
        for (String size : new String[] {null, "", " "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, BUGGY, 1, 100, 3, 10, size));
        }
    }

    @Test
    public void testTheSizeOfAClassIsReadOffItsLinesOfCode() throws MlException {
        assertEquals(SIZE, MlSettings.load().sizeAttribute());
    }

    @Test
    public void testTheModelsAreStudiedOnTheEarliestThirdOfTheReleases() {
        assertEquals(0.33, MlSettings.trimFraction(), 0.0);
    }

    @Test
    public void testAnEvaluationWithoutAModelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> settings(List.of()));
    }

    @Test
    public void testAnUnnamedBuggyClassIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, " ", 1, 100, 3, 10, SIZE));
    }

    @Test
    public void testAModelBuiltOutOfNothingIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, BUGGY, 1, 0, 3, 10, SIZE));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, BUGGY, 1, 100, 0, 10, SIZE));
        assertThrows(IllegalArgumentException.class,
                () -> new MlSettings(MODELS, BALANCING, SELECTION, 10, 1, BUGGY, 0, 100, 3, 10, SIZE));
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
        return new MlSettings(classifiers, BALANCING, SELECTION, 10, 1, BUGGY, 1, 100, 3, 10, SIZE);
    }

}
