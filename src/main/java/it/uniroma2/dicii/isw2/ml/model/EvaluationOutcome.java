package it.uniroma2.dicii.isw2.ml.model;

import java.time.Duration;

/**
 * How one model scored, on one of the two occasions it is measured.
 * <p>
 * Precision, recall, F1 and the area under the ROC curve are read of the <b>buggy</b> class alone, not
 * averaged over the two. Some 23% of the trimmed dataset's rows are buggy, so a classifier answering
 * "not buggy" to everything is right 77% of the time and useless: accuracy and the weighted averages
 * both reward it, and only the figures of the minority class say that it never found anything. Accuracy
 * and kappa are of the whole confusion matrix, as they can only be — kappa being what says how much of
 * that accuracy is more than guessing at the observed rates would reach.
 *
 * @param classifier   which model was measured
 * @param phase        on which occasion, i.e. against which rows
 * @param folds        how many folds it was cross-validated over, or zero for the inference phase,
 *                     which cuts none — it trains once and predicts the releases it was held back from
 * @param accuracy     the share of rows classified correctly
 * @param precision    of the buggy class
 * @param recall       of the buggy class
 * @param fMeasure     of the buggy class
 * @param areaUnderRoc of the buggy class
 * @param kappa        agreement with the truth over and above what guessing at the observed rates
 *                     would reach
 * @param elapsed      how long the phase took
 */
public record EvaluationOutcome(ClassifierKind classifier, EvaluationPhase phase, int folds,
                                double accuracy, double precision, double recall, double fMeasure,
                                double areaUnderRoc, double kappa, Duration elapsed) {
}
