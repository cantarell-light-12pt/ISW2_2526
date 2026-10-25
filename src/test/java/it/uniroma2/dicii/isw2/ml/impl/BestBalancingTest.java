package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import it.uniroma2.dicii.isw2.ml.model.Measure;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Checks that the technique a measure picks is the one the model scored highest under, and that the
 * cases where "highest" is unclear — a tie, a figure that is no number — are settled the documented way.
 */
public class BestBalancingTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    public void testTheTechniqueScoringHighestIsPicked() {
        List<EvaluationOutcome> outcomes = List.of(
                outcome(ClassifierKind.RANDOM_FOREST, BalancingKind.NONE, 0.70),
                outcome(ClassifierKind.RANDOM_FOREST, BalancingKind.UNDERSAMPLING, 0.80),
                outcome(ClassifierKind.RANDOM_FOREST, BalancingKind.SMOTE, 0.75));
        assertEquals(BalancingKind.UNDERSAMPLING, picked(outcomes, ClassifierKind.RANDOM_FOREST));
    }

    /**
     * Every model is measured in one list, so a model's verdict must be read off its own rows: another
     * model scoring higher under some technique says nothing about this one.
     */
    @Test
    public void testOnlyTheRowsOfTheModelAskedAboutAreCompared() {
        List<EvaluationOutcome> outcomes = List.of(
                outcome(ClassifierKind.IBK, BalancingKind.NONE, 0.70),
                outcome(ClassifierKind.IBK, BalancingKind.SMOTE, 0.60),
                outcome(ClassifierKind.RANDOM_FOREST, BalancingKind.SMOTE, 0.99));
        assertEquals(BalancingKind.NONE, picked(outcomes, ClassifierKind.IBK));
    }

    /**
     * A technique that does no better than the rows as they are has bought nothing, so a tie goes to the
     * one measured first, which is None as the project lists them.
     */
    @Test
    public void testATieGoesToTheTechniqueMeasuredFirst() {
        List<EvaluationOutcome> outcomes = List.of(
                outcome(ClassifierKind.NAIVE_BAYES, BalancingKind.NONE, 0.70),
                outcome(ClassifierKind.NAIVE_BAYES, BalancingKind.SMOTE, 0.70));
        assertEquals(BalancingKind.NONE, picked(outcomes, ClassifierKind.NAIVE_BAYES));
    }

    /**
     * The F1 of a model that never predicted the buggy class is no number, and a model that found nothing
     * is not the one to choose, however NaN happens to compare.
     */
    @Test
    public void testAFigureThatIsNoNumberNeverWins() {
        List<EvaluationOutcome> outcomes = List.of(
                outcome(ClassifierKind.NAIVE_BAYES, BalancingKind.NONE, Double.NaN),
                outcome(ClassifierKind.NAIVE_BAYES, BalancingKind.SMOTE, 0.10));
        assertEquals(BalancingKind.SMOTE, picked(outcomes, ClassifierKind.NAIVE_BAYES));
    }

    @Test
    public void testAModelThatScoredNoNumberAnywhereHasNoVerdict() {
        List<EvaluationOutcome> outcomes = List.of(
                outcome(ClassifierKind.NAIVE_BAYES, BalancingKind.NONE, Double.NaN));
        assertFalse(BestBalancing.of(outcomes, ClassifierKind.NAIVE_BAYES, Measure.F1).isPresent());
        assertFalse(BestBalancing.of(outcomes, ClassifierKind.IBK, Measure.F1).isPresent());
    }

    /**
     * Each measure has to read the figure it is named after, or the verdict would name the winner of a
     * different contest.
     */
    @Test
    public void testEveryMeasureReadsTheFigureItIsNamedAfter() {
        EvaluationOutcome outcome = new EvaluationOutcome(ClassifierKind.IBK, BalancingKind.NONE,
                EvaluationPhase.SELECTION, 0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, Duration.ZERO);
        assertEquals(0.1, Measure.ACCURACY.of(outcome), TOLERANCE);
        assertEquals(0.4, Measure.F1.of(outcome), TOLERANCE);
        assertEquals(0.5, Measure.AUC.of(outcome), TOLERANCE);
        assertEquals(0.6, Measure.KAPPA.of(outcome), TOLERANCE);
    }

    /**
     * @param outcomes   how every model scored
     * @param classifier the model to choose for
     * @return the technique F1 picks for it
     */
    private static BalancingKind picked(List<EvaluationOutcome> outcomes, ClassifierKind classifier) {
        Optional<EvaluationOutcome> best = BestBalancing.of(outcomes, classifier, Measure.F1);
        return best.orElseThrow().balancing();
    }

    /**
     * @param classifier which model scored
     * @param balancing  under which technique
     * @param f1         the F1 it scored, the figure these tests choose by
     * @return the outcome, every other figure at a value that would make a different winner of it
     */
    private static EvaluationOutcome outcome(ClassifierKind classifier, BalancingKind balancing, double f1) {
        return new EvaluationOutcome(classifier, balancing, EvaluationPhase.SELECTION, 0, 1 - f1, 1 - f1,
                1 - f1, f1, 1 - f1, 1 - f1, Duration.ZERO);
    }

}
