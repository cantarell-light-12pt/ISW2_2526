package it.uniroma2.dicii.isw2.ml.impl;

import it.uniroma2.dicii.isw2.ml.DatasetBalancer;
import it.uniroma2.dicii.isw2.ml.exception.MlException;
import weka.classifiers.Classifier;
import weka.classifiers.SingleClassifierEnhancer;
import weka.core.Instance;
import weka.core.Instances;

import java.io.Serial;

/**
 * A model that balances the rows it is trained on, and nothing else.
 * <p>
 * This is how balancing reaches both phases without either of them knowing about it. The validation
 * hands each fold a copy of this model and trains it on that fold's nine tenths, so it is those nine
 * tenths that are resampled, and the tenth the fold is measured on is predicted as it is. The inference
 * phase trains it on the whole training half and predicts the test releases, which are never resampled
 * either. Balancing the training half once, before the folds were cut, would have been simpler and
 * wrong: SMOTE would have synthesised rows out of buggy classes a fold later held out, and the fold would
 * have been measured on the neighbours of rows it had been trained on.
 * <p>
 * Predicting is left to the model it wraps, untouched. Resampling is something done to a training set,
 * and a row asked about is a single row with no class distribution to speak of.
 */
public class BalancedClassifier extends SingleClassifierEnhancer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DatasetBalancer balancer;

    /**
     * @param model    the model to train on the balanced rows, untrained
     * @param balancer how to balance them
     */
    public BalancedClassifier(Classifier model, DatasetBalancer balancer) {
        setClassifier(model);
        this.balancer = balancer;
    }

    /**
     * Trains the wrapped model on the given rows, balanced.
     *
     * @param data the rows to train on, which are not written into
     * @throws MlException if they cannot be balanced, or the model cannot be trained on what comes out
     */
    @Override
    public void buildClassifier(Instances data) throws MlException {
        Instances balanced = balancer.balance(data);
        try {
            getClassifier().buildClassifier(balanced);
        } catch (Exception e) {
            // Weka reports everything from an unbuildable classifier to an attribute it cannot handle as
            // a bare Exception, which leaves nothing more specific to catch
            throw new MlException("Unable to train " + getClassifier().getClass().getSimpleName()
                    + " on " + balanced.numInstances() + " balanced rows", e);
        }
    }

    /**
     * @param row the row to predict, as it is
     * @return what the wrapped model makes of it
     * @throws MlException if it cannot be asked
     */
    @Override
    public double[] distributionForInstance(Instance row) throws MlException {
        try {
            return getClassifier().distributionForInstance(row);
        } catch (Exception e) {
            // As above: the wrapped model declares nothing narrower than Exception
            throw new MlException("Unable to ask " + getClassifier().getClass().getSimpleName()
                    + " about a row", e);
        }
    }

}
