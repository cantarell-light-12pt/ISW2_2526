package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import weka.core.Instances;

/**
 * Trains one model on a dataset and reports how well it predicts the label of a row it was not trained
 * on.
 * <p>
 * Neither of the two things that vary is fixed by this interface, which is the point of its shape: the
 * model is named by a {@link ClassifierKind} rather than built here, and the dataset arrives as
 * {@code Instances} rather than as the path they were read from — so a dataset a feature selection has
 * reduced is measured through the very same call as the one the extraction workflow wrote.
 */
public interface ModelEvaluator {

    /**
     * Measures one model over the given rows.
     *
     * @param data       the rows to train and test on, labelled, with their class attribute set
     * @param classifier which model to train
     * @return how it scored
     * @throws MlException if the model cannot be trained or measured
     */
    EvaluationOutcome evaluate(Instances data, ClassifierKind classifier) throws MlException;

}
