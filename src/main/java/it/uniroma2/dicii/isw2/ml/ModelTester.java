package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.exception.MlException;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import it.uniroma2.dicii.isw2.ml.model.EvaluationOutcome;
import weka.core.Instances;

/**
 * Trains one model on the training set and asks it about the test set, which is the inference the whole
 * split exists to make possible.
 * <p>
 * Separate from {@link ModelEvaluator} because it takes two sets of rows rather than one, and because
 * the two say different things: an evaluator estimates how a model would do, by holding rows out of
 * data it is allowed to read, while this one reports how it did on releases that were never part of
 * that data at all.
 */
public interface ModelTester {

    /**
     * Trains a model and measures it on rows it has never seen.
     *
     * @param training   the rows to train on
     * @param test       the rows to predict, held back from everything until now
     * @param classifier which model to train
     * @return how it scored on the test rows
     * @throws MlException if the model cannot be trained, or the two sets do not describe the same
     *                     columns
     */
    EvaluationOutcome test(Instances training, Instances test, ClassifierKind classifier)
            throws MlException;

}
