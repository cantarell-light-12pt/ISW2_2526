package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.Instances;

import java.time.Duration;

/**
 * Reads the measures off a Weka {@code Evaluation}, in one place, so that every configuration is
 * read the same way — a precision taken over the buggy class for one model and averaged over both
 * classes for another would put two numbers in one column of the report that cannot be compared.
 */
final class EvaluationOutcomes {

    private EvaluationOutcomes() {
        // Reads outcomes, and holds none
    }

    /**
     * @param evaluation         the predictions to read
     * @param configuration      which model made them, how the rows it was trained on were balanced
     *                           and which of their attributes it was trained on
     * @param folds              how many folds it was cross-validated over
     * @param npofb20            the share of the buggy classes found within a fifth of the size, which
     *                           Weka does not compute and is read off the predictions by the caller
     * @param positiveClassIndex which value of the label the per-class measures are read of
     * @param elapsed            how long the validation took
     * @return what it scored
     */
    static EvaluationOutcome from(Evaluation evaluation, ModelConfiguration configuration,
                                  int folds, int positiveClassIndex, double npofb20,
                                  Duration elapsed) {
        return new EvaluationOutcome(configuration.classifier(), configuration.balancing(),
                configuration.featureSelection(), folds, evaluation.pctCorrect() / 100,
                evaluation.precision(positiveClassIndex), evaluation.recall(positiveClassIndex),
                evaluation.fMeasure(positiveClassIndex), evaluation.areaUnderROC(positiveClassIndex),
                evaluation.kappa(), npofb20, elapsed);
    }

    /**
     * Resolves the class the per-class measures are read of, from the data rather than from anything
     * fixed when an evaluator was built: the label's values are numbered in the order the file happens
     * to introduce them, and a dataset a feature selection has reduced is a header that has to be asked
     * again.
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
     * Resolves the attribute the size of a class is read off, by its name rather than by its position,
     * for the reason the buggy class is: a dataset a feature selection has reduced is a header that has
     * to be asked again.
     *
     * @param data          the rows being measured
     * @param sizeAttribute the name of the attribute holding the size of each class
     * @return its index
     * @throws MlException if no numeric attribute is named that way, which leaves NPofB20 nothing to
     *                     measure the effort of an inspection by
     */
    static int sizeIndex(Instances data, String sizeAttribute) throws MlException {
        Attribute size = data.attribute(sizeAttribute);
        if (size == null || !size.isNumeric()) {
            throw new MlException("The dataset holds no numeric attribute '" + sizeAttribute
                    + "' to read the size of a class off, so there is no effort to report the NPofB20 of");
        }
        return size.index();
    }

    /**
     * @param score a figure being logged
     * @return it, to the three decimals a log line is worth reading at
     */
    static double rounded(double score) {
        return Math.round(score * 1000) / 1000.0;
    }

}
