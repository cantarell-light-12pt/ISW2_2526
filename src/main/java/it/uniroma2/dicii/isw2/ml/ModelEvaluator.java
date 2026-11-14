package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import it.uniroma2.dicii.isw2.ml.model.ModelConfiguration;
import weka.core.Instances;

/**
 * Trains one model on a dataset and reports how well it predicts the label of a row it was not trained
 * on.
 * <p>
 * Neither of the two things that vary is fixed by this interface, which is the point of its shape: the
 * model is named by a {@link ModelConfiguration} rather than built here, and the dataset arrives as
 * {@code Instances} rather than as the path they were read from. How the rows a model is trained on are
 * balanced, and which of their attributes it is trained on, are named by the configuration as well, and
 * it is the implementation's to make sure that only those rows are balanced and selected over.
 */
public interface ModelEvaluator {

    /**
     * Measures one model over the given rows.
     *
     * @param data          the rows to train and test on, labelled, with their class attribute set
     * @param configuration which model to train, how to balance the rows it is trained on and which of
     *                      their attributes to keep, leaving the rows it is tested on alone
     * @return how it scored
     * @throws MlException if the model cannot be trained or measured
     */
    EvaluationOutcome evaluate(Instances data, ModelConfiguration configuration) throws MlException;

}
