package it.uniroma2.dicii.isw2.ml;

import it.uniroma2.dicii.isw2.ml.model.BalancingKind;
import it.uniroma2.dicii.isw2.ml.model.ClassifierKind;
import weka.classifiers.Classifier;

/**
 * Builds the model one {@link ClassifierKind} names, configured as the settings ask, balancing the rows
 * it is trained on as one {@link BalancingKind} names.
 * <p>
 * It exists so that nothing above it has to name a Weka class: adding a fourth classifier to the
 * comparison is then a value of the enum and a branch here, and no change at all to the evaluator or
 * to the workflow. The balancing is part of the model rather than something done to the rows before
 * they reach it, so that whatever trains the model balances exactly what the model is trained on — the
 * training part of each fold, or the whole training half — and nothing it is measured on.
 */
public interface ModelFactory {

    /**
     * @param kind      which model to build
     * @param balancing how to balance the rows it is trained on
     * @return a fresh, configured and untrained model. Fresh every time, since a model carried from one
     * fold to the next would answer with what the previous fold taught it
     */
    Classifier build(ClassifierKind kind, BalancingKind balancing);

}
