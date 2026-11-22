package it.uniroma2.dicii.isw2.ml.model;

import java.time.Duration;

/**
 * How one model scored, cross-validated over the trimmed dataset.
 * <p>
 * Precision, recall, F1 and the area under the ROC curve are read of the <b>buggy</b> class alone, not
 * averaged over the two. Some 23% of the trimmed dataset's rows are buggy, so a classifier answering
 * "not buggy" to everything is right 77% of the time and useless: accuracy and the weighted averages
 * both reward it, and only the figures of the minority class say that it never found anything. Accuracy
 * and kappa are of the whole confusion matrix, as they can only be — kappa being what says how much of
 * that accuracy is more than guessing at the observed rates would reach.
 *
 * @param classifier       which model was measured
 * @param balancing        how the rows it was trained on were balanced
 * @param featureSelection which of the attributes of those rows it was trained on
 * @param folds            how many folds it was cross-validated over
 * @param accuracy         the share of rows classified correctly
 * @param precision        of the buggy class
 * @param recall           of the buggy class
 * @param fMeasure         of the buggy class
 * @param areaUnderRoc     of the buggy class
 * @param kappa            agreement with the truth over and above what guessing at the observed rates
 *                         would reach
 * @param npofb20          the share of the buggy classes found by inspecting the classes in order of
 *                         bug density until a fifth of the size of them all has been read
 * @param elapsed          how long the validation took
 */
public record EvaluationOutcome(ClassifierKind classifier, BalancingKind balancing,
                                FeatureSelectionKind featureSelection, int folds,
                                double accuracy, double precision, double recall, double fMeasure,
                                double areaUnderRoc, double kappa, double npofb20, Duration elapsed) {
}
