package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.EvaluationPhase;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instances;

import java.time.Duration;

/**
 * Reads the six measures off a Weka {@code Evaluation}.
 * <p>
 * Shared by the two phases on purpose. They are measured over different rows and answer different
 * questions, but a figure only means the same thing in both if it was read the same way — a precision
 * of one phase taken over the buggy class and of the other averaged over both would put two numbers in
 * one column of the report that cannot be compared.
 */
final class EvaluationOutcomes {

    private EvaluationOutcomes() {
        // Reads outcomes, and holds none
    }

    /**
     * @param evaluation         the predictions to read
     * @param classifier         which model made them
     * @param phase              on which occasion
     * @param folds              how many folds it was cross-validated over, or zero if it was not
     * @param positiveClassIndex which value of the label the per-class measures are read of
     * @param elapsed            how long the phase took
     * @return what it scored
     */
    static EvaluationOutcome from(Evaluation evaluation, ClassifierKind classifier,
                                  EvaluationPhase phase, int folds, int positiveClassIndex,
                                  Duration elapsed) {
        return new EvaluationOutcome(classifier, phase, folds, evaluation.pctCorrect() / 100,
                evaluation.precision(positiveClassIndex), evaluation.recall(positiveClassIndex),
                evaluation.fMeasure(positiveClassIndex), evaluation.areaUnderROC(positiveClassIndex),
                evaluation.kappa(), elapsed);
    }

    /**
     * Resolves the class the per-class measures are read of, from the data rather than from anything
     * fixed when an evaluator was built: the label's values are numbered in the order the file happens
     * to introduce them, and each half of a split, or a dataset a feature selection has reduced, is a
     * header that has to be asked again.
     *
     * @param data          the rows being measured
     * @param positiveClass the label of the buggy class, as it reads in the dataset
     * @return the zero-based index {@code Evaluation} reads its per-class figures by
     * @throws MlException if no value of the label reads that way, which leaves precision, recall, F1
     *                     and AUC measuring nothing
     */
    static int positiveClassIndex(Instances data, String positiveClass) throws MlException {
        Attribute label = data.classAttribute();
        int index = label.indexOfValue(positiveClass);
        if (index < 0) {
            throw new MlException("The label '" + label.name() + "' takes no value '" + positiveClass
                    + "', so there is no buggy class to report the precision, the recall, the F1 and "
                    + "the AUC of");
        }
        return index;
    }

    /**
     * @param score a figure being logged
     * @return it, to the three decimals a log line is worth reading at
     */
    static double rounded(double score) {
        return Math.round(score * 1000) / 1000.0;
    }

}
